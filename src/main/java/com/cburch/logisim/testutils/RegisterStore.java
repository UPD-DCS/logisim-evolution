/*
 * Register Store Component for Logisim Evolution
 * Loads values into named registers via terminal command: store register_name=0xVALUE
 * Uses Timer-based polling to check for commands without requiring clock trigger
 */

package com.cburch.logisim.testutils;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.memory.Register;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.Set;

import javax.swing.Timer;

/**
 * Component that loads values into named registers via terminal commands.
 * Uses Timer-based polling to check for commands without requiring clock trigger.
 * Command format: store register_name=0xVALUE (e.g., store myReg=0xFF)
 * Supports finding registers in subcircuits.
 */
public class RegisterStore extends InstanceFactory implements StdCommandParser.CommandListener {
    
    public static final String _ID = "RegisterStore";
    
    // Timer interval in milliseconds - check for commands frequently
    private static final int TIMER_INTERVAL_MS = 10;
    
    // Component size
    private static final int XSIZE = 60;
    private static final int YSIZE = 50;
    
    // Data class for Timer-based polling
    private static class RegisterStoreData implements InstanceData, Cloneable, ActionListener {
        // Timer for checking commands
        private Timer timer;
        private InstanceComponent component;
        private Simulator simulator;
        private int tickCount = 0;
        
        RegisterStoreData(InstanceState state) {
            // Initialize timer for checking commands
            component = state.getInstance().getComponent();
            simulator = state.getProject().getSimulator();
            timer = new Timer(TIMER_INTERVAL_MS, this);
            timer.start();
        }
        
        @Override
        public RegisterStoreData clone() {
            try {
                return (RegisterStoreData) super.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            tickCount++;
            // Timer fired - trigger simulation update to process commands
            if (component != null) {
                component.fireInvalidated();
            }
            if (simulator != null) {
                simulator.nudge();
            }
        }
    }
    
    public RegisterStore() {
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
        // This port can be left unconnected or connected to Ground
        com.cburch.logisim.instance.Port[] ports = new com.cburch.logisim.instance.Port[1];
        ports[0] = new com.cburch.logisim.instance.Port(0, 20, com.cburch.logisim.instance.Port.INPUT, 1);
        ports[0].setToolTip(new com.cburch.logisim.util.StringGetter() {
            @Override
            public String toString() {
                return "Connect to Ground or leave unconnected";
            }
        });
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
            GraphicsUtil.drawCenteredText(g, "Reg", bds.getX() + bds.getWidth() / 2, bds.getY() + 12);
            GraphicsUtil.drawCenteredText(g, "Store", bds.getX() + bds.getWidth() / 2, bds.getY() + 24);
            GraphicsUtil.drawCenteredText(g, "(poll)", bds.getX() + bds.getWidth() / 2, bds.getY() + 36);
        } else {
            // Evolution appearance - matches RomStore/RamStore style
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            
            g.drawRect(bds.getX() + 10, bds.getY() + 10, 40, 40);
            
            GraphicsUtil.switchToWidth(g, 1);
            g.setColor(Color.BLACK);
            GraphicsUtil.drawCenteredText(g, "Reg", bds.getX() + 30, bds.getY() + 22);
            GraphicsUtil.drawCenteredText(g, "Store", bds.getX() + 30, bds.getY() + 36);
        }
        
        // Draw label
        painter.drawLabel();
    }
    
    @Override
    public void propagate(InstanceState state) {
        // Start the command parser if not already started
        StdCommandParser parser = StdCommandParser.getInstance();
        parser.start();
        
        // Get or create timer-based data
        RegisterStoreData data = (RegisterStoreData) state.getData();
        if (data == null) {
            data = new RegisterStoreData(state);
            state.setData(data);
        }
        
        StdCommandParser.StoreCommand[] storeCommands = parser.getStoreCommands();
        
        if (storeCommands.length == 0) {
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
        
        // Get all components in the circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Process each store command
        for (StdCommandParser.StoreCommand cmd : storeCommands) {
            String targetRegName = cmd.getRegisterName();
            long value = cmd.parseValue();
            
            // Find the register by label in current circuit and subcircuits
            int count = applyToRegister(circuitState, targetRegName, value);
            
            if (count > 0) {
                System.out.println("[RegisterStore] Set " + targetRegName + " = 0x" 
                    + Long.toHexString(value));
            }
        }
        
        // Clear processed store commands
        parser.clearStoreCommands();
    }
    
    // CommandListener callback - called immediately when a store command is received
    @Override
    public void onStoreCommand(String command, StdCommandParser.StoreCommand storeCmd) {
        // Don't clear here - let the timer-based processing handle it
    }
    
    // CommandListener callback - called when a tick command is received (not used by RegisterStore)
    @Override
    public void onTickCommand(String command, StdCommandParser.TickCommand tickCmd) {
        // Not used
    }
    
    // CommandListener callback - called when a print command is received (not used by RegisterStore)
    @Override
    public void onPrintCommand(String command, StdCommandParser.PrintCommand printCmd) {
        // Not used
    }
    
    /**
     * Apply store command to register - searches recursively through subcircuits
     * @param circuitState the circuit state
     * @param targetRegName register label to find
     * @param value value to store
     * @return number of registers updated
     */
    private int applyToRegister(CircuitState circuitState, String targetRegName, long value) {
        int count = 0;
        
        // Get all components in the circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Find the register by label
        for (Component comp : components) {
            if (comp.getFactory() instanceof Register) {
                String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
                
                // Check if this is the target register
                if (label != null && label.equals(targetRegName)) {
                    // Get the register's data and set the value directly
                    // Note: RegisterData is package-private, so we use reflection
                    InstanceState regState = circuitState.getInstanceState(comp);
                    Object regData = regState.getData();
                    
                    if (regData != null) {
                        BitWidth width = regState.getAttributeValue(StdAttr.WIDTH);
                        if (width == null) {
                            width = BitWidth.create(8);
                        }
                        
                        // Mask the value to fit the register width
                        long maskedValue = value & ((1L << width.getWidth()) - 1);
                        
                        // Use reflection to set the value field
                        try {
                            Field valueField = regData.getClass().getDeclaredField("value");
                            valueField.setAccessible(true);
                            valueField.set(regData, Value.createKnown(width, maskedValue));
                            
                            // Fire invalidated to notify the simulation that the register value changed
                            regState.fireInvalidated();
                            
                            count++;
                        } catch (Exception e) {
                            System.err.println("[RegisterStore] Error setting register value: " + e.getMessage());
                        }
                    }
                    break; // Found the register, stop searching in this circuit
                }
            }
        }
        
        // Also check sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            count += applyToRegister(subState, targetRegName, value);
        }
        
        return count;
    }
    
    // Simple class to hold string getter
    static class LogisimStrings implements com.cburch.logisim.util.StringGetter {
        @Override
        public String toString() {
            return "Register Store";
        }
    }
}
