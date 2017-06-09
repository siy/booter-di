package io.booter.injector.core.supplier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static io.booter.injector.core.supplier.Utils.validateNotNull;

/**
 * General purpose convenience methods for transforming suppliers into suppliers with specific behavior.
 */
public final class Suppliers {
    private Suppliers() {
    }

    /**
     * This method transforms input supplier which creates instances of other suppliers into lazily instantiating
     * supplier. In other words, input factory which creates instances of suppliers will be invoked only once and only
     * when first call to created supplier will be made. Note that after creation of supplier, resulting supplier
     * becomes very thin wrapper around created supplier and is subject of HotSpot optimizations during
     * further calls.
     *
     * @param factory
     *          Factory supplier which provides instances of supplier of specified type. Invoked only once.
     * @return Lazily instantiating supplier.
     */
    public static <T> Supplier<T> factoryLazy(final Supplier<Supplier<T>> factory) {
        validateNotNull(factory);

        return new Supplier<T>() {
            private final Supplier<T> defaultDelegate = this::init;
            private final AtomicBoolean marker = new AtomicBoolean();
            private Supplier<T> delegate = defaultDelegate;

            private T init() {
                if (marker.compareAndSet(false, true)) {
                    delegate = factory.get();
                } else {
                    while (delegate == defaultDelegate) {
                        //Intentionally left empty
                    }
                }
                return delegate.get();
            }

            public T get() {
                return delegate.get();
            }
        };
    }

    /**
     * Convenience method for the creation of singleton suppliers from source instance suppliers.
     *
     * @param factory
     *          Source supplier. Invoked only once.
     * @return Created singleton supplier.
     */
    public static <T> Supplier<T> lazy(final Supplier<T> factory) {
        validateNotNull(factory);
        return factoryLazy(() -> {T instance = factory.get(); return () -> instance;});
    }

    /**
     * Convenience method for creating lazy or eager singletons using source instance supplier.
     *
     * @param factory
     *          Source supplier. Invoked only once.
     * @param eager
     *          If <code>true</code> instance will be created eagerly. Otherwise lazy singleton will be created.
     * @return Created singleton supplier.
     */
    public static <T> Supplier<T> singleton(final Supplier<T> factory, boolean eager) {
        validateNotNull(factory);

        if (eager) {
            T instance = factory.get();
            return () -> instance;
        }
        return lazy(factory);
    }

    /**
     * This method creates instance of lazily enhancing supplier. For the first few calls used initial supplier, which
     * might be slow, but acceptable for infrequent invocation. Once limit is reached, supplier is replaced with
     * another supplier created by supplier factory. The creation of the enhanced supplier may be slow, but resulting
     * supplier should be faster at run time. Once enhanced supplier is created, supplier created by this method
     * becomes very thin wrapper around enhanced supplier and is subject of HotSpot optimizations.
     * <br />
     * This implementation has hardcoded invocation limit (3 invocations) after which enhanced supplier will be created.
     *
     * @param initial
     *          Initial supplier which is used for initial invocations.
     * @param enhanced
     *          Factory which creates enhanced supplier. Invoked only once.
     * @return Lazily enhancing supplier
     */
    public static <T> Supplier<T> enhancing(final Supplier<T> initial, Supplier<Supplier<T>> enhanced) {
        validateNotNull(initial, enhanced);

        return new Supplier<T>() {
            private Supplier<T> delegate = () -> step(() -> () -> step(() -> () -> step(enhanced)));

            private T step(Supplier<Supplier<T>> next) {
                T instance = initial.get();
                delegate = next.get();
                return instance;
            }

            @Override
            public T get() {
                return delegate.get();
            }
        };
    }
}