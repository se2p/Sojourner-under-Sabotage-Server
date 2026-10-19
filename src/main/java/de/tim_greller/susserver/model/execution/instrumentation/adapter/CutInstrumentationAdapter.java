package de.tim_greller.susserver.model.execution.instrumentation.adapter;

import static org.springframework.asm.Opcodes.ASM7;

import de.tim_greller.susserver.model.execution.instrumentation.Debug;
import de.tim_greller.susserver.model.execution.instrumentation.InstrumentationTracker;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

public class CutInstrumentationAdapter extends ClassVisitor {

    // Fields share the tracker's variable state with the locals, which are keyed "method/name".
    // Qualifying the field keeps a field and a same-named local apart, and reads like an IDE.
    private static String qualifiedFieldName(final int opcode, final String owner, final String name) {
        if (opcode == Opcodes.PUTFIELD) {
            return "this." + name;
        }
        final String simpleName = owner.substring(owner.lastIndexOf('/') + 1);
        final int userSuffix = simpleName.indexOf('#');
        return (userSuffix < 0 ? simpleName : simpleName.substring(0, userSuffix)) + "." + name;
    }

    private final String classId;

    public CutInstrumentationAdapter(final ClassWriter pClassWriter, final String pClassId) {
        super(ASM7, pClassWriter);
        classId = pClassId;
    }

    @Override
    public MethodVisitor visitMethod(
            final int pAccess,
            final String pMethodName,
            final String pDescriptor,
            final String pSignature,
            final String[] pExceptions) {
        final MethodVisitor mv = super.visitMethod(pAccess, pMethodName, pDescriptor, pSignature, pExceptions);
        //moved to super class due to duplicate code
        return new VarTrackingMethodVisitor(ASM7, mv, classId, pMethodName, pAccess, pDescriptor) {
            @Override
            public void visitLineNumber(final int pLine, final Label pStart) {
                InstrumentationTracker.trackLine(pLine, classId);
                super.visitLineNumber(pLine, pStart);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (owner.equals("java/io/PrintStream") && (name.equals("println") || name.equals("print"))) {
                    visitLdcInsn(classId);
                    visitLdcInsn(pMethodName);
                    String type = switch (descriptor) {
                        case "(Ljava/lang/String;)V" -> "Ljava/lang/String;";
                        case "(I)V", "(F)V", "(D)V", "(J)V", "(C)V", "(Z)V", "(B)V", "(S)V" -> descriptor.substring(1, 2);
                        case "([I)V", "([F)V", "([D)V", "([J)V", "([C)V", "([Z)V", "([B)V", "([S)V" -> descriptor.substring(1, 3);
                        case "()V" -> "";
                        default -> "Ljava/lang/Object;";
                    };
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(Debug.class),
                            "log",
                            "(" + type + "Ljava/lang/String;Ljava/lang/String;)V",
                            false);
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                if (opcode == Opcodes.GETSTATIC
                        && owner.equals("java/lang/System")
                        && name.equals("out")
                        && desc.equals("Ljava/io/PrintStream;")) {
                    return;
                }
                //necessary to track fields in classes
                //todo move to super class?
                if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                    boolean twoSlot = desc.equals("J") || desc.equals("D");
                    if (twoSlot) {
                        visitInsn(opcode == Opcodes.PUTFIELD ? Opcodes.DUP2_X1 : Opcodes.DUP2);
                    } else {
                        visitInsn(opcode == Opcodes.PUTFIELD ? Opcodes.DUP_X1 : Opcodes.DUP);
                    }
                    super.visitFieldInsn(opcode, owner, name, desc);
                    visitLdcInsn(qualifiedFieldName(opcode, owner, name));
                    visitLdcInsn(classId);
                    visitLdcInsn(pMethodName);
                    boolean isBool = desc.equals("Z");
                    String trackMethodName = isBool ? "trackFieldBool" : "trackField";
                    String typeSig = switch (desc) {
                        case "J" -> "J";
                        case "D" -> "D";
                        case "F" -> "F";
                        case "I", "Z", "B", "S", "C" -> "I";
                        default -> "Ljava/lang/Object;";
                    };
                    visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            Type.getInternalName(InstrumentationTracker.class),
                            trackMethodName,
                            "(" + typeSig + "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                            false);
                } else {
                    super.visitFieldInsn(opcode, owner, name, desc);
                }
            }
        };
    }
}
