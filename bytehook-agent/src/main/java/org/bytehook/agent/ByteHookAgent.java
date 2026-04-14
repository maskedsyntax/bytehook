package org.bytehook.agent;

import org.bytehook.core.instrument.ByteHookTransformer;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class ByteHookAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[ByteHookAgent] Loading...");
        String message = (agentArgs != null && !agentArgs.isEmpty()) ? agentArgs : "Hook Injected";
        
        inst.addTransformer(new ClassFileTransformer() {
            private final ByteHookTransformer transformer = new ByteHookTransformer();

            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                
                // Only instrument specific classes to avoid recursion/system issues
                if (className != null && className.startsWith("TestApp")) {
                    try {
                        return transformer.transform(classfileBuffer, message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null; // No transformation
            }
        });
    }
}
