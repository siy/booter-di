package io.booter.injector.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import io.booter.injector.Binding;
import io.booter.injector.Injector;
import io.booter.injector.Key;
import io.booter.injector.Module;
import io.booter.injector.annotations.ConfiguredBy;
import io.booter.injector.annotations.Supplies;
import io.booter.injector.core.exception.InjectorException;
import io.booter.injector.core.supplier.LambdaFactory;
import io.booter.injector.core.supplier.Utils;

import static io.booter.injector.core.supplier.SupplierFactory.*;
import static io.booter.injector.core.supplier.Suppliers.factoryLazy;

/**
 * Implementation of the {@link Injector}.
 * <p>
 * This implementation uses lazy evaluation and batching to achieve optimal performance in different use cases.
 */
public class ScanningInjector implements Injector {
    private final ConcurrentMap<Key, Supplier<?>> bindings = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Class<?>> modules = new ConcurrentHashMap<>();

    /**
     * Create instance of injector.
     */
    public ScanningInjector() {
        bindings.put(Key.of(Injector.class), () -> this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T get(Class<T> clazz) {
        return supplier(clazz).get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Key key) {
        return (T) supplier(key).get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Supplier<T> supplier(Class<T> clazz) {
        return supplier(Key.of(clazz));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> supplier(Key key) {
        Supplier<T> supplier = (Supplier<T>) bindings.get(key);

        if (supplier != null) {
            return supplier;
        }

        synchronized (bindings) {
            supplier = (Supplier<T>) bindings.get(key);

            if (supplier != null) {
                return supplier;
            }

            buildTree(key, new ChainedMap<>()).forEach(this::bindNode);

            return (Supplier<T>) bindings.get(key);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Injector configure(Class<?>... configurators) {
        if (configurators == null) {
            throw new InjectorException("Null class is passed to runTimeConfigure()");
        }

        for (Class<?> clazz : configurators) {
            if (clazz == null) {
                throw new InjectorException("Null class is passed to runTimeConfigure()");
            }

            modules.computeIfAbsent(clazz, (cls) -> configureSingle(clazz));
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private Class<?> configureSingle(Class<?> clazz) {
        Node node = resolveConfig(clazz);

        if (!Module.class.isAssignableFrom(node.key().rawClass())) {
            return clazz;
        }

        Supplier<Module> configSupplier = (Supplier<Module>) bindings.get(node.key());

        configSupplier.get().collectBindings().forEach(this::bindImplementations);
        return clazz;
    }

    private void bindImplementations(Binding<?> binding) {
        Key key = binding.key();

        Supplier<?> supplier = binding.isResolved() ? (Supplier<?>) binding.binding()
                                                    : buildSupplier(binding, bindDependencies(key, (Class<?>) binding.binding()));

        bindings.putIfAbsent(key, supplier);
    }

    private Supplier<?> buildSupplier(Binding<?> binding, Node node) {
        List<Supplier<?>> parameters = new ArrayList<>();

        node.dependencies().forEach(key -> parameters.add(key.isSupplier() ? () -> bindings.get(key) : bindings.get(key)));

        return binding.isSingleton()
               ? createSingletonSupplier(node.constructor(), parameters, binding.isEager())
               : createInstanceSupplier(node.constructor(), parameters);
    }

    private Node bindDependencies(Key key, Class<?> clazz) {
        Node node = buildNoConfigNode(key, clazz);
        ChainedMap<Key, Node> nodes = new ChainedMap<>();

        node.dependencies().forEach((k) -> buildTree(k, nodes));
        nodes.forEach(this::bindNode);

        return node;
    }

    private Node resolveConfig(Class<?> clazz) {
        ChainedMap<Key, Node> nodes = new ChainedMap<>();
        Node node = buildConfigNode(clazz, nodes);

        nodes.put(node.key(), node).dependencies().forEach((k) -> buildTree(k, new ChainedMap<>(nodes)));
        nodes.forEach(this::bindNode);

        return node;
    }

    private void bindNode(Key key, Node node) {
        bindings.computeIfAbsent(key, (k) -> factoryLazy(() -> buildSupplier(node)));
    }

    private Supplier<?> buildSupplier(Node node) {
        List<Supplier<?>> parameters = new ArrayList<>();

        node.dependencies()
            .forEach(key -> parameters.add(key.isSupplier() ? () -> bindings.get(key) : bindings.get(key)));

        return node.isMethod() ? createMethodSupplier(node.method(), parameters)
                               : createInstanceSupplier(node.constructor(), parameters);
    }

    private List<Key> collectDependencies(Executable executable, Key lead) {
        List<Key> result = new ArrayList<>();

        if (lead != null) {
            result.add(lead);
        }

        for (Parameter parameter : LambdaFactory.getParametersFunction.apply(executable)) {
            result.add(Key.of(parameter));
        }

        return result;
    }

    private ChainedMap<Key, Node> buildTree(Key key, ChainedMap<Key, Node> nodes) {
        if (bindings.containsKey(key)) {
            return nodes;
        }

        Node existing = nodes.get(key);

        if (existing != null) {
            if (existing.key().isSupplier() || key.isSupplier()) {
                return nodes;
            }

            throw new InjectorException("Cycle is detected for " + key);
        }

        nodes.put(key, buildNode(key)).dependencies().forEach((k) -> buildTree(k, new ChainedMap<>(nodes)));
        return nodes;
    }

    private Node buildNode(Key key) {
        Constructor<?> constructor = Utils.locateConstructor(key);

        handleConfiguredBy(constructor.getDeclaringClass());

        return new Node(key, collectDependencies(constructor, null), constructor);
    }

    private void handleConfiguredBy(Class<?> clazz) {
        ConfiguredBy configuredBy = clazz.getAnnotation(ConfiguredBy.class);

        if (configuredBy != null) {
            modules.computeIfAbsent(configuredBy.value(), k -> configureSingle(configuredBy.value()));
        }
    }

    private Node buildNoConfigNode(Key key, Class<?> clazz) {
        return new Node(key, collectDependencies(Utils.locateConstructor(clazz), null), Utils.locateConstructor(clazz));
    }

    private Node buildConfigNode(Class<?> config, ChainedMap<Key, Node> nodes) {
        Key configKey = Key.of(config);

        if (nodes.get(configKey) != null) {
            return null;
        }

        for (Method method : config.getMethods()) {
            if (!method.isAnnotationPresent(Supplies.class)) {
                continue;
            }

            Key key = Key.of(method.getGenericReturnType(), method.getDeclaredAnnotations());
            List<Key> dependencies = collectDependencies(method, configKey);

            nodes.put(key, new Node(key, dependencies, method))
                 .dependencies()
                 .forEach((k) -> buildTree(k, new ChainedMap<>(nodes)));
        }

        return buildNode(configKey);
    }
}