/*
 * Register Printer Component for Logisim Evolution
 * This component prints all named register values to stdout
 * based on a configurable clock trigger (rising edge, falling edge, high, low)
 */

package com.testutils;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.InstanceStateImpl;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.memory.Register;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.util.Set;

public class RegisterPrinter extends InstanceFactory {
    
    public static final String _ID = "RegisterPrinter";
    
    // Clock input port index
    private static final int CLOCK_PORT = 0;
    
    // Component size (matching Register)
    private static final int XSIZE = 60;
    private static final int YSIZE = 50;
    
    // Data class to store last clock value for edge detection
    private static class ClockData implements InstanceData, Cloneable {
        Value lastClock = Value.FALSE;
        
        @Override
        public ClockData clone() {
            try {
                return (ClockData) super.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
        
        // Returns true if the clock edge matches the trigger type
        boolean isTriggered(Value newClock, Object trigger) {
            Value oldClock = lastClock;
            lastClock = newClock;
            
            if (trigger == null || trigger == StdAttr.TRIG_RISING) {
                // Rising edge: old was FALSE, new is TRUE
                return oldClock == Value.FALSE && newClock == Value.TRUE;
            } else if (trigger == StdAttr.TRIG_FALLING) {
                // Falling edge: old was TRUE, new is FALSE
                return oldClock == Value.TRUE && newClock == Value.FALSE;
            } else if (trigger == StdAttr.TRIG_HIGH) {
                // High level: clock is TRUE
                return newClock == Value.TRUE;
            } else if (trigger == StdAttr.TRIG_LOW) {
                // Low level: clock is FALSE
                return newClock == Value.FALSE;
            } else {
                // Default to rising edge
                return oldClock == Value.FALSE && newClock == Value.TRUE;
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
            // Evolution appearance - matches the drawing layout
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
            // Classic: clock on left side, near top
            ps[CLOCK_PORT] = new Port(-20, 10, Port.INPUT, BitWidth.ONE);
        } else {
            // Evolution: clock on bottom (matching the new layout)
            ps[CLOCK_PORT] = new Port(0, 40, Port.INPUT, BitWidth.ONE);
        }
        instance.setPorts(ps);
    }
    
    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        Bounds bds = painter.getBounds();
        
        // Determine appearance
        Object appearance = painter.getAttributeValue(StdAttr.APPEARANCE);
        boolean isClassic = (appearance == StdAttr.APPEAR_CLASSIC);
        
        // Get component colors
        Color componentColor = new Color(AppPreferences.COMPONENT_COLOR.get());
        Color secondaryColor = new Color(AppPreferences.COMPONENT_SECONDARY_COLOR.get());
        
        if (isClassic) {
            // Classic appearance - use the classic rendering style
            g.setColor(new Color(230, 230, 230));
            painter.drawBounds();
            g.setColor(Color.BLACK);
            
            // Draw text - centered in the component
            GraphicsUtil.switchToWidth(g, 1);
            GraphicsUtil.drawCenteredText(g, "Register", bds.getX() + bds.getWidth() / 2, bds.getY() + 10);
            GraphicsUtil.drawCenteredText(g, "Printer", bds.getX() + bds.getWidth() / 2, bds.getY() + 22);
            
            // Draw trigger type indicator
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
            
            // Draw ports
            painter.drawPort(CLOCK_PORT);
        } else {
            // Evolution appearance - draw outline rectangle like other components
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            
            // Draw the main component outline rectangle (positioned like Register)
            g.drawRect(bds.getX() + 10, bds.getY() + 10, 40, 40);
            
            // Draw D label on the left side
            GraphicsUtil.switchToWidth(g, 1);
            g.setColor(Color.BLACK);
            GraphicsUtil.drawCenteredText(g, "Register Printer", bds.getX() + 18, bds.getY() + 18);
            
            // Draw output indicator on the right side

            
            // Draw clock symbol or enable indicator
            GraphicsUtil.switchToWidth(g, 1);
            Object trig = painter.getAttributeValue(StdAttr.TRIGGER);
            boolean isLatch = (trig == StdAttr.TRIG_HIGH || trig == StdAttr.TRIG_LOW);
            if (!isLatch) {
                painter.drawClockSymbol(bds.getX() + 10, bds.getY() + 40);
            } else {
                g.setColor(secondaryColor);
                GraphicsUtil.drawCenteredText(g, "E", bds.getX() + 18, bds.getY() + 40);
            }
            
            // Draw trigger indicator at bottom
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            g.drawLine(bds.getX(), bds.getY() + 40, bds.getX() + 10, bds.getY() + 40);
            

            
            // Draw ports
            painter.drawPort(CLOCK_PORT);
        }
        
        // Draw label
        painter.drawLabel();
    }
    
    @Override
    public void propagate(InstanceState state) {
        // Get the clock input value
        Value clockValue = state.getPortValue(CLOCK_PORT);
        
        // Get or create clock state data for edge detection
        ClockData clockData = (ClockData) state.getData();
        if (clockData == null) {
            clockData = new ClockData();
            state.setData(clockData);
        }
        
        // Get the trigger attribute
        Object triggerType = state.getAttributeValue(StdAttr.TRIGGER);
        
        // Check if the clock trigger condition is met
        if (!clockData.isTriggered(clockValue, triggerType)) {
            return; // Trigger condition not met, skip printing
        }
        
        // Get the circuit state - need to cast to InstanceStateImpl to get getCircuitState()
        CircuitState circuitState;
        if (state instanceof InstanceStateImpl) {
            circuitState = ((InstanceStateImpl) state).getCircuitState();
        } else {
            // Fallback: try to get from project
            circuitState = state.getProject().getCircuitState();
        }
        
        if (circuitState == null) {
            return;
        }
        
        // StringBuilder to collect all register values
        StringBuilder sb = new StringBuilder();
        sb.append("=== Register Values ===\n");
        
        int registerCount = 0;
        
        // Process the main circuit and all sub-circuits recursively
        registerCount = collectRegisterValues(circuitState, sb, registerCount, "");
        
        if (registerCount == 0) {
            sb.append("(No registers found)\n");
        }
        sb.append("=======================\n");
        
        // Print to stdout
        System.out.print(sb.toString());
    }
    
    /**
     * Recursively collect register values from a circuit state and all its sub-circuits
     */
    private int collectRegisterValues(CircuitState circuitState, StringBuilder sb, int registerCount, String prefix) {
        // Get all components in this circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Iterate through all components
        for (Component comp : components) {
            // Check if this is a Register component
            if (comp.getFactory() instanceof Register) {
                // Get the label
                String label = comp.getAttributeSet().getValue(StdAttr.LABEL);
                
                // Get the output location
                Location outLoc = getRegisterOutputLocation(comp);
                
                if (outLoc != null) {
                    Value val = circuitState.getValue(outLoc);
                    
                    if (val != null) {
                        registerCount++;
                        String name = (label != null && !label.isEmpty()) ? label : "unnamed_" + registerCount;
                        // Add circuit prefix if in sub-circuit
                        if (!prefix.isEmpty()) {
                            name = prefix + "/" + name;
                        }
                        String hexValue = StringUtil.toHexString(val.getBitWidth().getWidth(), val.toLongValue());
                        sb.append(String.format("%s: 0x%s (%d bits)\n", name, hexValue, val.getBitWidth().getWidth()));
                    }
                }
            }
        }
        
        // Recursively process sub-circuits
        Set<CircuitState> substates = circuitState.getSubstates();
        for (CircuitState subState : substates) {
            String subCircuitName = subState.getCircuit().getName();
            registerCount = collectRegisterValues(subState, sb, registerCount, prefix.isEmpty() ? subCircuitName : prefix + "/" + subCircuitName);
        }
        
        return registerCount;
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
