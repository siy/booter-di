package io.booter.injector.core.supplier;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;

import io.booter.injector.annotations.ComputationStyle;
import io.booter.injector.annotations.Singleton;
import io.booter.injector.core.SupplierFactory;

public class DefaultSupplierFactory implements SupplierFactory {
    public DefaultSupplierFactory() {
    }

    @Override
    public <T> Supplier<T> create(Constructor<T> constructor, Supplier<?>[] parameters) {
        Singleton singleton = constructor.getDeclaringClass().getAnnotation(Singleton.class);
        Supplier<T> instanceSupplier = wrapWithPostConstruct(constructor, LambdaFactory.create(constructor, parameters));

        return wrap(singleton, instanceSupplier);
    }

    @Override
    public <T> Supplier<T> createSingleton(Constructor<T> constructor, Supplier<?>[] parameters, boolean eager) {
        Supplier<T> instanceSupplier = wrapWithPostConstruct(constructor, LambdaFactory.create(constructor, parameters));

        return buildSingletonSupplier(instanceSupplier, eager);
    }

    private <T> Supplier<T> wrapWithPostConstruct(Constructor<T> constructor, Supplier<T> instanceSupplier) {
        MethodHandle methodHandle = locatePostConstructMethod(constructor.getDeclaringClass());

        if (methodHandle != null) {
            return new PostConstructInvokingSupplier(instanceSupplier, methodHandle);
        }
        return instanceSupplier;
    }

    private <T> MethodHandle locatePostConstructMethod(Class<T> declaringClass) {

        for (Method method: declaringClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                return LambdaFactory.create(method);
            }
        }
        return null;
    }

    private <T> Supplier<T> wrap(Singleton singleton, Supplier<T> instanceSupplier) {
        if (singleton == null) {
            return instanceSupplier;
        }

        return buildSingletonSupplier(instanceSupplier, singleton.value() == ComputationStyle.EAGER);
    }

    private <T> Supplier<T> buildSingletonSupplier(Supplier<T> instanceSupplier, boolean isEager) {
        if (isEager) {
            T instance = instanceSupplier.get();
            return () -> instance;
        } else {
            return new InternalLazySupplier<>(instanceSupplier);
        }
    }

    private static class InternalInstanceSupplier<T> implements Supplier<T> {
        private final T instance;

        InternalInstanceSupplier(T instance) {
            this.instance = instance;
        }

        @Override
        public T get() {
            return instance;
        }
    }

    private static class InternalLazySupplier<T> implements Supplier<T> {
        private volatile Supplier<T> instanceSupplier = () -> lazyInstanceCreator();
        private final Supplier<T> instanceCreator;

        private synchronized T lazyInstanceCreator() {
            if (instanceSupplier instanceof InternalInstanceSupplier) {
                return instanceSupplier.get();
            }
            return (instanceSupplier = new InternalInstanceSupplier<>(instanceCreator.get())).get();
        }

        InternalLazySupplier(Supplier<T> instanceCreator) {
            this.instanceCreator = instanceCreator;
        }

        @Override
        public T get() {
            return instanceSupplier.get();
        }
    }
}
