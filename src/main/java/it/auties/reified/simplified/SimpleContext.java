package it.auties.reified.simplified;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import lombok.experimental.UtilityClass;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;

@UtilityClass
public class SimpleContext {
    private final String JAVAC_ENV = "com.sun.tools.javac.processing.JavacProcessingEnvironment";

    public Context resolveContext(ProcessingEnvironment environment) {
        var envClass = environment.getClass();
        if (envClass.getName().equals(JAVAC_ENV)) {
            return ((JavacProcessingEnvironment) environment).getContext();
        }

        if (Proxy.isProxyClass(envClass)) {
            return resolveIntelliJContext(environment);
        }

        return resolveGradleContext(environment);
    }

    private Context resolveIntelliJContext(ProcessingEnvironment environment) {
        var handler = Proxy.getInvocationHandler(environment);
        return resolveIntelliJEnvironment(handler.getClass(), handler)
                .orElseThrow(() -> new UnsupportedOperationException("Cannot find environment in intellij: missing field"))
                .getContext();
    }

    private Optional<JavacProcessingEnvironment> resolveIntelliJEnvironment(Class<?> clazz, InvocationHandler handler) {
        return resolveFieldRecursively(clazz, "val$delegateTo", handler);
    }

    private Context resolveGradleContext(ProcessingEnvironment environment) {
        return resolveGradleEnvironment(environment)
                .orElseThrow(() -> new UnsupportedOperationException("Reified annotation processing failed: unsupported environment!"))
                .getContext();
    }

    private Optional<JavacProcessingEnvironment> resolveGradleEnvironment(ProcessingEnvironment environment) {
        return resolveFieldRecursively(environment.getClass(), "delegate", environment);
    }

    private Optional<JavacProcessingEnvironment> resolveFieldRecursively(Class<?> clazz, String fieldName, Object handler) {
        if (clazz == null) {
            return Optional.empty();
        }

        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return Optional.of((JavacProcessingEnvironment) field.get(handler));
        } catch (NoSuchFieldException ignored) {
            return resolveFieldRecursively(clazz.getSuperclass(), fieldName, handler);
        } catch (IllegalAccessException exception) {
            throw new UnsupportedOperationException("Cannot find environment: access exception", exception);
        }
    }
}
