/*
 * Test Utils Library for Logisim Evolution
 * Provides testing utility components:
 * - TestUtils: Register Printer
 * - RamPrinter: RAM Printer
 * - RomPrinter: ROM Printer
 * - RegisterStore: Load values into named registers via terminal
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
            
            // Clock Signal component - generates clock pulses via terminal
            InstanceFactory clockSignal = new ClockSignal();
            tools.add(new AddTool(clockSignal));
        }
        return tools;
    }
}
