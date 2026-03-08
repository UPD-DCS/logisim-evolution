## Build Steps

### Prerequisites
- Java JDK 21 or higher
- Logisim Evolution compiled classes (in `build/classes/java/main`)

### Build Instructions

1. **Build the main Logisim Evolution project**:
At the root of the repo
```bash
./gradlew classes --no-daemon -q
```

2. **Compile the test utils library**:
```bash
cd testutils
mkdir -p build/classes/java/main
javac -d build/classes/java/main -cp ../build/classes/java/main src/com/testutils/*.java
```

3. **Create the JAR file**:
```bash
jar cf build/libs/test-utils.jar -C build/classes/java/main com
```

## How to Use in Logisim Evolution

1. Start Logisim Evolution
2. Go to **Project > Load Library > Load JAR library**
3. Select the `test-utils.jar` file
4. When prompted for the class name, enter: `com.testutils.TestUtilsLibrary`
5. The components will appear in the "Test Utils" library

---

# Terminal Commands Reference

This document provides a quick reference for all terminal commands available in the Test Utils library for Logisim Evolution.

## Commands Overview

| Command | Description | Component |
|---------|-------------|-----------|
| `store` | Load value into a named register | Register Store |
| `store ram` | Load value into a specific RAM address | RAM Store |
| `store rom` | Load value into a specific ROM address | ROM Store |
| `storefile ram` | Load values from file into RAM | RAM Store |
| `storefile rom` | Load values from file into ROM | ROM Store |
| `tick` | Generate clock pulses | Clock Signal |

---

## Command Details

### 1. Store Register

Load a value into a named register.

**Syntax:**
```
store register_name=VALUE
```

**Value Formats:**
- Hexadecimal: `0xFF`, `0x1A`, `0xABCD`
- Decimal: `255`, `42`, `1000`
- Binary: `0b11111111`, `0b10101010`

**Examples:**
```
store myReg=0xFF        # Hex value
store counter=42        # Decimal value
store data=0b10101010   # Binary value
store result=0xFF00     # 16-bit hex value
```

---

### 2. Store RAM

Load a value into a specific memory address in a named RAM component.

**Syntax:**
```
store ram ram_name ADDRESS=VALUE
```

**Examples:**
```
store ram myRam 0x10=0xFF     # Set address 0x10 to 0xFF
store ram myRam 0=42          # Set address 0 to 42
store ram myRam 256=0xFF      # Decimal address
store ram myRam 0x1000=0xABCD # 16-bit value
```

---

### 3. Store ROM

Load a value into a specific memory address in a named ROM component.

**Syntax:**
```
store rom rom_name ADDRESS=VALUE
```

**Examples:**
```
store rom myRom 0x10=0xFF     # Set address 0x10 to 0xFF
store rom myRom 0=42          # Set address 0 to 42
store rom myRom 256=0xFF      # Decimal address
store rom myRom 0x1000=0xABCD # 16-bit value
```

---

### 4. Storefile RAM

Load multiple values from a text file into a RAM component. Values are written sequentially starting from address 0.

**Syntax:**
```
storefile ram ram_name filename
```

**File Format:**
- One value per line
- Hex values: `0xFF`, `0xAB12`
- Decimal values: `255`, `42`
- Comments: Lines starting with `#` or `//` are ignored
- Empty lines are ignored

**Example file (`memory.txt`):**
```
# Program memory contents
0x00
0x01
0x02
0x03
0xFF
0xAB
0x12
0x34
```

**Example usage:**
```
storefile ram myRam memory.txt
```

---

### 5. Storefile ROM

Load multiple values from a text file into a ROM component. Values are written sequentially starting from address 0.

**Syntax:**
```
storefile rom rom_name filename
```

**File Format:**
Same as Storefile RAM

**Example file (`program.txt`):**
```
// ROM program
0x3F  # opcode
0x00  # operand
0xFF  # end
```

**Example usage:**
```
storefile rom myRom program.txt
```

---

### 6. Tick

Generate clock pulses. The Clock Signal component outputs the pulses on its clock pin.

**Syntax:**
```
tick N
```

**Examples:**
```
tick 1      # Generate 1 clock pulse
tick 10     # Generate 10 clock pulses
tick 100    # Generate 100 clock pulses
tick 0.5    # Generate half a clock pulse
```

---

## Using Multiple Commands

You can send multiple commands, one per line. All commands in the same tick are processed together:

```
store myReg=0xFF
store ram myRam 0=10
store rom myRom 0=0xAA
tick 1
```

---

## Notes

1. **Clock Trigger**: Store commands (`store`, `store ram`, `store rom`, `storefile`) are only executed when the clock input of the Store component triggers (rising edge, falling edge, or level depending on configuration). (Will fix on the next version)

2. **Component Requirements**: 
   - Register Store component must be in the circuit for `store` commands
   - RAM Store component must be in the circuit for `store ram` and `storefile ram` commands
   - ROM Store component must be in the circuit for `store rom` and `storefile rom` commands
   - Clock Signal component must be in the circuit for `tick` commands

3. **Finding Components**: After loading the test-utils.jar library, the components appear in the "Test Utils" library in Logisim Evolution.

4. **Label Requirements**: Memory components (RAM/ROM) and registers must have labels set to be found by the store commands. Right-click the component and set the Label attribute.
