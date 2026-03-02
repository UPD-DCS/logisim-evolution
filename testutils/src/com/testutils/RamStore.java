/*
 * RAM Store Component for Logisim Evolution
 * Loads values into named RAM components via terminal command: store ram ram_name address=0xVALUE
 * Example: store ram myRam 0x10=0xFF
 */

package com.testutils;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.memory.MemContents;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Component that loads values into named RAM via terminal commands.
 * Command format: store ram ram_name address=0xVALUE (e.g., store ram myRam 0x10=0xFF)
 */
public class RamStore extends InstanceFactory {
    
    public static final String _ID = "RamStore";
    
    // Clock input port index
    private static final int CLOCK_PORT = 0;
    
    // Component size
    private static final int XSIZE = 60;
    private static final int YSIZE = 50;
    
    // Data class to track last clock value for edge detection
    private static class ClockState implements InstanceData, Cloneable {
        Value lastClock = Value.FALSE;
        
        @Override
        public ClockState clone() {
            try {
                return (ClockState) super.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
        
        boolean isRisingEdge(Value newClock) {
            boolean rising = lastClock == Value.FALSE && newClock == Value.TRUE;
            lastClock = newClock;
            return rising;
        }
    }
    
    public RamStore() {
        super(_ID, new LogisimStrings());
        setAttributes(
            new Attribute[] { 
                StdAttr.LABEL, 
                StdAttr.LABEL_FONT, 
                StdAttr.LABEL_LOC,
                StdAttr.TRIGGER,
                StdAttr.APPEARANCE
            },
            new Object[] { 
                "", 
                StdAttr.DEFAULT_LABEL_FONT, 
                StdAttr.LABEL_CENTER,
                StdAttr.TRIG_RISING,
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
        updatePorts(instance);
        instance.computeLabelTextField(com.cburch.logisim.instance.Instance.AVOID_SIDES);
    }
    
    @Override
    protected void instanceAttributeChanged(com.cburch.logisim.instance.Instance instance, Attribute<?> attr) {
        if (attr == StdAttr.APPEARANCE) {
            instance.recomputeBounds();
            updatePorts(instance);
            instance.computeLabelTextField(com.cburch.logisim.instance.Instance.AVOID_SIDES);
        } else if (attr == StdAttr.LABEL_LOC) {
            instance.computeLabelTextField(com.cburch.logisim.instance.Instance.AVOID_SIDES);
        }
    }
    
    private void updatePorts(com.cburch.logisim.instance.Instance instance) {
        final var ps = new Port[1];
        if (instance.getAttributeValue(StdAttr.APPEARANCE) == StdAttr.APPEAR_CLASSIC) {
            ps[CLOCK_PORT] = new Port(-20, 10, Port.INPUT, BitWidth.ONE);
        } else {
            ps[CLOCK_PORT] = new Port(0, 40, Port.INPUT, BitWidth.ONE);
        }
        instance.setPorts(ps);
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
            GraphicsUtil.drawCenteredText(g, "RAM", bds.getX() + bds.getWidth() / 2, bds.getY() + 10);
            GraphicsUtil.drawCenteredText(g, "Store", bds.getX() + bds.getWidth() / 2, bds.getY() + 22);
            
            // Draw trigger type
            Object trigger = painter.getAttributeValue(StdAttr.TRIGGER);
            String triggerText;
            if (trigger == StdAttr.TRIG_RISING) {
                triggerText = "^>";
            } else if (trigger == StdAttr.TRIG_FALLING) {
                triggerText = "v>";
            } else if (trigger == StdAttr.TRIG_HIGH) {
                triggerText = "1";
            } else if (trigger == StdAttr.TRIG_LOW) {
                triggerText = "0";
            } else {
                triggerText = "^>";
            }
            GraphicsUtil.drawCenteredText(g, triggerText, bds.getX() + bds.getWidth() / 2, bds.getY() + 34);
            
            painter.drawPort(CLOCK_PORT);
        } else {
            // Evolution appearance
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            
            g.drawRect(bds.getX() + 10, bds.getY() + 10, 40, 40);
            
            GraphicsUtil.switchToWidth(g, 1);
            g.setColor(Color.BLACK);
            GraphicsUtil.drawCenteredText(g, "RAM", bds.getX() + 18, bds.getY() + 18);
            GraphicsUtil.drawCenteredText(g, "Store", bds.getX() + 18, bds.getY() + 30);
            
            // Draw clock symbol
            Object trig = painter.getAttributeValue(StdAttr.TRIGGER);
            boolean isLatch = (trig == StdAttr.TRIG_HIGH || trig == StdAttr.TRIG_LOW);
            if (!isLatch) {
                painter.drawClockSymbol(bds.getX() + 10, bds.getY() + 40);
            } else {
                g.setColor(secondaryColor);
                GraphicsUtil.drawCenteredText(g, "E", bds.getX() + 18, bds.getY() + 40);
            }
            
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            g.drawLine(bds.getX(), bds.getY() + 40, bds.getX() + 10, bds.getY() + 40);
            
            painter.drawPort(CLOCK_PORT);
        }
        
        painter.drawLabel();
    }
    
    @Override
    public void propagate(InstanceState state) {
        // Start the command parser if not already started
        StdCommandParser parser = StdCommandParser.getInstance();
        parser.start();
        
        // Get the clock input value
        Value clockValue = state.getPortValue(CLOCK_PORT);
        
        // Get or create clock state data for edge detection
        ClockState clockState = (ClockState) state.getData();
        if (clockState == null) {
            clockState = new ClockState();
            state.setData(clockState);
        }
        
        // Get the trigger attribute
        Object triggerType = state.getAttributeValue(StdAttr.TRIGGER);
        
        // Check if the clock trigger condition is met
        boolean triggered = false;
        if (triggerType == null || triggerType == StdAttr.TRIG_RISING) {
            triggered = clockState.isRisingEdge(clockValue);
        } else if (triggerType == StdAttr.TRIG_FALLING) {
            triggered = clockState.lastClock == Value.TRUE && clockValue == Value.FALSE;
            clockState.lastClock = clockValue;
        } else if (triggerType == StdAttr.TRIG_HIGH) {
            triggered = clockValue == Value.TRUE;
        } else if (triggerType == StdAttr.TRIG_LOW) {
            triggered = clockValue == Value.FALSE;
        }
        
        if (!triggered) {
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
        
        // Get all RAM store commands from the parser
        StdCommandParser.MemStoreCommand[] storeCommands = parser.getRamStoreCommands();
        StdCommandParser.MemStoreFileCommand[] storeFileCommands = parser.getRamStoreFileCommands();
        
        if (storeCommands.length == 0 && storeFileCommands.length == 0) {
            return;
        }
        
        // Process each store command
        for (StdCommandParser.MemStoreCommand cmd : storeCommands) {
            String targetMemName = cmd.getMemName();
            long address = cmd.parseAddress();
            long value = cmd.parseValue();
            
            // Find the RAM by label
            int count = applyToMemory(circuitState, targetMemName, address, value, false);
            
            if (count > 0) {
                System.out.println("[RamStore] Set " + targetMemName + "[" + Long.toHexString(address) + "] = 0x" 
                    + Long.toHexString(value));
            }
        }
        
        // Process each storefile command
        for (StdCommandParser.MemStoreFileCommand cmd : storeFileCommands) {
            String targetMemName = cmd.getMemName();
            String fileName = cmd.getFileName();
            
            // Load data from file and apply to memory
            int count = applyFileToMemory(circuitState, targetMemName, fileName, false);
            
            if (count > 0) {
                System.out.println("[RamStore] Loaded " + count + " values from " + fileName + " to " + targetMemName);
            }
        }
        
        // Clear processed commands
        parser.clearCommands();
    }
    
    /**
     * Apply store command to memory (RAM or ROM)
     * @param circuitState the circuit state
     * @param targetMemName memory label to find
     * @param address memory address
     * @param value value to store
     * @param isRom true for ROM, false for RAM
     * @return number of memories updated
     */
    private int applyToMemory(CircuitState circuitState, String targetMemName, long address, long value, boolean isRom) {
        int count = 0;
        
        // Get all components in the circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Find the memory by label
        for (Component comp : components) {
            if (isRom && !(comp.getFactory() instanceof com.cburch.logisim.std.memory.Rom)) {
                continue;
            }
            if (!isRom && !(comp.getFactory() instanceof Ram)) {
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
                                count++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[RamStore] Error accessing memory: " + e.getMessage());
                }
            }
        }
        
        // Also check sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            count += applyToMemory(subState, targetMemName, address, value, isRom);
        }
        
        return count;
    }
    
    /**
     * Apply storefile command to memory - reads hex values from file
     * @param circuitState the circuit state
     * @param targetMemName memory label to find
     * @param fileName file to read from
     * @param isRom true for ROM, false for RAM
     * @return number of memory locations updated
     */
    private int applyFileToMemory(CircuitState circuitState, String targetMemName, String fileName, boolean isRom) {
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
                    System.err.println("[RamStore] Warning: Could not parse value '" + line + "' - skipping");
                }
            }
        } catch (IOException e) {
            System.err.println("[RamStore] Error reading file " + fileName + ": " + e.getMessage());
            return 0;
        }
        
        if (values.isEmpty()) {
            System.out.println("[RamStore] No values loaded from " + fileName);
            return 0;
        }
        
        // Get all components in the circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Find the memory by label and get its data bit width
        int dataBits = 8; // default
        for (Component comp : components) {
            if (isRom && !(comp.getFactory() instanceof com.cburch.logisim.std.memory.Rom)) {
                continue;
            }
            if (!isRom && !(comp.getFactory() instanceof Ram)) {
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
                                    count++;
                                    address++;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[RamStore] Error accessing memory: " + e.getMessage());
                }
            }
        }
        
        // Also check sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            count += applyFileToMemory(subState, targetMemName, fileName, isRom);
        }
        
        return count;
    }
    
    // Simple class to hold string getter
    static class LogisimStrings implements com.cburch.logisim.util.StringGetter {
        @Override
        public String toString() {
            return "RAM Store";
        }
    }
}
