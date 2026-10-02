package com.spaceconquest.frontend;

import java.util.function.Consumer;

/** Initializes callback-bearing views after their constructors have returned. */
public final class PostConstructionInitializer {
    private PostConstructionInitializer() {}

    public static <T> T initialize(T instance, Consumer<? super T> initializer) {
        initializer.accept(instance);
        return instance;
    }
}
