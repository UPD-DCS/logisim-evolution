/*
 * Test Utils Library for Logisim Evolution
 * Provides testing utility components:
 * - RegisterPrinter: Print register values to stdout
 * - RamPrinter: Print RAM values to stdout
 * - RomPrinter: Print ROM values to stdout
 * - RegisterStore: Load values into named registers via terminal
 * - RamStore: Load values into named RAM via terminal
 * - RomStore: Load values into named ROM via terminal
 * - ClockSignal: Generate clock pulses via terminal
 */

package com.testutils;

import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.instance.InstanceFactory;

import java.util.ArrayList;
import java.util.List;

public class TestUtilsLibrary extends Library {
    
    private List<Tool> tools = null;
    
    public TestUtilsLibrary() {
        setHidden();
    }
    
    @Override
    public String getName() {
        return "TestUtils";
    }
    
    @Override
    public String getDisplayName() {
        return "Test Utils";
    }
    
    @Override
    public List<Tool> getTools() {
        if (tools == null) {
            tools = new ArrayList<>();
            // Register Printer component
            InstanceFactory registerPrinter = new RegisterPrinter();
            tools.add(new AddTool(registerPrinter));
            
            // RAM Printer component
            InstanceFactory ramPrinter = new RamPrinter();
            tools.add(new AddTool(ramPrinter));
            
            // ROM Printer component
            InstanceFactory romPrinter = new RomPrinter();
            tools.add(new AddTool(romPrinter));
            
            // Register Store component - loads values via terminal command
            InstanceFactory registerStore = new RegisterStore();
            tools.add(new AddTool(registerStore));
            
            // RAM Store component - loads values into named RAM via terminal command
            InstanceFactory ramStore = new RamStore();
            tools.add(new AddTool(ramStore));
            
            // ROM Store component - loads values into named ROM via terminal command
            InstanceFactory romStore = new RomStore();
            tools.add(new AddTool(romStore));
            
            // Clock Signal component - generates clock pulses via terminal
            InstanceFactory clockSignal = new ClockSignal();
            tools.add(new AddTool(clockSignal));
        }
        return tools;
    }
}
