package de.tim_greller.susserver.model.execution.instrumentation.adapter;

import static org.springframework.asm.Opcodes.ASM7;

import de.tim_greller.susserver.model.execution.instrumentation.InstrumentationTracker;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

public class TestInstrumentationAdapter extends ClassVisitor {

    private final String testClassId;
    private final String cutClassId;

    public TestInstrumentationAdapter(final ClassWriter pClassWriter, final String pCutClassId, final String pTestClassId) {
        super(ASM7, pClassWriter);
        testClassId = pCutClassId;
        cutClassId = pTestClassId;
    }

    @Override
    public MethodVisitor visitMethod(
            final int pAccess,
            final String pMethodName,
            final String pDescriptor,
            final String pSignature,
            final String[] pExceptions) {
        final MethodVisitor mv = super.visitMethod(pAccess, pMethodName, pDescriptor, pSignature, pExceptions);

        return new VarTrackingMethodVisitor(ASM7, mv, testClassId, pMethodName, pAccess, pDescriptor) {
            @Override
            public void visitLineNumber(final int pLine, final Label pStart) {
                InstrumentationTracker.trackLine(pLine, testClassId);
                super.visitLineNumber(pLine, pStart);
            }

            @Override
            public void visitCode() {
                // track method entered
                visitLdcInsn(testClassId);
                visitLdcInsn(pMethodName);
                visitLdcInsn(cutClassId);
                visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(InstrumentationTracker.class),
                        "trackEnterTestMethod",
                        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                        false
                );

                super.visitCode();
            }
        };
    }
}