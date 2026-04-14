package org.bytehook.core.instrument;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.function.Consumer;

import static java.lang.constant.ConstantDescs.*;

public class ByteHookTransformer {

    private static final ClassFile CLASS_FILE = ClassFile.of();
    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");
    private static final ClassDesc CD_PrintStream = ClassDesc.of("java.io.PrintStream");

    public byte[] transform(byte[] classBuffer, String message) {
        ClassModel classModel = CLASS_FILE.parse(classBuffer);

        return CLASS_FILE.transformClass(classModel, (classBuilder, classElement) -> {
            if (classElement instanceof MethodModel method) {
                // Skip <init> for simple logging, or we might log before super()
                String name = method.methodName().stringValue();
                if (name.equals("<init>") || name.equals("<clinit>")) {
                    classBuilder.with(method);
                    return;
                }

                classBuilder.transformMethod(method, (methodBuilder, methodElement) -> {
                    if (methodElement instanceof CodeModel codeModel) {
                        methodBuilder.withCode(codeBuilder -> {
                            injectLogging(codeBuilder, message + " [" + name + "]");
                            for (CodeElement ce : codeModel) {
                                codeBuilder.with(ce);
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
               .invokevirtual(CD_PrintStream, "println", MethodTypeDesc.of(CD_void, CD_String));
    }
}
