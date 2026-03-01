/*
 * Register Store Component for Logisim Evolution
 * Loads values into named registers via terminal command: store register_name=0xVALUE
 */

package com.testutils;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
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

import java.awt.Color;
import java.awt.Graphics;
import java.lang.reflect.Field;
import java.util.Set;

/**
 * Component that loads values into named registers via terminal commands.
 * Command format: store register_name=0xVALUE (e.g., store myReg=0xFF)
 */
public class RegisterStore extends InstanceFactory {
    
    public static final String _ID = "RegisterStore";
    
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
    
    public RegisterStore() {
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
            ps[CLOCK_PORT] = new Port(-20, 10, Port.INPUT, BitWidth.ONE);
        } else {
            // Evolution: clock on bottom (matching the new layout)
            ps[CLOCK_PORT] = new Port(30, 60, Port.INPUT, BitWidth.ONE);
        }
        instance.setPorts(ps);
    }
    
    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        Bounds bds = painter.getBounds();
        
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
            GraphicsUtil.drawCenteredText(g, "Store", bds.getX() + bds.getWidth() / 2, bds.getY() + 22);
            
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
            GraphicsUtil.drawCenteredText(g, "D", bds.getX() + 18, bds.getY() + 18);
            
            // Draw output indicator on the right side
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            g.drawLine(bds.getX() + 50, bds.getY() + 30, bds.getX() + 60, bds.getY() + 30);
            
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
            g.drawLine(bds.getX() + 30, bds.getY() + 50, bds.getX() + 30, bds.getY() + 60);
            
            // Draw trigger type
            GraphicsUtil.switchToWidth(g, 1);
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
            GraphicsUtil.drawCenteredText(g, triggerText, bds.getX() + 30, bds.getY() + 55);
            
            // Draw ports
            painter.drawPort(CLOCK_PORT);
        }
        
        // Draw label
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
            // Falling edge detection
            triggered = clockState.lastClock == Value.TRUE && clockValue == Value.FALSE;
            clockState.lastClock = clockValue;
        } else if (triggerType == StdAttr.TRIG_HIGH) {
            triggered = clockValue == Value.TRUE;
        } else if (triggerType == StdAttr.TRIG_LOW) {
            triggered = clockValue == Value.FALSE;
        }
        
        if (!triggered) {
            return; // Trigger condition not met, skip processing commands
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
        
        // Get all store commands from the parser
        StdCommandParser.StoreCommand[] storeCommands = parser.getStoreCommands();
        
        if (storeCommands.length == 0) {
            return;
        }
        
        // Get all components in the circuit
        Set<Component> components = circuitState.getCircuit().getComponents();
        
        // Process each store command
        for (StdCommandParser.StoreCommand cmd : storeCommands) {
            String targetRegName = cmd.getRegisterName();
            long value = cmd.parseValue();
            
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
                                System.out.println("[RegisterStore] Set " + targetRegName + " = 0x" 
                                    + Long.toHexString(maskedValue));
                            } catch (Exception e) {
                                System.err.println("[RegisterStore] Error setting register value: " + e.getMessage());
                            }
                        }
                        break; // Found the register, stop searching
                    }
                }
            }
        }
    }
    
    // Simple class to hold string getter
    static class LogisimStrings implements com.cburch.logisim.util.StringGetter {
        @Override
        public String toString() {
            return "Register Store";
        }
    }
}
