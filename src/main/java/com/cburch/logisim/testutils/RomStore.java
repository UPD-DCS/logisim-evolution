/*
 * ROM Store Component for Logisim Evolution
 * Loads values into named ROM components via terminal command: store rom rom_name address=0xVALUE
 * Example: store rom myRom 0x10=0xFF
 * Uses Timer-based polling to check for commands without requiring clock trigger
 */

package com.cburch.logisim.testutils;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.memory.MemContents;
import com.cburch.logisim.std.memory.Rom;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import javax.swing.Timer;

/**
 * Component that loads values into named ROM via terminal commands.
 * Uses Timer-based polling to check for commands without requiring clock trigger.
 * Command format: store rom rom_name address=0xVALUE (e.g., store rom myRom 0x10=0xFF)
 */
public class RomStore extends InstanceFactory implements StdCommandParser.CommandListener {
    
    public static final String _ID = "RomStore";
    
    // Timer interval in milliseconds - check for commands frequently
    private static final int TIMER_INTERVAL_MS = 10;
    
    // Component size
    private static final int XSIZE = 60;
    private static final int YSIZE = 50;
    
    // Data class for Timer-based polling
    private static class RomStoreData implements InstanceData, Cloneable, ActionListener {
        // Timer for checking commands
        private Timer timer;
        private InstanceComponent component;
        private Simulator simulator;
        
        RomStoreData(InstanceState state) {
            // Initialize timer for checking commands
            component = state.getInstance().getComponent();
            simulator = state.getProject().getSimulator();
            timer = new Timer(TIMER_INTERVAL_MS, this);
            timer.start();
        }
        
        @Override
        public RomStoreData clone() {
            try {
                return (RomStoreData) super.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            // Timer fired - trigger simulation update to process commands
            if (component != null) {
                component.fireInvalidated();
            }
            if (simulator != null) {
                simulator.nudge();
            }
        }
    }
    
    public RomStore() {
        super(_ID, new LogisimStrings());
        setAttributes(
            new Attribute[] { 
                StdAttr.LABEL, 
                StdAttr.LABEL_FONT, 
                StdAttr.LABEL_LOC,
                StdAttr.APPEARANCE
            },
            new Object[] { 
                "", 
                StdAttr.DEFAULT_LABEL_FONT, 
                StdAttr.LABEL_CENTER,
                AppPreferences.getDefaultAppearance()
            });
    }
    
    @Override
    public Bounds getOffsetBounds(AttributeSet attrs) {
        if (attrs.getValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
            return Bounds.create(-30, -10, 30, 40);
        } else {
            return Bounds.create(0, 0, 60, 60);
        }
    }
    
    @Override
    protected void configureNewInstance(com.cburch.logisim.instance.Instance instance) {
        instance.addAttributeListener();
        // Add a dummy input port to ensure component is in propagation path
        com.cburch.logisim.instance.Port[] ports = new com.cburch.logisim.instance.Port[1];
        ports[0] = new com.cburch.logisim.instance.Port(0, 20, com.cburch.logisim.instance.Port.INPUT, 1);
        instance.setPorts(ports);
        instance.computeLabelTextField(com.cburch.logisim.instance.Instance.AVOID_SIDES);
    }
    
    @Override
    protected void instanceAttributeChanged(com.cburch.logisim.instance.Instance instance, Attribute<?> attr) {
        if (attr == StdAttr.APPEARANCE) {
            instance.recomputeBounds();
            // Add a dummy input port to ensure component is in propagation path
            com.cburch.logisim.instance.Port[] ports = new com.cburch.logisim.instance.Port[1];
            ports[0] = new com.cburch.logisim.instance.Port(0, 20, com.cburch.logisim.instance.Port.INPUT, 1);
            instance.setPorts(ports);
            instance.computeLabelTextField(com.cburch.logisim.instance.Instance.AVOID_SIDES);
        } else if (attr == StdAttr.LABEL_LOC) {
            instance.computeLabelTextField(com.cburch.logisim.instance.Instance.AVOID_SIDES);
        }
    }
    
    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        Bounds bds = painter.getBounds();
        
        Object appearance = painter.getAttributeValue(StdAttr.APPEARANCE);
        boolean isClassic = (appearance == StdAttr.APPEAR_CLASSIC);
        
        Color componentColor = new Color(AppPreferences.COMPONENT_COLOR.get());
        Color secondaryColor = new Color(AppPreferences.COMPONENT_SECONDARY_COLOR.get());
        
