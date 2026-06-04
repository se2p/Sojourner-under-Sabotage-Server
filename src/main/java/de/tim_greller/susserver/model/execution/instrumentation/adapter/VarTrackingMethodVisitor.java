package de.tim_greller.susserver.model.execution.instrumentation.adapter;

import de.tim_greller.susserver.model.execution.instrumentation.InstrumentationTracker;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

abstract class VarTrackingMethodVisitor extends MethodVisitor {

    private final String classId;
    private final String methodName;

    protected VarTrackingMethodVisitor(int api, MethodVisitor delegate, String classId, String methodName) {
        super(api, delegate);
        this.classId = classId;
        this.methodName = methodName;
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        super.visitVarInsn(opcode, varIndex);

        if (opcode >= 54 && opcode <= 58) {
            String descriptor = switch (opcode) {
                case Opcodes.ISTORE -> "I";
                case Opcodes.FSTORE -> "F";
                case Opcodes.DSTORE -> "D";
                case Opcodes.LSTORE -> "J";
                default -> "Ljava/lang/Object;";
            };
            visitVarInsn(opcode - 33, varIndex);
            visitLdcInsn(varIndex);
            visitLdcInsn(classId);
            visitLdcInsn(methodName);
            visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(InstrumentationTracker.class),
                    "trackVar",
                    "(" + descriptor + "ILjava/lang/String;Ljava/lang/String;)V",
                    false);
        }
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        super.visitIincInsn(varIndex, increment);
        visitVarInsn(Opcodes.ILOAD, varIndex);
        visitLdcInsn(varIndex);
        visitLdcInsn(classId);
        visitLdcInsn(methodName);
        visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(InstrumentationTracker.class),
                "trackVar",
                "(IILjava/lang/String;Ljava/lang/String;)V",
                false);
    }

    @Override
    public void visitLineNumber(int pLine, Label pStart) {
        super.visitLineNumber(pLine, pStart);
        visitLdcInsn(pLine);
        visitLdcInsn(classId);
        visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(InstrumentationTracker.class),
                "trackLineVisit",
                "(ILjava/lang/String;)V",
                false);
        visitLdcInsn(pLine);
        visitLdcInsn(classId);
        visitLdcInsn(methodName);
        visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(InstrumentationTracker.class),
                "trackDebugStep",
                "(ILjava/lang/String;Ljava/lang/String;)V",
                false);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature,
                                   Label start, Label end, int index) {
        super.visitLocalVariable(name, descriptor, signature, start, end, index);
        InstrumentationTracker.trackVarDef(index, name, descriptor, classId, methodName);
    }
}