package de.tim_greller.susserver.model.execution.instrumentation.adapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.tim_greller.susserver.model.execution.instrumentation.InstrumentationTracker;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;

// Shared base visitor that injects InstrumentationTracker calls
// (extracted from the cut/test adapters to avoid duplication)
abstract class VarTrackingMethodVisitor extends MethodVisitor {

    private final String classId;
    private final String methodName;
    private final int access;
    private final String descriptor;
    private boolean parametersTracked;
    private final Map<Label, Integer> labelPositions = new HashMap<>();
    private final List<Integer> stepSites = new ArrayList<>();
    private final List<Scope> scopes = new ArrayList<>();

    protected VarTrackingMethodVisitor(int api, MethodVisitor delegate, String classId, String methodName,
                                       int access, String descriptor) {
        super(api, delegate);
        this.classId = classId;
        this.methodName = methodName;
        this.access = access;
        this.descriptor = descriptor;
    }

    // The tracker only learns a value from the *STORE that writes it, but parameters arrive
    // already in their slots and are never stored. Without this they stay invisible for their
    // whole method, and the debugger shows an empty frame while stepping through a callee.
    private void trackParameters() {
        int slot = (access & Opcodes.ACC_STATIC) == 0 ? 1 : 0; // slot 0 is "this"; fields are reported separately
        for (Type argument : Type.getArgumentTypes(descriptor)) {
            super.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot);
            visitLdcInsn(slot);
            visitLdcInsn(classId);
            visitLdcInsn(methodName);
            visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(InstrumentationTracker.class),
                    "trackVar",
                    "(" + trackVarSignature(argument) + "ILjava/lang/String;Ljava/lang/String;)V",
                    false);
            slot += argument.getSize();
        }
    }

    // Mirrors the *STORE mapping: everything the JVM holds in an int slot is reported as an int
    private static String trackVarSignature(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> "I";
            case Type.FLOAT -> "F";
            case Type.LONG -> "J";
            case Type.DOUBLE -> "D";
            default -> "Ljava/lang/Object;";
        };
    }

    // On every variable STORE, reload the stored value and report it to trackVar
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

    // IINC bypasses visitVarInsn, so report the incremented int separately
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

    // At each line: record the line visit and create a debug step
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
        // Report the parameters once the first line of the method has been visited: the tracker
        // files values under the line last visited, which until now was still the caller's.
        if (!parametersTracked) {
            parametersTracked = true;
            trackParameters();
        }
        final int siteId = stepSites.size();
        stepSites.add(positionOf(pStart));
        visitLdcInsn(pLine);
        visitLdcInsn(classId);
        visitLdcInsn(methodName);
        visitLdcInsn(siteId);
        visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(InstrumentationTracker.class),
                "trackDebugStep",
                "(ILjava/lang/String;Ljava/lang/String;I)V",
                false);
    }

    // Labels are visited in code order, so their visit order is a usable stand-in for a bytecode
    // offset: it is all the scope ranges below need in order to be compared against a step site.
    @Override
    public void visitLabel(Label label) {
        super.visitLabel(label);
        labelPositions.putIfAbsent(label, labelPositions.size());
    }

    private int positionOf(Label label) {
        return labelPositions.getOrDefault(label, Integer.MAX_VALUE);
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature,
                                   Label start, Label end, int index) {
        super.visitLocalVariable(name, descriptor, signature, start, end, index);
        // "this" is not a local the player wrote; its fields are reported separately as "this.x"
        if (!"this".equals(name)) {
            scopes.add(new Scope(index, name, positionOf(start), positionOf(end)));
        }
    }

    // The local variable table only arrives after the code, so a name cannot be resolved while the
    // instructions are visited. Resolve it here instead, per step site, exactly like a debugger
    // does: a slot carries the name of the scope covering that site. This keeps two variables that
    // share a slot apart (sibling blocks reuse slots), and hides the compiler's synthetic slots,
    // which have no scope at all and would otherwise borrow an unrelated variable's name.
    @Override
    public void visitEnd() {
        for (int siteId = 0; siteId < stepSites.size(); siteId++) {
            final int sitePosition = stepSites.get(siteId);
            final Map<Integer, String> visibleNames = new TreeMap<>();
            for (Scope scope : scopes) {
                if (scope.start <= sitePosition && sitePosition < scope.end) {
                    visibleNames.put(scope.slot, scope.name);
                }
            }
            InstrumentationTracker.registerStepSite(classId, methodName, siteId, visibleNames);
        }
        super.visitEnd();
    }

    private record Scope(int slot, String name, int start, int end) {}
}