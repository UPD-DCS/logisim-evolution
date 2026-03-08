/*
 * RAM Printer Component for Logisim Evolution
 * Prints named RAM memory values to stdout via terminal command: print ram <label>
 * Uses Timer-based polling to check for commands
 */

package com.testutils;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
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
import com.cburch.logisim.std.memory.MemContents;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import javax.swing.Timer;

public class RamPrinter extends InstanceFactory {
    
    public static final String _ID = "RamPrinter";
    
    // Timer interval in milliseconds - check for commands frequently
    private static final int TIMER_INTERVAL_MS = 50;
    
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
    
    public RamPrinter() {
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
        // No ports - this component uses timer-based polling
        instance.setPorts(new com.cburch.logisim.instance.Port[0]);
        instance.computeLabelTextField(com.cburch.logisim.instance.Instance.AVOID_SIDES);
    }
    
    @Override
    protected void instanceAttributeChanged(com.cburch.logisim.instance.Instance instance, Attribute<?> attr) {
        if (attr == StdAttr.APPEARANCE) {
            instance.recomputeBounds();
            instance.setPorts(new com.cburch.logisim.instance.Port[0]);
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
            GraphicsUtil.drawCenteredText(g, "RAM", bds.getX() + bds.getWidth() / 2, bds.getY() + 12);
            GraphicsUtil.drawCenteredText(g, "Print", bds.getX() + bds.getWidth() / 2, bds.getY() + 24);
            GraphicsUtil.drawCenteredText(g, "(poll)", bds.getX() + bds.getWidth() / 2, bds.getY() + 36);
        } else {
            // Evolution appearance - matches Store components style
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            
            g.drawRect(bds.getX() + 10, bds.getY() + 10, 40, 40);
            
            GraphicsUtil.switchToWidth(g, 1);
            g.setColor(Color.BLACK);
            GraphicsUtil.drawCenteredText(g, "RAM", bds.getX() + 30, bds.getY() + 22);
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
        StdCommandParser.PrintCommand[] printCommands = parser.getPrintRamCommands();
        
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
            printRamValue(circuitState, targetName, "");
        }
        
        // Clear processed commands
        parser.clearCommands();
    }
    
    /**
     * Find and print a specific RAM by label
     */
    private void printRamValue(CircuitState circuitState, String targetName, String prefix) {
        // Get all components in this circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Iterate through all components
        for (Component comp : components) {
            // Check if this is a RAM component
            if (comp.getFactory() instanceof Ram) {
                // Get the label
                String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
                
                // Check if this is the target RAM
                if (label != null && label.equals(targetName)) {
                    // Get the RAM state from the circuit state
                    InstanceState ramState = circuitState.getInstanceState(comp);
                    
                    // Use reflection to get the memory state and contents
                    try {
                        // Get the getState method from the Ram factory (via Mem superclass)
                        Method getStateMethod = Ram.class.getSuperclass().getDeclaredMethod("getState", com.cburch.logisim.instance.InstanceState.class);
                        getStateMethod.setAccessible(true);
                        Object memState = getStateMethod.invoke(comp.getFactory(), ramState);
                        
                        if (memState != null) {
                            // Get the contents field - walk up class hierarchy to find it
                            Field contentsField = null;
                            Class<?> cls = memState.getClass();
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
                                MemContents contents = (MemContents) contentsField.get(memState);
                                
                                if (contents != null) {
                                    String name = label;
                                    if (!prefix.isEmpty()) {
                                        name = prefix + "/" + name;
                                    }
                                    printRamContents(contents, name);
                                    return;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[RamPrinter] Error accessing RAM: " + e.getMessage());
                    }
                }
            }
        }
        
        // Recursively search sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            String subCircuitName = subState.getCircuit().getName();
            printRamValue(subState, targetName, prefix.isEmpty() ? subCircuitName : prefix + "/" + subCircuitName);
        }
    }
    
    /**
     * Print RAM contents in hex format
     */
    private void printRamContents(MemContents contents, String name) {
        int memSize = contents.getLogLength();
        int dataWidth = contents.getWidth();
        
        System.out.println("[RamPrinter] " + name + " (" + memSize + " x " + dataWidth + " bits):");
        
        // Print first 256 addresses or fewer if memory is smaller
        int maxAddr = Math.min(memSize, 256);
        
        for (int addr = 0; addr < maxAddr; addr++) {
            long value = contents.get(addr);
            String hexValue = Long.toHexString(value);
			if (value == 0){
				continue;
			}
            // Pad with zeros to match data width
            int hexDigits = (dataWidth + 3) / 4;
            hexValue = String.format("%0" + hexDigits + "X", value);
            System.out.println("  [0x" + String.format("%02X", addr) + "] = 0x" + hexValue);
        }
        
        if (memSize > 256) {
            System.out.println("  ... (" + (memSize - 256) + " more addresses)");
        }
    }
    
    // Simple class to hold string getter
    static class LogisimStrings implements com.cburch.logisim.util.StringGetter {
        @Override
        public String toString() {
            return "RAM Printer";
        }
    }
}
