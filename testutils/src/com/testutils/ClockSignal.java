/*
 * Clock Signal Component for Logisim Evolution
 * Generates clock pulses via terminal command: tick n
 * This version ticks all existing clocks in the circuit rather than emitting its own clock signal.
 */

package com.testutils;

import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.GraphicsUtil;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

/**
 * Component that generates clock pulses via terminal commands.
 * Command format: tick n (e.g., tick 10 or tick 1.5)
 * Each tick generates a full clock cycle for all clocks in the circuit
 * The component no longer has an output port - it directly controls the simulation
 */
public class ClockSignal extends InstanceFactory {
    
    public static final String _ID = "ClockSignal";
    
    // Component size
    private static final int XSIZE = 60;
    private static final int YSIZE = 50;
    
    // Timer interval in milliseconds - check for commands frequently
    private static final int TIMER_INTERVAL_MS = 50;
    
    // Data class to track clock state
    private static class ClockSignalData implements InstanceData, Cloneable, ActionListener {
        double pendingTicks = 0;    // Number of ticks to generate (can be fractional)
        
        // Timer for checking commands and triggering simulation
        private Timer timer;
        private InstanceComponent component;
        private Simulator simulator;
        
        ClockSignalData(InstanceState state) {
            // Initialize timer for checking commands
            component = state.getInstance().getComponent();
            simulator = state.getProject().getSimulator();
            timer = new Timer(TIMER_INTERVAL_MS, this);
            timer.start();
        }
        
        @Override
        public ClockSignalData clone() {
            try {
                return (ClockSignalData) super.clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
        
        @Override
        public void actionPerformed(ActionEvent e) {
            // Timer fired - trigger simulation update
            if (component != null) {
                component.fireInvalidated();
            }
            if (simulator != null) {
                simulator.nudge();
            }
        }
    }
    
    public ClockSignal() {
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
            // Evolution appearance - no ports
            return Bounds.create(0, 0, 60, 60);
        }
    }
    
    @Override
    protected void configureNewInstance(com.cburch.logisim.instance.Instance instance) {
        instance.addAttributeListener();
        // No ports - this component has no connections
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
        
        // Determine appearance
        Object appearance = painter.getAttributeValue(StdAttr.APPEARANCE);
        boolean isClassic = (appearance == StdAttr.APPEAR_CLASSIC);
        
        // Get component colors
        Color componentColor = new Color(AppPreferences.COMPONENT_COLOR.get());
        Color secondaryColor = new Color(AppPreferences.COMPONENT_SECONDARY_COLOR.get());
        
        if (isClassic) {
            // Classic appearance
            g.setColor(new Color(230, 230, 230));
            painter.drawBounds();
            g.setColor(Color.BLACK);
            
            // Draw text
            GraphicsUtil.switchToWidth(g, 1);
            GraphicsUtil.drawCenteredText(g, "Clock", bds.getX() + bds.getWidth() / 2, bds.getY() + 10);
            GraphicsUtil.drawCenteredText(g, "Signal", bds.getX() + bds.getWidth() / 2, bds.getY() + 22);
            
            // Draw tick count indicator
            ClockSignalData data = (ClockSignalData) painter.getData();
            String statusText;
            if (data != null && data.pendingTicks > 0) {
                statusText = "P:" + data.pendingTicks;
            } else {
                statusText = "Idle";
            }
            GraphicsUtil.drawCenteredText(g, statusText, bds.getX() + bds.getWidth() / 2, bds.getY() + 34);
        } else {
            // Evolution appearance - draw outline rectangle like other components
            g.setColor(componentColor);
            GraphicsUtil.switchToWidth(g, 2);
            
            // Draw the main component outline rectangle
            g.drawRect(bds.getX() + 10, bds.getY() + 10, 40, 40);
            
            // Draw clock symbol in the center
            GraphicsUtil.switchToWidth(g, 1);
            g.setColor(Color.BLACK);
            painter.drawClockSymbol(bds.getX() + 20, bds.getY() + 25);
            
            // Draw status indicator at bottom
            GraphicsUtil.switchToWidth(g, 1);
            ClockSignalData data = (ClockSignalData) painter.getData();
            String statusText;
            if (data != null && data.pendingTicks > 0) {
                statusText = "P:" + data.pendingTicks;
                g.setColor(secondaryColor);
            } else {
                statusText = "Idle";
                g.setColor(componentColor);
            }
            GraphicsUtil.drawCenteredText(g, statusText, bds.getX() + 30, bds.getY() + 55);
        }
        
        // Draw label
        painter.drawLabel();
    }
    
    @Override
    public void propagate(InstanceState state) {
        // Start the command parser if not already started
        StdCommandParser parser = StdCommandParser.getInstance();
        parser.start();
        
        // Get or create clock state data
        ClockSignalData data = (ClockSignalData) state.getData();
        if (data == null) {
            data = new ClockSignalData(state);
            state.setData(data);
        }
        
        // Check for new tick commands
        StdCommandParser.TickCommand[] tickCommands = parser.getTickCommands();
        
        // Add pending ticks from new commands
        for (StdCommandParser.TickCommand cmd : tickCommands) {
            data.pendingTicks += cmd.getCount() * 2;
            System.out.println("[ClockSignal] Received tick command: " + cmd.getCount() 
                + " (total pending: " + data.pendingTicks + ")");
        }
        
        // If we have pending ticks, process them
        if (data.pendingTicks > 0) {
            // Get the simulator
            Simulator simulator = state.getProject().getSimulator();
            
            if (simulator != null) {
                // Determine how many full ticks to perform
                int fullTicks = (int) data.pendingTicks;
                
                if (fullTicks > 0) {
                    // Tick all clocks in the circuit
                    simulator.tick(fullTicks);
                    System.out.println("[ClockSignal] Ticked " + fullTicks + " clock cycle(s)");
                    data.pendingTicks -= fullTicks;
                }
                

            }
        }
    }
    
    // Simple class to hold string getter
    static class LogisimStrings implements com.cburch.logisim.util.StringGetter {
        @Override
        public String toString() {
            return "Clock Signal";
        }
    }
}