        if (isClassic) {
            g.setColor(new Color(230, 230, 230));
            painter.drawBounds();
            g.setColor(Color.BLACK);
            
            GraphicsUtil.switchToWidth(g, 1);
            GraphicsUtil.drawCenteredText(g, "ROM", bds.getX() + bds.getWidth() / 2, bds.getY() + 12);
            GraphicsUtil.drawCenteredText(g, "Store", bds.getX() + bds.getWidth() / 2, bds.getY() + 24);
            GraphicsUtil.drawCenteredText(g, "(poll)", bds.getX() + bds.getWidth() / 2, bds.getY() + 36);
        } else {
            // Evolution appearance
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            
            g.drawRect(bds.getX() + 10, bds.getY() + 10, 40, 40);
            
            GraphicsUtil.switchToWidth(g, 1);
            g.setColor(Color.BLACK);
            GraphicsUtil.drawCenteredText(g, "ROM", bds.getX() + 30, bds.getY() + 22);
            GraphicsUtil.drawCenteredText(g, "Store", bds.getX() + 30, bds.getY() + 36);
        }
        
        painter.drawLabel();
    }
    
    @Override
    public void propagate(InstanceState state) {
        // Start the command parser if not already started
        StdCommandParser parser = StdCommandParser.getInstance();
        parser.start();
        
        // Get or create timer-based data
        RomStoreData data = (RomStoreData) state.getData();
        if (data == null) {
            data = new RomStoreData(state);
            state.setData(data);
        }
        
        // Get all ROM store commands from the parser
        StdCommandParser.MemStoreCommand[] storeCommands = parser.getRomStoreCommands();
        StdCommandParser.MemStoreFileCommand[] storeFileCommands = parser.getRomStoreFileCommands();
        
        if (storeCommands.length == 0 && storeFileCommands.length == 0) {
            return;
        }
        
        // Get the circuit state
        CircuitState circuitState;
        if (state instanceof InstanceStateImpl) {
            circuitState = ((InstanceStateImpl) state).getCircuitState();
        } else {
            circuitState = state.getProject().getCircuitState();
        }
        
        if (circuitState == null) {
            return;
        }
        
        // Process each store command
        for (StdCommandParser.MemStoreCommand cmd : storeCommands) {
            String targetMemName = cmd.getMemName();
            long address = cmd.parseAddress();
            long value = cmd.parseValue();
            
            // Find the ROM by label and apply
            int count = applyToMemory(circuitState, targetMemName, address, value);
            
            if (count > 0) {
                System.out.println("[RomStore] Set " + targetMemName + "[" + Long.toHexString(address) + "] = 0x" 
                    + Long.toHexString(value));
            }
        }
        
        // Process each storefile command
        for (StdCommandParser.MemStoreFileCommand cmd : storeFileCommands) {
            String targetMemName = cmd.getMemName();
            String fileName = cmd.getFileName();
            
            // Load data from file and apply to memory
            int count = applyFileToMemory(circuitState, targetMemName, fileName);
            
            if (count > 0) {
                System.out.println("[RomStore] Loaded " + count + " values from " + fileName + " to " + targetMemName);
            }
        }
    }
    
