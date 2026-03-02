/*
 * Standard Command Parser Utility for Logisim Evolution
 * Reads commands from stdin and provides parsing utilities
 */

package com.testutils;

import java.io.InputStreamReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for reading and parsing commands from stdin.
 * This is similar to TtyInterface.StdinThread but provides a more
 * flexible command parsing interface.
 */
public class StdCommandParser {
    
    /** Singleton instance */
    private static StdCommandParser instance;
    
    /** Command queue */
    private final Queue<String> commandQueue;
    
    /** Stdin reader thread */
    private StdinReaderThread readerThread;
    
    /** Flag to track if parser is started */
    private volatile boolean started = false;
    
    // Regex patterns for command parsing
    private static final Pattern STORE_PATTERN = 
        Pattern.compile("^store\\s+(\\w+)\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORE_RAM_PATTERN = 
        Pattern.compile("^store\\s+ram\\s+(\\w+)\\s+(\\d+|0x[0-9a-fA-F]+)\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORE_ROM_PATTERN = 
        Pattern.compile("^store\\s+rom\\s+(\\w+)\\s+(\\d+|0x[0-9a-fA-F]+)\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORE_FILE_PATTERN = 
        Pattern.compile("^storefile\\s+(ram|rom)\\s+(\\w+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TICK_PATTERN = 
        Pattern.compile("^tick\\s+(\\d+\\.?\\d*)$", Pattern.CASE_INSENSITIVE);
    
    /**
     * Private constructor - use getInstance()
     */
    private StdCommandParser() {
        commandQueue = new LinkedList<>();
    }
    
    /**
     * Get the singleton instance
     */
    public static synchronized StdCommandParser getInstance() {
        if (instance == null) {
            instance = new StdCommandParser();
        }
        return instance;
    }
    
    /**
     * Start the stdin reader thread if not already started
     */
    public synchronized void start() {
        if (!started) {
            readerThread = new StdinReaderThread();
            readerThread.setDaemon(true);
            readerThread.start();
            started = true;
        }
    }
    
    /**
     * Check if there are any commands in the queue
     */
    public boolean hasCommand() {
        synchronized (commandQueue) {
            return !commandQueue.isEmpty();
        }
    }
    
    /**
     * Get the next command from the queue
     * @return next command, or null if queue is empty
     */
    public String getCommand() {
        synchronized (commandQueue) {
            return commandQueue.poll();
        }
    }
    
    /**
     * Get all pending commands without clearing the queue
     * @return array of commands
     */
    public String[] peekAllCommands() {
        synchronized (commandQueue) {
            return commandQueue.toArray(new String[0]);
        }
    }
    
    /**
     * Clear all pending commands
     */
    public void clearCommands() {
        synchronized (commandQueue) {
            commandQueue.clear();
        }
    }
    
    /**
     * Get all available commands and clear the queue
     * @return array of commands (may be empty)
     */
    public String[] getAllCommands() {
        synchronized (commandQueue) {
            if (commandQueue.isEmpty()) {
                return new String[0];
            }
            String[] result = commandQueue.toArray(new String[0]);
            commandQueue.clear();
            return result;
        }
    }
    
    /**
     * Parse a store command: store register_name=0xvalue
     * @param command the command string
     * @return StoreCommand object, or null if not a valid store command
     */
    public StoreCommand parseStoreCommand(String command) {
        Matcher matcher = STORE_PATTERN.matcher(command.trim());
        if (matcher.matches()) {
            String registerName = matcher.group(1);
            String valueStr = matcher.group(2);
            return new StoreCommand(registerName, valueStr);
        }
        return null;
    }
    
    /**
     * Parse a RAM store command: store ram ram_name address=0xvalue
     * @param command the command string
     * @return MemStoreCommand object, or null if not a valid RAM store command
     */
    public MemStoreCommand parseRamStoreCommand(String command) {
        Matcher matcher = STORE_RAM_PATTERN.matcher(command.trim());
        if (matcher.matches()) {
            String memName = matcher.group(1);
            String addrStr = matcher.group(2);
            String valueStr = matcher.group(3);
            return new MemStoreCommand(memName, addrStr, valueStr);
        }
        return null;
    }
    
    /**
     * Parse a ROM store command: store rom rom_name address=0xvalue
     * @param command the command string
     * @return MemStoreCommand object, or null if not a valid ROM store command
     */
    public MemStoreCommand parseRomStoreCommand(String command) {
        Matcher matcher = STORE_ROM_PATTERN.matcher(command.trim());
        if (matcher.matches()) {
            String memName = matcher.group(1);
            String addrStr = matcher.group(2);
            String valueStr = matcher.group(3);
            return new MemStoreCommand(memName, addrStr, valueStr);
        }
        return null;
    }
    
    /**
     * Parse a storefile command: storefile ram/rom component_name filename
     * @param command the command string
     * @return MemStoreFileCommand object, or null if not a valid storefile command
     */
    public MemStoreFileCommand parseStoreFileCommand(String command) {
        Matcher matcher = STORE_FILE_PATTERN.matcher(command.trim());
        if (matcher.matches()) {
            String type = matcher.group(1);
            String memName = matcher.group(2);
            String fileName = matcher.group(3);
            return new MemStoreFileCommand(type, memName, fileName);
        }
        return null;
    }
    
    /**
     * Parse a tick command: tick n
     * @param command the command string
     * @return TickCommand object, or null if not a valid tick command
     */
    public TickCommand parseTickCommand(String command) {
        Matcher matcher = TICK_PATTERN.matcher(command.trim());
        if (matcher.matches()) {
            double count = Double.parseDouble(matcher.group(1));
            return new TickCommand(count);
        }
        return null;
    }
    
    /**
     * Process all pending commands and return store commands
     * @return array of store commands
     */
    public StoreCommand[] getStoreCommands() {
        String[] cmds = getAllCommands();
        java.util.List<StoreCommand> stores = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            // Check for RAM/ROM store commands first
            MemStoreCommand memStore = parseRamStoreCommand(cmd);
            if (memStore == null) {
                memStore = parseRomStoreCommand(cmd);
            }
            if (memStore != null) {
                // Convert to regular store command format for compatibility
                stores.add(new StoreCommand(memStore.getMemName() + "[" + memStore.getAddress() + "]", memStore.getValueStr()));
                continue;
            }
            
            StoreCommand store = parseStoreCommand(cmd);
            if (store != null) {
                stores.add(store);
            }
        }
        
        return stores.toArray(new StoreCommand[0]);
    }
    
    /**
     * Process all pending commands and return RAM store commands
     * @return array of RAM store commands
     */
    public MemStoreCommand[] getRamStoreCommands() {
        // Peek at commands without clearing - let RomStore also process
        String[] cmds = peekAllCommands();
        java.util.List<MemStoreCommand> stores = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            MemStoreCommand store = parseRamStoreCommand(cmd);
            if (store != null) {
                stores.add(store);
            }
        }
        
        return stores.toArray(new MemStoreCommand[0]);
    }
    
    /**
     * Process all pending commands and return ROM store commands
     * @return array of ROM store commands
     */
    public MemStoreCommand[] getRomStoreCommands() {
        // Peek at commands without clearing - let RamStore also process
        String[] cmds = peekAllCommands();
        java.util.List<MemStoreCommand> stores = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            MemStoreCommand store = parseRomStoreCommand(cmd);
            if (store != null) {
                stores.add(store);
            }
        }
        
        return stores.toArray(new MemStoreCommand[0]);
    }
    
    /**
     * Process all pending commands and return RAM storefile commands
     * @return array of RAM storefile commands
     */
    public MemStoreFileCommand[] getRamStoreFileCommands() {
        // Peek at commands without clearing - let RomStoreFile also process
        String[] cmds = peekAllCommands();
        java.util.List<MemStoreFileCommand> stores = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            MemStoreFileCommand store = parseStoreFileCommand(cmd);
            if (store != null && "ram".equalsIgnoreCase(store.getType())) {
                stores.add(store);
            }
        }
        
        return stores.toArray(new MemStoreFileCommand[0]);
    }
    
