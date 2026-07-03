package org.bytehook.core.instrument;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

public class JarProcessor {

    private final ByteHookTransformer transformer = new ByteHookTransformer();

    public void processJar(File inputJar, File outputPath, String message, ByteHookTransformer.HookType type, String methodFilter) throws IOException {
        try (JarInputStream jin = new JarInputStream(new FileInputStream(inputJar));
             JarOutputStream jout = new JarOutputStream(new FileOutputStream(outputPath), jin.getManifest())) {

            JarEntry entry;
            while ((entry = jin.getNextJarEntry()) != null) {
                String name = entry.getName();
                
                // Copy entry metadata
                JarEntry newEntry = new JarEntry(name);
                jout.putNextEntry(newEntry);

                if (name.endsWith(".class")) {
                    byte[] classBuffer = jin.readAllBytes();
                    try {
                        byte[] transformed = transformer.transform(classBuffer, message, type, methodFilter);
                        jout.write(transformed);
                    } catch (Exception e) {
                        System.err.println("Failed to transform " + name + ": " + e.getMessage());
                        jout.write(classBuffer); // fallback to original
                    }
                } else {
                    // Copy non-class resources as-is
                    jin.transferTo(jout);
                }
                jout.closeEntry();
            }
        }
    }
}
