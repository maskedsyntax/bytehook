package org.bytehook.decompiler;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.util.*;

public class ByteHookDecompiler {

    private static final ClassFile CLASS_FILE = ClassFile.of();

    public String decompile(byte[] classBuffer, boolean showBytecode) {
        ClassModel classModel = CLASS_FILE.parse(classBuffer);
        StringBuilder sb = new StringBuilder();

        String className = classModel.thisClass().asInternalName().replace('/', '.');
        sb.append("public class ").append(className).append(" {\n\n");

        for (MethodModel method : classModel.methods()) {
            sb.append(decompileMethod(method, showBytecode)).append("\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String decompileMethod(MethodModel method, boolean showBytecode) {
        StringBuilder sb = new StringBuilder();
        String name = method.methodName().stringValue();
        String desc = method.methodType().stringValue();
        
        sb.append("  public ").append(formatSignature(name, desc)).append(" {\n");

        method.code().ifPresent(code -> {
            Stack<String> stack = new Stack<>();
            Map<Integer, String> locals = new HashMap<>();
            Map<Label, String> labelMap = new HashMap<>();
            int labelCounter = 1;

            // Pre-pass to find all labels used in branches
            for (CodeElement element : code) {
                if (element instanceof BranchInstruction branch) {
                    labelMap.putIfAbsent(branch.target(), "label_" + labelCounter++);
                }
            }

            for (CodeElement element : code) {
                if (element instanceof Label label && labelMap.containsKey(label)) {
                    sb.append("    ").append(labelMap.get(label)).append(":\n");
                }

                if (showBytecode && element instanceof Instruction instr) {
                    sb.append("    // ").append(instr.opcode().name()).append("\n");
                }

                if (element instanceof ConstantInstruction constant) {
                    stack.push(constant.constantValue().toString());
                } else if (element instanceof LoadInstruction load) {
                    stack.push(locals.getOrDefault(load.slot(), "var" + load.slot()));
                } else if (element instanceof StoreInstruction store) {
                    String val = stack.isEmpty() ? "???" : stack.pop();
                    locals.put(store.slot(), val);
                    sb.append("    var").append(store.slot()).append(" = ").append(val).append(";\n");
                } else if (element instanceof IncrementInstruction iinc) {
                    String current = locals.getOrDefault(iinc.slot(), "var" + iinc.slot());
                    locals.put(iinc.slot(), "(" + current + " + " + iinc.constant() + ")");
                    sb.append("    var").append(iinc.slot()).append("++;\n");
                } else if (element instanceof OperatorInstruction op) {
                    if (stack.size() >= 2) {
                        String b = stack.pop();
                        String a = stack.pop();
                        stack.push("(" + a + " " + getOpSymbol(op.opcode()) + " " + b + ")");
                    }
                } else if (element instanceof BranchInstruction branch) {
                    String target = labelMap.get(branch.target());
                    if (branch.opcode() == Opcode.GOTO || branch.opcode() == Opcode.GOTO_W) {
                        sb.append("    goto ").append(target).append(";\n");
                    } else {
                        // Conditional branch
                        String b = isZeroBranch(branch.opcode()) ? "0" : (stack.isEmpty() ? "0" : stack.pop());
                        String a = stack.isEmpty() ? "0" : stack.pop();
                        sb.append("    if (").append(a).append(" ").append(getBranchSymbol(branch.opcode())).append(" ").append(b).append(") goto ").append(target).append(";\n");
                    }
                } else if (element instanceof InvokeInstruction invoke) {
                    int argCount = getArgCount(invoke.typeSymbol().descriptorString());
                    List<String> args = new ArrayList<>();
                    for (int i = 0; i < argCount && !stack.isEmpty(); i++) {
                        args.add(0, stack.pop());
                    }
                    String argsStr = String.join(", ", args);
                    String owner = invoke.owner().asInternalName().replace('/', '.');
                    String mName = invoke.name().stringValue();
                    
                    if (mName.equals("println")) {
                        sb.append("    System.out.println(").append(argsStr).append(");\n");
                    } else {
                        String call = owner + "." + mName + "(" + argsStr + ")";
                        if (invoke.typeSymbol().descriptorString().endsWith("V")) {
                            sb.append("    ").append(call).append(";\n");
                        } else {
                            stack.push(call);
                        }
                    }
                } else if (element instanceof ReturnInstruction ret) {
                    if (ret.opcode() == Opcode.RETURN) {
                        sb.append("    return;\n");
                    } else {
                        String val = stack.isEmpty() ? "" : stack.pop();
                        sb.append("    return ").append(val).append(";\n");
                    }
                }
            }
        });

        sb.append("  }\n");
        return sb.toString();
    }

    private boolean isZeroBranch(Opcode op) {
        return switch (op) {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> true;
            default -> false;
        };
    }

    private String getBranchSymbol(Opcode op) {
        return switch (op) {
            case IF_ICMPEQ, IF_ACMPEQ, IFEQ -> "==";
            case IF_ICMPNE, IF_ACMPNE, IFNE -> "!=";
            case IF_ICMPLT, IFLT -> "<";
            case IF_ICMPGE, IFGE -> ">=";
            case IF_ICMPGT, IFGT -> ">";
            case IF_ICMPLE, IFLE -> "<=";
            default -> "??";
        };
    }

    private String formatSignature(String name, String desc) {
        int paramEnd = desc.indexOf(')');
        String params = desc.substring(1, paramEnd);
        String ret = desc.substring(paramEnd + 1);
        
        List<String> paramList = new ArrayList<>();
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == 'I') paramList.add("int");
            else if (c == 'L' && params.startsWith("java/lang/String", i)) {
                paramList.add("String[]");
                break;
            }
        }
        
        String returnType = ret.equals("V") ? "void" : (ret.equals("I") ? "int" : ret);
        return returnType + " " + name + "(" + String.join(", ", paramList) + ")";
    }

    private int getArgCount(String desc) {
        int count = 0;
        int paramEnd = desc.indexOf(')');
        for (int i = 1; i < paramEnd; i++) {
            if (desc.charAt(i) == 'L') {
                while (desc.charAt(i) != ';') i++;
                count++;
            } else if (desc.charAt(i) == '[') {
                continue;
            } else {
                count++;
            }
        }
        return count;
    }

    private String getOpSymbol(Opcode op) {
        return switch (op) {
            case IADD, LADD, FADD, DADD -> "+";
            case ISUB, LSUB, FSUB, DSUB -> "-";
            case IMUL, LMUL, FMUL, DMUL -> "*";
            case IDIV, LDIV, FDIV, DDIV -> "/";
            default -> "?";
        };
    }
}
