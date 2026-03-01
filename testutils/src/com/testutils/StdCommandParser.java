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
            StoreCommand store = parseStoreCommand(cmd);
            if (store != null) {
                stores.add(store);
            }
        }
        
        return stores.toArray(new StoreCommand[0]);
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
