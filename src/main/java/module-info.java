open module it.auties.reified {
    requires java.compiler;
    requires jdk.compiler;
    requires com.google.auto.service;
    requires lombok;
    requires org.objectweb.asm;

    exports it.auties.reified.annotation;
    exports it.auties.reified.model;
    exports it.auties.reified.processor;
}