    /**
     * Apply store command to ROM memory
     * @param circuitState the circuit state
     * @param targetMemName memory label to find
     * @param address memory address
     * @param value value to store
     * @return number of memories updated
     */
    private int applyToMemory(CircuitState circuitState, String targetMemName, long address, long value) {
        int count = 0;
        
        // Get all components in the circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Find the ROM by label
        for (Component comp : components) {
            if (!(comp.getFactory() instanceof Rom)) {
                continue;
            }
            
            String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
            
            // Check if this is the target memory
            if (label != null && label.equals(targetMemName)) {
                // Get the memory state
                InstanceState memState = circuitState.getInstanceState(comp);
                
                // Use reflection to get the memory state and contents
                try {
                    // Get the getState method from the memory factory (via Mem superclass)
                    Method getStateMethod = comp.getFactory().getClass().getSuperclass()
                        .getDeclaredMethod("getState", com.cburch.logisim.instance.InstanceState.class);
                    getStateMethod.setAccessible(true);
                    Object stateObj = getStateMethod.invoke(comp.getFactory(), memState);
                    
                    if (stateObj != null) {
                        // Get the contents field - walk up class hierarchy
                        Field contentsField = null;
                        Class<?> cls = stateObj.getClass();
                        while (cls != null) {
                            try {
                                contentsField = cls.getDeclaredField("contents");
                                break;
                            } catch (NoSuchFieldException e) {
                                cls = cls.getSuperclass();
                            }
                        }
                        
                        if (contentsField != null) {
                            contentsField.setAccessible(true);
                            MemContents contents = (MemContents) contentsField.get(stateObj);
                            
                            if (contents != null) {
                                // Get data bit width
                                int dataBits = contents.getWidth();
                                
                                // Mask the value to fit the data width
                                long maskedValue = value & ((1L << dataBits) - 1);
                                
                                // Set the value at the address
                                contents.set(address, maskedValue);
                                
                                // Fire invalidated to notify the simulation that the memory changed
                                memState.fireInvalidated();
                                
                                count++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[RomStore] Error accessing memory: " + e.getMessage());
                }
            }
        }
        
        // Also check sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            count += applyToMemory(subState, targetMemName, address, value);
        }
        
        return count;
    }
    
    /**
     * Apply storefile command to ROM memory - reads hex values from file
     * @param circuitState the circuit state
     * @param targetMemName memory label to find
     * @param fileName file to read from
     * @return number of memory locations updated
     */
    private int applyFileToMemory(CircuitState circuitState, String targetMemName, String fileName) {
        int count = 0;
        
        // Read file and parse hex values
        java.util.List<Long> values = new java.util.ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                // Parse hex value (expecting 0x<hex> format)
                try {
                    if (line.startsWith("0x") || line.startsWith("0X")) {
                        values.add(Long.parseLong(line.substring(2), 16));
                    } else {
                        // Try as decimal
                        values.add(Long.parseLong(line));
                    }
                } catch (NumberFormatException e) {
                    System.err.println("[RomStore] Warning: Could not parse value '" + line + "' - skipping");
                }
            }
        } catch (IOException e) {
            System.err.println("[RomStore] Error reading file " + fileName + ": " + e.getMessage());
            return 0;
        }
        
        if (values.isEmpty()) {
            System.out.println("[RomStore] No values loaded from " + fileName);
            return 0;
        }
        
        // Get all components in the circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Find the ROM by label and get its data bit width
        int dataBits = 8; // default
        for (Component comp : components) {
            if (!(comp.getFactory() instanceof Rom)) {
                continue;
            }
            
            String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
            
            if (label != null && label.equals(targetMemName)) {
                // Get the memory state
                InstanceState memState = circuitState.getInstanceState(comp);
                
                try {
                    Method getStateMethod = comp.getFactory().getClass().getSuperclass()
                        .getDeclaredMethod("getState", com.cburch.logisim.instance.InstanceState.class);
                    getStateMethod.setAccessible(true);
                    Object stateObj = getStateMethod.invoke(comp.getFactory(), memState);
                    
                    if (stateObj != null) {
                        Field contentsField = null;
                        Class<?> cls = stateObj.getClass();
                        while (cls != null) {
                            try {
                                contentsField = cls.getDeclaredField("contents");
                                break;
                            } catch (NoSuchFieldException e) {
                                cls = cls.getSuperclass();
                            }
                        }
                        
                        if (contentsField != null) {
                            contentsField.setAccessible(true);
                            MemContents contents = (MemContents) contentsField.get(stateObj);
                            
                            if (contents != null) {
                                dataBits = contents.getWidth();
                                
                                // Write values to memory starting at address 0
                                long address = 0;
                                for (Long value : values) {
                                    long maskedValue = value & ((1L << dataBits) - 1);
                                    contents.set(address, maskedValue);
                                    
                                    // Fire invalidated to notify the simulation that the memory changed
                                    memState.fireInvalidated();
                                    
                                    count++;
                                    address++;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[RomStore] Error accessing memory: " + e.getMessage());
                }
            }
        }
        
        // Also check sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            count += applyFileToMemory(subState, targetMemName, fileName);
        }
        
        return count;
    }
    
    // Simple class to hold string getter
    static class LogisimStrings implements com.cburch.logisim.util.StringGetter {
        @Override
        public String toString() {
            return "ROM Store";
        }
    }
    
    // CommandListener callback - called immediately when a store command is received
    @Override
    public void onStoreCommand(String command, StdCommandParser.StoreCommand storeCmd) {
        // Don't clear here - let the timer-based processing handle it
    }
    
    // CommandListener callback - called when a tick command is received (not used by RomStore)
    @Override
    public void onTickCommand(String command, StdCommandParser.TickCommand tickCmd) {
        // Not used
    }
    
    // CommandListener callback - called when a print command is received (not used by RomStore)
    @Override
    public void onPrintCommand(String command, StdCommandParser.PrintCommand printCmd) {
        // Not used
    }
}
