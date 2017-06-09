package io.booter.injector.core;

import io.booter.injector.Binding;
import io.booter.injector.Key;

import java.util.function.Supplier;

/**
 * Set of convenience methods for creation of instances of {@link Binding} for various situations.
 */
public final class Bindings {
    private Bindings() {}

    /**
     * Create instance of {@link Binding} for specific implementation class.
     *
     * @param key
     *          {@link Key} to which implementation class should be bound.
     * @param implementation
     *          Implementation class.
     * @param singleton
     *          If <code>true</code> then singleton binding will be created.
     * @param eager
     *          if singleton should be created then this parameter controls type of the singleton. If <code>true</code>
     *          then eager singleton is bound and lazy singleton otherwise.
     * @return Created instance of {@link Binding}
     */
    public static Binding<Class<?>> of(Key key, Class<?> implementation, boolean singleton, boolean eager) {
        return new BindingImpl<>(key, implementation, false, singleton, eager);
    }

    /**
     * Create binding for provided instance.
     *
     * @param key
     *         {@link Key} to which instance should be bound.
     * @param instance
     *         Instance which should be bound.
     *
     * @return Created instance of {@link Binding}
     */
    public static<T> Binding<Supplier<T>> of(Key key, T instance) {
        return new BindingImpl<>(key, () -> instance, true, true, true);
    }

    /**
     * Create binding for provided supplier.
     *
     * @param key
     *          {@link Key} to which supplier should be bound.
     * @param supplier
     *          Supplier which should be bound.
     *
     * @return Created instance of {@link Binding}
     */
    public static<T> Binding<Supplier<T>> of(Key key, Supplier<T> supplier) {
        return new BindingImpl<>(key, supplier, true, true, true);
    }

    private static class BindingImpl<T> implements Binding<T> {
        private final Key key;
        private final T binding;
        private final boolean resolved;
        private final boolean singleton;
        private final boolean eager;

        public BindingImpl(Key key, T binding, boolean resolved, boolean singleton, boolean eager) {
            this.key = key;
            this.binding = binding;
            this.resolved = resolved;
            this.singleton = singleton;
            this.eager = eager;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public T binding() {
            return binding;
        }

        @Override
        public boolean isResolved() {
            return resolved;
        }

        @Override
        public boolean isSingleton() {
            return singleton;
        }

        @Override
        public boolean isEager() {
            return eager;
        }
    }
}
