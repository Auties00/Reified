package it.auties.reified.util;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.util.NoSuchElementException;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IllegalReflection {
    private static final String UNSAFE_FIELD_NAME = "theUnsafe";
    private final Unsafe unsafe;
    private final long offset;

    public IllegalReflection(){
        this.unsafe = getUnsafe();
        this.offset = findOffset();
        System.err.printf("IllegalReflection detected override offset: %s%n", offset);
    }

    private long findOffset() {
        try {
            var offsetField = AccessibleObject.class.getDeclaredField("override");
            return unsafe.objectFieldOffset(offsetField);
        }catch (Throwable throwable){
            throwable.printStackTrace();
            return findOffsetFallback();
        }
    }

    private long findOffsetFallback() {
        try {
            return unsafe.objectFieldOffset(Placeholder.class.getDeclaredField("override"));
        }catch (Throwable innerThrowable){
            System.err.printf("IllegalReflection final throwable %s%n", innerThrowable.getMessage());
            return -1;
        }
    }

    private Unsafe getUnsafe() {
        try {
            var unsafeField = Unsafe.class.getDeclaredField(UNSAFE_FIELD_NAME);
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        }catch (NoSuchFieldException exception){
            throw new NoSuchElementException(String.format("Cannot find %s in %s", UNSAFE_FIELD_NAME, Unsafe.class.getName()));
        }catch (IllegalAccessException exception){
            throw new UnsupportedOperationException(String.format("Access to %s has been blocked: the day has come. In this future has the OpenJDK team created a publicly available compiler api that can do something? Probably not", Unsafe.class.getName()), exception);
        }
    }

    public <T extends AccessibleObject> T open(T object){
        if(offset != -1){
            unsafe.putBoolean(object, offset, true);
            return object;
        }

        object.setAccessible(true);
        return object;
    }

    @SuppressWarnings("unused")
    private static class Placeholder {
        boolean override;
        Object accessCheckCache;
    }
}
