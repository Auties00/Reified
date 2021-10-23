package it.auties.reified.simplified;

import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import it.auties.reified.util.IllegalReflection;
import lombok.experimental.UtilityClass;

import javax.annotation.processing.ProcessingEnvironment;
import java.lang.reflect.Proxy;
import java.util.Optional;

@UtilityClass
public class SimpleContext {
    private final IllegalReflection REFLECTION = IllegalReflection.singleton();
    private final String JAVAC_ENV = "com.sun.tools.javac.processing.JavacProcessingEnvironment";
    private final String GRADLE_WRAPPED_FIELD = "delegate";

    public Context resolveContext(ProcessingEnvironment environment) {
        var envClass = environment.getClass();
        if (envClass.getName().equals(JAVAC_ENV)) {
            return resolveJavacContext(environment, envClass);
        }

        if (Proxy.isProxyClass(envClass)) {
            return resolveIntelliJContext(environment);
        }

        return resolveGradleEnvironment(environment);
    }

    private Context resolveJavacContext(ProcessingEnvironment environment, Class<? extends ProcessingEnvironment> envClass) {
        try {
            return (Context) REFLECTION.open(envClass.getDeclaredMethod("getContext")).invoke(environment);
        }catch (Throwable exception){
            throw new UnsupportedOperationException("Cannot resolve javac context", exception);
        }
    }

    private Context resolveIntelliJContext(ProcessingEnvironment environment) {
        try {
            var getContext = JavacProcessingEnvironment.class.getMethod("getContext");
            return (Context) Proxy.getInvocationHandler(environment)
                    .invoke(environment, getContext, new Object[0]);
        }catch (Throwable exception){
            throw new UnsupportedOperationException("Cannot resolve intellij context", exception);
        }
    }

    private Context resolveGradleEnvironment(ProcessingEnvironment environment) {
        return resolveFieldRecursively(environment.getClass(), GRADLE_WRAPPED_FIELD, environment)
                .orElseThrow(() -> new UnsupportedOperationException("Unsupported environment!"))
                .getContext();
    }

    private Optional<JavacProcessingEnvironment> resolveFieldRecursively(Class<?> clazz, String fieldName, Object handler) {
        if (clazz == null) {
            return Optional.empty();
        }

        try {
            return Optional.of((JavacProcessingEnvironment) REFLECTION.open(clazz.getDeclaredField(fieldName)).get(handler));
        } catch (NoSuchFieldException ignored) {
            return resolveFieldRecursively(clazz.getSuperclass(), fieldName, handler);
        } catch (IllegalAccessException exception) {
            throw new UnsupportedOperationException("Cannot find environment: access exception", exception);
        }
    }
}
