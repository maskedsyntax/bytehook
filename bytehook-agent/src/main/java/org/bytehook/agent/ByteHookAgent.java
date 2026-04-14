package org.bytehook.agent;

import org.bytehook.core.instrument.ByteHookTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class ByteHookAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[ByteHookAgent] Loading...");
        
        String message = "Hook Injected";
        ByteHookTransformer.HookType type = ByteHookTransformer.HookType.LOGGING;

        if (agentArgs != null && !agentArgs.isEmpty()) {
            String[] parts = agentArgs.split(",", 2);
            try {
                type = ByteHookTransformer.HookType.valueOf(parts[0].toUpperCase());
                if (parts.length > 1) {
                    message = parts[1];
                }
            } catch (IllegalArgumentException e) {
                // First part wasn't a hook type, treat whole thing as message
                message = agentArgs;
            }
        }
        
        final String finalMessage = message;
        final ByteHookTransformer.HookType finalType = type;

        inst.addTransformer(new ClassFileTransformer() {
            private final ByteHookTransformer transformer = new ByteHookTransformer();

            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                
                // Only instrument specific classes to avoid recursion/system issues
                if (className != null && className.startsWith("samples/")) {
                    try {
                        return transformer.transform(classfileBuffer, finalMessage, finalType);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null; // No transformation
            }
        });
    }
}
