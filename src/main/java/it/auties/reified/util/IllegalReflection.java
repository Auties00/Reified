package it.auties.reified.util;

import sun.misc.Unsafe;

import java.io.OutputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.NoSuchElementException;

public class IllegalReflection {
    private static final Unsafe unsafe;
    private static final long offset;

    static {
        unsafe = getUnsafe();
        offset = findOffset();
    }

    public static void openJavac(){
        try {
            var jdkCompilerModule = findCompilerModule();
            var addOpensMethod = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
            var addOpensMethodOffset = unsafe.objectFieldOffset(ModulePlaceholder.class.getDeclaredField("first"));
            unsafe.putBooleanVolatile(addOpensMethod, addOpensMethodOffset, true);
            Arrays.stream(Package.getPackages())
                    .map(Package::getName)
                    .filter(pack -> pack.startsWith("com.sun.tools.javac"))
                    .forEach(pack -> invokeAccessibleMethod(addOpensMethod, jdkCompilerModule, pack, IllegalReflection.class.getModule()));
        }catch (Throwable throwable){
            throw new UnsupportedOperationException("Cannot open Javac Modules", throwable);
        }
    }

    private static Module findCompilerModule() {
        return ModuleLayer.boot()
                .findModule("jdk.compiler")
                .orElseThrow(() -> new ExceptionInInitializerError("Missing module: jdk.compiler"));
    }

    private static void invokeAccessibleMethod(Method method, Object caller, Object... arguments){
        try {
            method.invoke(caller, arguments);
        }catch (Throwable throwable){
            throw new RuntimeException("Cannot invoke accessible method", throwable);
        }
    }

    private static long findOffset() {
        try {
            var offsetField = AccessibleObject.class.getDeclaredField("override");
            return unsafe.objectFieldOffset(offsetField);
        }catch (Throwable throwable){
            return findOffsetFallback();
        }
    }

    private static long findOffsetFallback() {
        try {
            return unsafe.objectFieldOffset(AccessibleObjectPlaceholder.class.getDeclaredField("override"));
        }catch (Throwable innerThrowable){
            return -1;
        }
    }

    private static Unsafe getUnsafe() {
        try {
            var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        }catch (NoSuchFieldException exception){
            throw new NoSuchElementException("Cannot find unsafe field in wrapper class");
        }catch (IllegalAccessException exception){
            throw new UnsupportedOperationException("Access to the unsafe wrapper has been blocked", exception);
        }
    }

    public static <T extends AccessibleObject> T open(T object){
        if(offset != -1){
            unsafe.putBoolean(object, offset, true);
            return object;
        }

        object.setAccessible(true);
        return object;
    }

    @SuppressWarnings("all")
    private static class AccessibleObjectPlaceholder {
        boolean override;
        Object accessCheckCache;
    }

    @SuppressWarnings("all")
    public static class ModulePlaceholder {
        boolean first;
        static final Object staticObj = OutputStream.class;
        volatile Object second;
        private static volatile boolean staticSecond;
        private static volatile boolean staticThird;
    }
}
