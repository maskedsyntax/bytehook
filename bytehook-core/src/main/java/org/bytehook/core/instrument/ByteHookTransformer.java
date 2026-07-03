package org.bytehook.core.instrument;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import static java.lang.constant.ConstantDescs.*;

public class ByteHookTransformer {

    public enum HookType {
        LOGGING,
        TIMING,
        ARGUMENTS
    }

    private static final ClassFile CLASS_FILE = ClassFile.of();
    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");
    private static final ClassDesc CD_PrintStream = ClassDesc.of("java.io.PrintStream");
    private static final ClassDesc CD_Object = ClassDesc.of("java.lang.Object");
    private static final MethodTypeDesc MTD_nanoTime = MethodTypeDesc.of(CD_long);
    private static final MethodTypeDesc MTD_println = MethodTypeDesc.of(CD_void, CD_String);
    private static final MethodTypeDesc MTD_print_str = MethodTypeDesc.of(CD_void, CD_String);
    private static final MethodTypeDesc MTD_valueOf_int = MethodTypeDesc.of(CD_String, CD_int);
    private static final MethodTypeDesc MTD_valueOf_long = MethodTypeDesc.of(CD_String, CD_long);
    private static final MethodTypeDesc MTD_valueOf_obj = MethodTypeDesc.of(CD_String, CD_Object);

    public byte[] transform(byte[] classBuffer, String message, HookType type, String methodFilter) {
        ClassModel classModel = CLASS_FILE.parse(classBuffer);

        return CLASS_FILE.transformClass(classModel, (classBuilder, classElement) -> {
            if (classElement instanceof MethodModel method) {
                String name = method.methodName().stringValue();
                String desc = method.methodType().stringValue();
                boolean isStatic = method.flags().has(java.lang.reflect.AccessFlag.STATIC);
                
                // Check filter
                if (methodFilter != null && !methodFilter.isEmpty() && !name.matches(methodFilter)) {
                    classBuilder.with(method);
                    return;
                }

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
                                    } else if (type == HookType.ARGUMENTS) {
                                        injectArgumentLogging(builder, name, desc, isStatic);
                                        startSlot = 0;
                                    } else {
                                        injectLogging(builder, message + " [ENTER: " + name + "]");
                                        startSlot = 0; 
                                    }
                                }

                                if (element instanceof ReturnInstruction) {
                                    if (type == HookType.TIMING) {
                                        injectTimingExit(builder, name, startSlot);
                                    } else if (type != HookType.ARGUMENTS) {
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

    private void injectArgumentLogging(CodeBuilder builder, String methodName, String desc, boolean isStatic) {
        builder.getstatic(CD_System, "out", CD_PrintStream)
               .ldc("Entering " + methodName + "(")
               .invokevirtual(CD_PrintStream, "print", MTD_print_str);

        int slot = isStatic ? 0 : 1;
        MethodTypeDesc mtd = MethodTypeDesc.ofDescriptor(desc);
        for (int i = 0; i < mtd.parameterCount(); i++) {
            ClassDesc paramType = mtd.parameterType(i);
            
            // Print label
            builder.getstatic(CD_System, "out", CD_PrintStream)
                   .ldc((i > 0 ? ", arg" : "arg") + i + "=")
                   .invokevirtual(CD_PrintStream, "print", MTD_print_str);

            // Load and print value
            builder.getstatic(CD_System, "out", CD_PrintStream);
            if (paramType.isPrimitive()) {
                if (paramType.equals(CD_int)) {
                    builder.iload(slot).invokevirtual(CD_PrintStream, "print", MethodTypeDesc.of(CD_void, CD_int));
                    slot++;
                } else if (paramType.equals(CD_long)) {
                    builder.lload(slot).invokevirtual(CD_PrintStream, "print", MethodTypeDesc.of(CD_void, CD_long));
                    slot += 2;
                } else {
                    builder.ldc("<?>").invokevirtual(CD_PrintStream, "print", MTD_print_str);
                    slot++;
                }
            } else {
                builder.aload(slot).invokevirtual(CD_PrintStream, "print", MethodTypeDesc.of(CD_void, CD_Object));
                slot++;
            }
        }

        builder.getstatic(CD_System, "out", CD_PrintStream)
               .ldc(")")
               .invokevirtual(CD_PrintStream, "println", MTD_println);
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
