package de.tim_greller.susserver.model.execution.security;

import java.util.List;

public class ClassLoadingFilter {

    private static final List<String> WHITELISTED_CLASSES = List.of(
            "java.lang.invoke.StringConcatFactory", // needed to concat variables with strings for logging

            // After sun.reflect.inflationThreshold (default 15) reflective calls, the JVM stops using the
            // native accessor and generates a bytecode one that extends these JDK-internal supertypes.
            // JUnit instantiates the test class once per test method, so any suite with more than 15 tests
            // trips this. Only the abstract supertypes are listed - test code cannot name them itself,
            // since javac rejects references to non-exported jdk.internal packages.
            "jdk.internal.reflect.MagicAccessorImpl",
            "jdk.internal.reflect.ConstructorAccessorImpl",
            "jdk.internal.reflect.MethodAccessorImpl"
    );

    private static final List<String> WHITELISTED_PACKAGES = List.of(
            "java.lang.",
            "java.util.",
            "java.math.",
            "java.text.",
            "java.time.",
            "org.junit.",

            "de.tim_greller.susserver.model.execution.instrumentation."
    );

    private static final List<String> BLACKLISTED_CLASSES = List.of(
            java.lang.System.class.getName(),
            java.lang.Thread.class.getName(),
            java.lang.ThreadGroup.class.getName(),
            java.util.TimerTask.class.getName(),
            java.lang.Runtime.class.getName(),
            java.lang.ClassLoader.class.getName()
    );

    private static final List<String> BLACKLISTED_PACKAGES = List.of(
            "java.lang.reflect.",
            "java.lang.instrument.",
            "java.lang.runtime.",
            "java.lang.management.",
            "java.lang.constant.",
            "java.lang.module.",
            "java.lang.ref.",

            "org.junit.runner.",
            "org.junit.runners.",
            "org.junit.experimental."
    );

    private boolean isIn(String className, List<String> list) {
        return list.stream().anyMatch(className::equals);
    }

    private boolean isInPackage(String className, List<String> list) {
        return list.stream().anyMatch(className::startsWith);
    }

    public boolean allowDelegateLoadingOf(String className) {
        boolean isAllowed = false;
        isAllowed |=  isInPackage(className, WHITELISTED_PACKAGES);
        isAllowed &= !isInPackage(className, BLACKLISTED_PACKAGES);
        isAllowed |=  isIn(className, WHITELISTED_CLASSES);
        isAllowed &= !isIn(className, BLACKLISTED_CLASSES);
        return isAllowed;
    }
}
