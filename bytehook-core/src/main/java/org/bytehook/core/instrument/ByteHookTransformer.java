package org.bytehook.core.instrument;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static java.lang.constant.ConstantDescs.*;

public class ByteHookTransformer {

    public enum HookType {
        LOGGING,
        TIMING
    }

    private static final ClassFile CLASS_FILE = ClassFile.of();
    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");
    private static final ClassDesc CD_PrintStream = ClassDesc.of("java.io.PrintStream");
    private static final MethodTypeDesc MTD_nanoTime = MethodTypeDesc.of(CD_long);
    private static final MethodTypeDesc MTD_println = MethodTypeDesc.of(CD_void, CD_String);

    public byte[] transform(byte[] classBuffer, String message, HookType type) {
        ClassModel classModel = CLASS_FILE.parse(classBuffer);

        return CLASS_FILE.transformClass(classModel, (classBuilder, classElement) -> {
            if (classElement instanceof MethodModel method) {
                String name = method.methodName().stringValue();
                if (name.equals("<init>") || name.equals("<clinit>")) {
                    classBuilder.with(method);
                    return;
                }

                classBuilder.transformMethod(method, (methodBuilder, methodElement) -> {
                    if (methodElement instanceof CodeModel codeModel) {
                        methodBuilder.transformCode(codeModel, new CodeTransform() {
                            int startSlot = -1;

                            @Override
                            public void accept(CodeBuilder builder, CodeElement element) {
                                if (startSlot == -1) {
                                    if (type == HookType.TIMING) {
                                        startSlot = builder.allocateLocal(TypeKind.LONG);
                                        builder.invokestatic(CD_System, "nanoTime", MTD_nanoTime)
                                               .lstore(startSlot);
                                    } else {
                                        injectLogging(builder, message + " [ENTER: " + name + "]");
                                        startSlot = 0; // Mark as initialized
                                    }
                                }

                                if (element instanceof ReturnInstruction) {
                                    if (type == HookType.TIMING) {
                                        injectTimingExit(builder, name, startSlot);
                                    } else {
                                        injectLogging(builder, message + " [EXIT: " + name + "]");
                                    }
                                }
                                builder.with(element);
                            }
                        });
                    } else {
                        methodBuilder.with(methodElement);
                    }
                });
            } else {
                classBuilder.with(classElement);
            }
        });
    }

    private void injectLogging(CodeBuilder builder, String message) {
        builder.getstatic(CD_System, "out", CD_PrintStream)
               .ldc(message)
               .invokevirtual(CD_PrintStream, "println", MTD_println);
    }

    private void injectTimingExit(CodeBuilder builder, String methodName, int startSlot) {
        // long duration = System.nanoTime() - start;
        // System.out.println("Method " + methodName + " took " + duration + " ns");
        builder.getstatic(CD_System, "out", CD_PrintStream)
               .ldc("Method " + methodName + " took ")
               .invokevirtual(CD_PrintStream, "print", MethodTypeDesc.of(CD_void, CD_String))
               .getstatic(CD_System, "out", CD_PrintStream)
               .invokestatic(CD_System, "nanoTime", MTD_nanoTime)
               .lload(startSlot)
               .lsub()
               .invokevirtual(CD_PrintStream, "print", MethodTypeDesc.of(CD_void, CD_long))
               .getstatic(CD_System, "out", CD_PrintStream)
               .ldc(" ns")
               .invokevirtual(CD_PrintStream, "println", MTD_println);
    }
}
