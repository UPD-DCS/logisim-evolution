/*
 * Register Printer Component for Logisim Evolution
 * Prints named register values to stdout via terminal command: print register <label>
 * Uses Timer-based polling to check for commands
 */

package com.cburch.logisim.testutils;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
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
import com.cburch.logisim.util.StringUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.Set;

import javax.swing.Timer;

public class RegisterPrinter extends InstanceFactory implements StdCommandParser.CommandListener {
    
    public static final String _ID = "RegisterPrinter";
    
    // Timer interval in milliseconds - check for commands frequently
    private static final int TIMER_INTERVAL_MS = 10;
    
    // Component size
    private static final int XSIZE = 60;
    private static final int YSIZE = 50;
    
    // Data class for Timer-based polling
    private static class PrinterData implements InstanceData, Cloneable, ActionListener {
        // Timer for checking commands
        private Timer timer;
        private InstanceComponent component;
        private Simulator simulator;
        
        PrinterData(InstanceState state) {
            // Initialize timer for checking commands
            component = state.getInstance().getComponent();
            simulator = state.getProject().getSimulator();
            timer = new Timer(TIMER_INTERVAL_MS, this);
            timer.start();
        }
        
        @Override
        public PrinterData clone() {
            try {
                return (PrinterData) super.clone();
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
    
    public RegisterPrinter() {
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
            GraphicsUtil.drawCenteredText(g, "Reg", bds.getX() + bds.getWidth() / 2, bds.getY() + 12);
            GraphicsUtil.drawCenteredText(g, "Print", bds.getX() + bds.getWidth() / 2, bds.getY() + 24);
            GraphicsUtil.drawCenteredText(g, "(poll)", bds.getX() + bds.getWidth() / 2, bds.getY() + 36);
        } else {
            // Evolution appearance - matches Store components style
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            
            g.drawRect(bds.getX() + 10, bds.getY() + 10, 40, 40);
            
            GraphicsUtil.switchToWidth(g, 1);
            g.setColor(Color.BLACK);
            GraphicsUtil.drawCenteredText(g, "Reg", bds.getX() + 30, bds.getY() + 22);
            GraphicsUtil.drawCenteredText(g, "Print", bds.getX() + 30, bds.getY() + 36);
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
        PrinterData data = (PrinterData) state.getData();
        if (data == null) {
            data = new PrinterData(state);
            state.setData(data);
        }
        
        // Get print commands from the parser
        StdCommandParser.PrintCommand[] printCommands = parser.getPrintRegisterCommands();
        
        if (printCommands.length == 0) {
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
        
        // Process each print command
        for (StdCommandParser.PrintCommand cmd : printCommands) {
            String targetName = cmd.getName();
            printRegisterValue(circuitState, targetName, "");
        }
        
        // Clear processed print commands
        parser.clearPrintCommands();
    }
    
    // CommandListener callback - called immediately when a print command is received
    @Override
    public void onPrintCommand(String command, StdCommandParser.PrintCommand printCmd) {
        // Don't clear here - let the timer-based processing handle it
    }
    
    // CommandListener callback - called when a tick command is received (not used by RegisterPrinter)
    @Override
    public void onTickCommand(String command, StdCommandParser.TickCommand tickCmd) {
        // Not used
    }
    
    // CommandListener callback - called when a store command is received (not used by RegisterPrinter)
    @Override
    public void onStoreCommand(String command, StdCommandParser.StoreCommand storeCmd) {
        // Not used
    }
    
    /**
     * Find and print a specific register by label
     */
    private void printRegisterValue(CircuitState circuitState, String targetName, String prefix) {
        // Get all components in this circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Iterate through all components
        for (Component comp : components) {
            // Check if this is a Register component
            if (comp.getFactory() instanceof Register) {
                // Get the label
                String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
                
                // Check if this is the target register
                if (label != null && label.equals(targetName)) {
                    // Get the register state from the circuit state
                    InstanceState regState = circuitState.getInstanceState(comp);
                    
                    if (regState != null) {
                        Object regData = regState.getData();
                        
                        if (regData != null) {
                            try {
                                // Use reflection to get the value field
                                Field valueField = regData.getClass().getDeclaredField("value");
                                valueField.setAccessible(true);
                                Object valueObj = valueField.get(regData);
                                
                                if (valueObj instanceof Value) {
                                    Value val = (Value) valueObj;
                                    String name = label;
                                    if (!prefix.isEmpty()) {
                                        name = prefix + "/" + name;
                                    }
                                    String hexValue = StringUtil.toHexString(val.getBitWidth().getWidth(), val.toLongValue());
                                    System.out.println("[RegisterPrinter] " + name + " = 0x" + hexValue + " (" + val.getBitWidth().getWidth() + " bits)");
                                    return;
                                }
                            } catch (Exception e) {
                                System.err.println("[RegisterPrinter] Error accessing register: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        
        // Recursively search sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            String subCircuitName = subState.getCircuit().getName();
            printRegisterValue(subState, targetName, prefix.isEmpty() ? subCircuitName : prefix + "/" + subCircuitName);
        }
    }
    
    private Location getRegisterOutputLocation(Component comp) {
        // Get the component's location
        Location baseLoc = comp.getLocation();
        
        // Check appearance attribute
        Object appearance = comp.getAttributeSet().getValue(StdAttr.APPEARANCE);
        
        int xOffset;
        int yOffset;
        
        if (appearance == com.cburch.logisim.instance.StdAttr.APPEAR_CLASSIC) {
            // Classic: output at (0, 0) relative to component location
            xOffset = 0;
            yOffset = 0;
        } else {
            // Evolution: output at (60, 30) relative to component location
            xOffset = 60;
            yOffset = 30;
        }
        
        // Location is immutable, create new one with offsets
        return Location.create(baseLoc.getX() + xOffset, baseLoc.getY() + yOffset, true);
    }
    
    // Simple class to hold string getter
    static class LogisimStrings implements com.cburch.logisim.util.StringGetter {
        @Override
        public String toString() {
            return "Register Printer";
        }
    }
}
