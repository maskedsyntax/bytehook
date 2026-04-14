# ByteHook: Advanced Bytecode Instrumentation Workbench

ByteHook is a zero-dependency Java bytecode manipulation tool powered by the **JDK 25 ClassFile API**. It allows for live instrumentation, runtime transformation via a Java Agent, and visual verification through a side-by-side decompiler GUI.

## Modules

- `bytehook-core`: The transformation engine.
- `bytehook-decompiler`: A basic source reconstruction engine for verification.
- `bytehook-agent`: A Java Agent for runtime instrumentation.
- `bytehook-cli`: Command-line tool for offline class modification.
- `bytehook-gui`: A JavaFX workbench for visual bytecode analysis.

## Quick Start

### 1. Build the Project
```bash
./gradlew assemble
```

### 2. Use the Visual Workbench (GUI)
Load a `.class` file, type a message, and see the injected code instantly.
```bash
./gradlew :bytehook-gui:run
```

### 3. Use the Java Agent (Runtime)
Instrument any class starting with `TestApp` at load-time.
```bash
java -javaagent:bytehook-agent/build/libs/bytehook-agent.jar="My Hook" TestApp
```

### 4. Use the CLI (Offline)
Modify a `.class` file and save it as `-hooked.class`.
```bash
java -cp bytehook-cli/build/libs/bytehook-cli.jar:... org.bytehook.cli.Main MyClass.class "Injected!"
```

## Features
- **JDK 25 Native:** No external dependencies (no ASM, no ByteBuddy).
- **Live Diff View:** GUI shows original vs. instrumented source side-by-side.
- **AOP Hooks:** Easy method entry/exit interception.
- **High-Fidelity:** Preserves original logic while injecting synthetic instructions.
