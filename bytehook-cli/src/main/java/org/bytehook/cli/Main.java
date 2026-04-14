package org.bytehook.cli;

import org.bytehook.core.instrument.ByteHookTransformer;
import org.bytehook.decompiler.ByteHookDecompiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java -jar bytehook-cli.jar <class-file> <message>");
            System.exit(1);
        }

        Path inputPath = Paths.get(args[0]);
        String message = args[1];

        if (!Files.exists(inputPath)) {
            System.err.println("Error: File " + inputPath + " does not exist.");
            System.exit(1);
        }

        byte[] inputBytes = Files.readAllBytes(inputPath);
        ByteHookTransformer transformer = new ByteHookTransformer();
        byte[] outputBytes = transformer.transform(inputBytes, message);

        Path outputPath = inputPath.resolveSibling(inputPath.getFileName().toString().replace(".class", "-hooked.class"));
        Files.write(outputPath, outputBytes);

        System.out.println("Transformed class saved to: " + outputPath);

        System.out.println("\n--- Decompiled Instrumented Source ---");
        ByteHookDecompiler decompiler = new ByteHookDecompiler();
        System.out.println(decompiler.decompile(outputBytes));
    }
}
