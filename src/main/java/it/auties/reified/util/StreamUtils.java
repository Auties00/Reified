package it.auties.reified.util;

import com.sun.tools.javac.util.List;

import java.util.Optional;
import java.util.stream.Stream;

public class StreamUtils {
    public static <T> Stream<T> onlyPresent(Stream<Optional<T>> stream){
        return stream.filter(Optional::isPresent).map(Optional::get);
    }
}
