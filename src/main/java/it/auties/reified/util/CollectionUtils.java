package it.auties.reified.util;

import com.sun.tools.javac.util.List;

import java.util.Optional;

public class CollectionUtils {
    public static <T> Optional<T> getSafe(List<T> list, int index) {
        return Optional.ofNullable(list)
                .filter(safe -> safe.size() - 1 >= index)
                .map(safe -> safe.get(index));
    }
}
