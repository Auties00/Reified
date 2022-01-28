package it.auties.reified.util;

import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.DiagnosticHandler;
import lombok.*;
import lombok.experimental.Accessors;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

@Accessors(fluent = true)
public class DiagnosticHandlerWorker {
    private static final Set<String> DISCARDED_ERROR_CODES = Set.of(
            "compiler.err.generic.array.creation",
            "compiler.err.cant.resolve"
    );

    @Getter
    private final CachedDiagnosticHandler handler;
    private final Log javacLogger;
    private final Field javacDiagnosticHandlerField;
    private final DiagnosticHandler javacDiagnosticHandler;

    @SneakyThrows
    public DiagnosticHandlerWorker(Attr attr){
        this.handler = new CachedDiagnosticHandler();
        var loggerField = attr.getClass().getDeclaredField("log");
        this.javacLogger = (Log) IllegalReflection.open(loggerField)
                .get(attr);
        this.javacDiagnosticHandlerField = javacLogger.getClass()
                .getDeclaredField("diagnosticHandler");
        this.javacDiagnosticHandler = (DiagnosticHandler) IllegalReflection.open(javacDiagnosticHandlerField)
                .get(javacLogger);
    }

    public void useCachedHandler(){
      try {
          IllegalReflection.open(javacDiagnosticHandlerField)
                  .set(javacLogger, handler);
      }catch (Throwable throwable){
          throw new RuntimeException("Cannot switch to cached diagnostic handler", throwable);
      }
    }

    public void useJavacHandler(){
        try {
            IllegalReflection.open(javacDiagnosticHandlerField)
                    .set(javacLogger, javacDiagnosticHandler);
        }catch (Throwable throwable){
            throw new RuntimeException("Cannot switch to cached diagnostic handler", throwable);
        }
    }

    public void reportErrors(){
        handler.cachedErrors()
                .stream()
                .filter(diagnostic -> DISCARDED_ERROR_CODES.stream()
                        .noneMatch(code -> diagnostic.getCode().contains(code)))
                .forEach(javacDiagnosticHandler::report);
    }

    @AllArgsConstructor
    @Value
    @Accessors(fluent = true)
    @EqualsAndHashCode(callSuper = true)
    public static class CachedDiagnosticHandler extends DiagnosticHandler {
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
