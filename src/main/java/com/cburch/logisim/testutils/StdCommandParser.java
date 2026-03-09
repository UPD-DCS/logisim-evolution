/*
 * Standard Command Parser Utility for Logisim Evolution
 * Reads commands from stdin and provides parsing utilities
 */

package com.cburch.logisim.testutils;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Console;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;
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
    
    /** Timer for periodic command checking */
    private java.util.Timer commandTimer;
    
    /** Flag to track if parser is started */
    private volatile boolean started = false;
    
    /** Listeners for immediate command processing */
    private final List<CommandListener> listeners = new ArrayList<>();
    
    /**
     * Interface for command listeners - components implement this
     * to receive commands immediately when they're received
     */
    public interface CommandListener {
        void onTickCommand(String command, TickCommand tickCmd);
        void onStoreCommand(String command, StoreCommand storeCmd);
        void onPrintCommand(String command, PrintCommand printCmd);
    }
    
    /**
     * Add a command listener to receive commands immediately
     * Only adds if listener is not already in the list
     */
    public synchronized void addListener(CommandListener listener) {
        for (CommandListener existing : listeners) {
            if (existing == listener) {
                return; // Already added
            }
        }
        listeners.add(listener);
    }
    
    /**
     * Remove a command listener
     */
    public synchronized void removeListener(CommandListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notify all listeners of a new command
     */
    private void notifyListeners(String cmd) {
        // Parse and notify
        TickCommand tickCmd = parseTickCommand(cmd);
        if (tickCmd != null) {
            synchronized (listeners) {
                for (CommandListener listener : listeners) {
                    listener.onTickCommand(cmd, tickCmd);
                }
            }
            return;
        }
        
        StoreCommand storeCmd = parseStoreCommand(cmd);
        if (storeCmd != null) {
            synchronized (listeners) {
                for (CommandListener listener : listeners) {
                    listener.onStoreCommand(cmd, storeCmd);
                }
            }
            return;
        }
        
        MemStoreCommand ramStoreCmd = parseRamStoreCommand(cmd);
        if (ramStoreCmd != null) {
            // Convert to store command format for listeners
            StoreCommand convertedStoreCmd = new StoreCommand(
                ramStoreCmd.getMemName() + "[" + ramStoreCmd.getAddress() + "]",
                ramStoreCmd.getValueStr());
            synchronized (listeners) {
                for (CommandListener listener : listeners) {
                    listener.onStoreCommand(cmd, convertedStoreCmd);
                }
            }
            return;
        }
        
        MemStoreCommand romStoreCmd = parseRomStoreCommand(cmd);
        if (romStoreCmd != null) {
            // Convert to store command format for listeners
            StoreCommand convertedStoreCmd = new StoreCommand(
                romStoreCmd.getMemName() + "[" + romStoreCmd.getAddress() + "]",
                romStoreCmd.getValueStr());
            synchronized (listeners) {
                for (CommandListener listener : listeners) {
                    listener.onStoreCommand(cmd, convertedStoreCmd);
                }
            }
            return;
        }
        
        PrintCommand printCmd = parsePrintCommand(cmd);
        if (printCmd != null) {
            synchronized (listeners) {
                for (CommandListener listener : listeners) {
                    listener.onPrintCommand(cmd, printCmd);
                }
            }
            return;
        }
    }
    
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
    private static final Pattern PRINT_PATTERN = 
        Pattern.compile("^print\\s+(ram|rom|register)\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    
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
            // Start stdin reader thread
            readerThread = new StdinReaderThread();
            readerThread.setDaemon(true);
            readerThread.start();
            
            // Start command timer for periodic checking (50ms interval)
            commandTimer = new java.util.Timer("StdCommandParser-Timer", true);
            commandTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    // Wake up any components waiting for commands by notifying listeners
                    // This ensures components check for new commands periodically
                    synchronized (listeners) {
                        if (!listeners.isEmpty()) {
                            for (CommandListener listener : listeners) {
                                // Trigger a notification - components will poll for commands
                            }
                        }
                    }
                }
            }, 50, 50);
            
            started = true;
        }
    }
    
    /**
     * Stop the command parser
     */
    public synchronized void stop() {
        if (commandTimer != null) {
            commandTimer.cancel();
            commandTimer = null;
        }
        started = false;
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
     * Clear all pending store commands
     * Call this after successfully processing store commands
     */
    public void clearStoreCommands() {
        String[] cmds = peekAllCommands();
        synchronized (commandQueue) {
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                // Only clear store commands (not tick, print, etc.)
                if (parseStoreCommand(cmd) != null || 
                    parseRamStoreCommand(cmd) != null || 
                    parseRomStoreCommand(cmd) != null ||
                    parseStoreFileCommand(cmd) != null) {
                    iter.remove();
                }
            }
        }
    }
    
    /**
     * Clear all pending tick commands
     * Call this after successfully processing tick commands
     */
    public void clearTickCommands() {
        String[] cmds = peekAllCommands();
        synchronized (commandQueue) {
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                // Only clear tick commands
                if (parseTickCommand(cmd) != null) {
                    iter.remove();
                }
            }
        }
    }
    
    /**
     * Clear all pending print commands
     * Call this after successfully processing print commands
     */
    public void clearPrintCommands() {
        String[] cmds = peekAllCommands();
        synchronized (commandQueue) {
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                // Only clear print commands
                if (parsePrintCommand(cmd) != null) {
                    iter.remove();
                }
            }
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
     * Parse a print command: print ram/rom/register name
     * @param command the command string
     * @return PrintCommand object, or null if not a valid print command
     */
    public PrintCommand parsePrintCommand(String command) {
        Matcher matcher = PRINT_PATTERN.matcher(command.trim());
        if (matcher.matches()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            return new PrintCommand(type, name);
        }
        return null;
    }
    
    /**
     * Process all pending commands and return store commands
     * Clears store commands immediately to prevent duplicate processing
     * @return array of store commands
     */
    public StoreCommand[] getStoreCommands() {
        // Get commands and clear immediately to prevent race conditions
        // where timer fires multiple times before commands are processed
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only store-related commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                if (parseStoreCommand(cmd) != null || 
                    parseRamStoreCommand(cmd) != null || 
                    parseRomStoreCommand(cmd) != null ||
                    parseStoreFileCommand(cmd) != null) {
                    iter.remove();
                }
            }
        }
        
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
     * Clears RAM store commands immediately to prevent duplicate processing
     * @return array of RAM store commands
     */
    public MemStoreCommand[] getRamStoreCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only RAM store commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                if (parseRamStoreCommand(cmd) != null) {
                    iter.remove();
                }
            }
        }
        
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
     * Clears ROM store commands immediately to prevent duplicate processing
     * @return array of ROM store commands
     */
    public MemStoreCommand[] getRomStoreCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only ROM store commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                if (parseRomStoreCommand(cmd) != null) {
                    iter.remove();
                }
            }
        }
        
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
     * Clears storefile commands immediately to prevent duplicate processing
     * @return array of RAM storefile commands
     */
    public MemStoreFileCommand[] getRamStoreFileCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only RAM storefile commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                MemStoreFileCommand store = parseStoreFileCommand(cmd);
                if (store != null && "ram".equalsIgnoreCase(store.getType())) {
                    iter.remove();
                }
            }
        }
        
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
     * Clears storefile commands immediately to prevent duplicate processing
     * @return array of ROM storefile commands
     */
    public MemStoreFileCommand[] getRomStoreFileCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only ROM storefile commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                MemStoreFileCommand store = parseStoreFileCommand(cmd);
                if (store != null && "rom".equalsIgnoreCase(store.getType())) {
                    iter.remove();
                }
            }
        }
        
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
     * Clears tick commands immediately to prevent duplicate processing
     * @return array of tick commands
     */
    public TickCommand[] getTickCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only tick commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                if (parseTickCommand(cmd) != null) {
                    iter.remove();
                }
            }
        }
        
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
     * Process all pending commands and return RAM print commands
     * Clears print commands immediately to prevent duplicate processing
     * @return array of print commands for RAM
     */
    public PrintCommand[] getPrintRamCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only print ram commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                PrintCommand print = parsePrintCommand(cmd);
                if (print != null && "ram".equalsIgnoreCase(print.getType())) {
                    iter.remove();
                }
            }
        }
        
        java.util.List<PrintCommand> prints = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            PrintCommand print = parsePrintCommand(cmd);
            if (print != null && "ram".equalsIgnoreCase(print.getType())) {
                prints.add(print);
            }
        }
        
        return prints.toArray(new PrintCommand[0]);
    }
    
    /**
     * Process all pending commands and return ROM print commands
     * Clears print commands immediately to prevent duplicate processing
     * @return array of print commands for ROM
     */
    public PrintCommand[] getPrintRomCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only print rom commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                PrintCommand print = parsePrintCommand(cmd);
                if (print != null && "rom".equalsIgnoreCase(print.getType())) {
                    iter.remove();
                }
            }
        }
        
        java.util.List<PrintCommand> prints = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            PrintCommand print = parsePrintCommand(cmd);
            if (print != null && "rom".equalsIgnoreCase(print.getType())) {
                prints.add(print);
            }
        }
        
        return prints.toArray(new PrintCommand[0]);
    }
    
    /**
     * Process all pending commands and return register print commands
     * Clears print commands immediately to prevent duplicate processing
     * @return array of print commands for registers
     */
    public PrintCommand[] getPrintRegisterCommands() {
        // Get commands and clear immediately to prevent race conditions
        String[] cmds;
        synchronized (commandQueue) {
            cmds = commandQueue.toArray(new String[0]);
            // Clear only print register commands
            java.util.Iterator<String> iter = commandQueue.iterator();
            while (iter.hasNext()) {
                String cmd = iter.next();
                PrintCommand print = parsePrintCommand(cmd);
                if (print != null && "register".equalsIgnoreCase(print.getType())) {
                    iter.remove();
                }
            }
        }
        
        java.util.List<PrintCommand> prints = new java.util.ArrayList<>();
        
        for (String cmd : cmds) {
            PrintCommand print = parsePrintCommand(cmd);
            if (print != null && "register".equalsIgnoreCase(print.getType())) {
                prints.add(print);
            }
        }
        
        return prints.toArray(new PrintCommand[0]);
    }
    
    /**
     * Daemon thread that reads from stdin or integrates with TtyInterface
     */
    private class StdinReaderThread extends Thread {
        
        // Reference to TtyInterface stdin thread (accessed via reflection)
        private Thread ttyThread = null;
        private java.lang.reflect.Method ttyGetBufferMethod = null;
        private boolean ttyMode = false;
        
        public StdinReaderThread() {
            super("StdCommandParser-StdinThread");
        }
        
        private void detectTtyMode() {
            // Check for tty mode via system property or command line args
            // Logisim Evolution sets various properties when running in tty mode
            String ttyProp = System.getProperty("logisim.tty");
            String[] args = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().toArray(new String[0]);
            
            for (String arg : args) {
                if (arg.contains("-tty") || arg.contains("tty")) {
                    ttyMode = true;
                    break;
                }
            }
            
            // Also try to find TtyInterface thread (it might be created later)
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                String name = t.getName().toLowerCase();
                if (name.contains("tty") && name.contains("stdin")) {
                    ttyMode = true;
                    // Try to get the getBuffer method
                    try {
                        Class<?> ttyClass = t.getClass();
                        ttyGetBufferMethod = ttyClass.getMethod("getBuffer");
                        ttyThread = t;
                    } catch (Exception e) {
                        // Ignore
                    }
                    break;
                }
            }
            
            if (ttyMode) {
                // Tty mode - will use stdin via TtyInterface
            } else {
                // Normal mode
            }
        }
        
        @Override
        public void run() {
            detectTtyMode();
            
            // Use blocking stdin reads in both modes
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line;
            
            while (true) {
                // Try to get commands from TtyInterface first (if available)
                if (ttyGetBufferMethod != null && ttyThread != null) {
                    try {
                        Object buffer = ttyGetBufferMethod.invoke(ttyThread);
                        if (buffer != null && buffer instanceof char[]) {
                            String cmd = new String((char[]) buffer).trim();
                            if (!cmd.isEmpty()) {
                                synchronized (commandQueue) {
                                    commandQueue.add(cmd);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore - will try again
                    }
                }
                
                // Also try stdin - use blocking read
                try {
                    // In tty mode, stdin.available() doesn't work, so use blocking read
                    line = reader.readLine();
                    if (line != null) {
                        String cmd = line.trim();
                        if (!cmd.isEmpty()) {
                            synchronized (commandQueue) {
                                commandQueue.add(cmd);
                            }
                        }
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
    
    /**
     * Represents a print command: print ram/rom/register name
     */
    public static class PrintCommand {
        private final String type;  // "ram", "rom", or "register"
        private final String name;
        
        public PrintCommand(String type, String name) {
            this.type = type;
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public String getName() {
            return name;
        }
        
        @Override
        public String toString() {
            return "print " + type + " " + name;
        }
    }
}