    /**
     * Process all pending commands and return ROM storefile commands
     * @return array of ROM storefile commands
     */
    public MemStoreFileCommand[] getRomStoreFileCommands() {
        // Peek at commands without clearing - let RamStoreFile also process
        String[] cmds = peekAllCommands();
        java.util.List<MemStoreFileCommand> stores = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            MemStoreFileCommand store = parseStoreFileCommand(cmd);
            if (store != null && "rom".equalsIgnoreCase(store.getType())) {
                stores.add(store);
            }
        }
        
        return stores.toArray(new MemStoreFileCommand[0]);
    }
    
    /**
     * Process all pending commands and return tick commands
     * @return array of tick commands
     */
    public TickCommand[] getTickCommands() {
        String[] cmds = getAllCommands();
        java.util.List<TickCommand> ticks = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            TickCommand tick = parseTickCommand(cmd);
            if (tick != null) {
                ticks.add(tick);
            }
        }
        
        return ticks.toArray(new TickCommand[0]);
    }
    
    /**
     * Daemon thread that reads from stdin
     */
    private class StdinReaderThread extends Thread {
        public StdinReaderThread() {
            super("StdCommandParser-StdinThread");
        }
        
        @Override
        public void run() {
            InputStreamReader stdin = new InputStreamReader(System.in);
            StringBuilder buffer = new StringBuilder();
            
            while (true) {
                try {
                    int ch = stdin.read();
                    if (ch == -1) {
                        // EOF - break or wait
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            break;
                        }
                        continue;
                    }
                    
                    if (ch == '\n' || ch == '\r') {
                        // End of command
                        String cmd = buffer.toString().trim();
                        if (!cmd.isEmpty()) {
                            synchronized (commandQueue) {
                                commandQueue.add(cmd);
                            }
                        }
                        buffer.setLength(0);
                    } else if (ch == '\t') {
                        // Ignore tabs
                    } else {
                        buffer.append((char) ch);
                    }
                } catch (IOException e) {
                    // On error, wait and retry
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Represents a store command: store register_name=value
     */
    public static class StoreCommand {
        private final String registerName;
        private final String valueStr;
        
        public StoreCommand(String registerName, String valueStr) {
            this.registerName = registerName;
            this.valueStr = valueStr;
        }
        
        public String getRegisterName() {
            return registerName;
        }
        
        public String getValueStr() {
            return valueStr;
        }
        
        /**
         * Parse the value string to a long
         * Supports: decimal (123), hex (0x7B), binary (0b1111011)
         */
        public long parseValue() {
            String v = valueStr.trim();
            if (v.startsWith("0x") || v.startsWith("0X")) {
                return Long.parseLong(v.substring(2), 16);
            } else if (v.startsWith("0b") || v.startsWith("0B")) {
                return Long.parseLong(v.substring(2), 2);
            } else {
                return Long.parseLong(v);
            }
        }
        
        @Override
        public String toString() {
            return "store " + registerName + "=" + valueStr;
        }
    }
    
    /**
     * Represents a memory store command: store ram/rom name address=value
     */
    public static class MemStoreCommand {
        private final String memName;
        private final String addrStr;
        private final String valueStr;
        
        public MemStoreCommand(String memName, String addrStr, String valueStr) {
            this.memName = memName;
            this.addrStr = addrStr;
            this.valueStr = valueStr;
        }
        
        public String getMemName() {
            return memName;
        }
        
        public String getAddress() {
            return addrStr;
        }
        
        public String getValueStr() {
            return valueStr;
        }
        
        /**
         * Parse the address string to a long
         * Supports: decimal (123), hex (0x7B)
         */
        public long parseAddress() {
            String v = addrStr.trim();
            if (v.startsWith("0x") || v.startsWith("0X")) {
                return Long.parseLong(v.substring(2), 16);
            } else {
                return Long.parseLong(v);
            }
        }
        
        /**
         * Parse the value string to a long
         * Supports: decimal (123), hex (0x7B), binary (0b1111011)
         */
        public long parseValue() {
            String v = valueStr.trim();
            if (v.startsWith("0x") || v.startsWith("0X")) {
                return Long.parseLong(v.substring(2), 16);
            } else if (v.startsWith("0b") || v.startsWith("0B")) {
                return Long.parseLong(v.substring(2), 2);
            } else {
                return Long.parseLong(v);
            }
        }
        
        @Override
        public String toString() {
            return "store " + memName + " " + addrStr + "=" + valueStr;
        }
    }
    
    /**
     * Represents a memory storefile command: storefile ram/rom name filename
     */
    public static class MemStoreFileCommand {
        private final String type;
        private final String memName;
        private final String fileName;
        
        public MemStoreFileCommand(String type, String memName, String fileName) {
            this.type = type;
            this.memName = memName;
            this.fileName = fileName;
        }
        
        public String getType() {
            return type;
        }
        
        public String getMemName() {
            return memName;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        @Override
        public String toString() {
            return "storefile " + type + " " + memName + " " + fileName;
        }
    }
    
    /**
     * Represents a tick command: tick n
     */
    public static class TickCommand {
        private final double count;
        
        public TickCommand(double count) {
            this.count = count;
        }
        
        public double getCount() {
            return count;
        }
        
        @Override
        public String toString() {
            return "tick " + count;
        }
    }
}
