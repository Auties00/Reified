package it.auties.reified.util;

import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.Accessors;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class DiagnosticHandlerWorker {
    private static final IllegalReflection REFLECTION = IllegalReflection.singleton();
    private static final Set<String> DISCARDED_ERROR_CODES = Set.of("compiler.err.generic.array.creation");
    private static final String ATTR_LOG_FIELD = "log";
    private static final String LOG_HANDLER_FIELD = "diagnosticHandler";

    private final CachedDiagnosticHandler cachedDiagnosticHandler;
    private final Log javacLogger;
    private final Field javacDiagnosticHandlerField;
    private final Log.DiagnosticHandler javacDiagnosticHandler;

    public DiagnosticHandlerWorker(Attr attr){
        try {
            this.cachedDiagnosticHandler = new CachedDiagnosticHandler();
            var loggerField = attr.getClass().getDeclaredField(ATTR_LOG_FIELD);
            this.javacLogger = (Log) REFLECTION.open(loggerField).get(attr);
            this.javacDiagnosticHandlerField = javacLogger.getClass().getDeclaredField(LOG_HANDLER_FIELD);
            this.javacDiagnosticHandler = (Log.DiagnosticHandler) REFLECTION.open(javacDiagnosticHandlerField).get(javacLogger);
        }catch (Throwable throwable){
            throw new RuntimeException("Cannot create DiagnosticHandlerWorker", throwable);
        }
    }

    public void useCachedHandler(){
      try {
          REFLECTION.open(javacDiagnosticHandlerField).set(javacLogger, cachedDiagnosticHandler);
      }catch (Throwable throwable){
          throw new RuntimeException("Cannot switch to cached diagnostic handler", throwable);
      }
    }

    public void useJavacHandler(){
        try {
            REFLECTION.open(javacDiagnosticHandlerField).set(javacLogger, javacDiagnosticHandler);
        }catch (Throwable throwable){
            throw new RuntimeException("Cannot switch to cached diagnostic handler", throwable);
        }
    }

    public void reportErrors(){
        cachedDiagnosticHandler.cachedErrors()
                .stream()
                .filter(diagnostic -> !DISCARDED_ERROR_CODES.contains(diagnostic.getCode()))
                .forEach(javacDiagnosticHandler::report);
    }

    @AllArgsConstructor
    @Value
    @Accessors(fluent = true)
    @EqualsAndHashCode(callSuper = true)
    public static class CachedDiagnosticHandler extends Log.DiagnosticHandler{
        Queue<JCDiagnostic> cachedErrors;
        public CachedDiagnosticHandler(){
            this(new LinkedList<>());
        }

        @Override
        public void report(JCDiagnostic diagnostic) {
            if(diagnostic == null){
                return;
            }

            cachedErrors.add(diagnostic);
        }
    }
}
