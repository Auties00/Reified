package it.auties.reified.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE_PARAMETER})
@Retention(RetentionPolicy.SOURCE)
public @interface Reified {
    String PATH = "it.auties.reified.annotation.Reified";
}
