open module it.auties.reified {
    requires static com.google.auto.service;
    requires static lombok;
    requires java.compiler;
    requires jdk.compiler;

    exports it.auties.reified.annotation;
    exports it.auties.reified.model;
    exports it.auties.reified.processor;
}