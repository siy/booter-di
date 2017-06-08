package io.booter.injector.core.supplier;

import io.booter.injector.core.exception.InjectorException;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.booter.injector.core.supplier.Suppliers.*;
import static org.assertj.core.api.Assertions.*;

public class SuppliersTest {
    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int NUM_ITERATIONS = NUM_THREADS * 100;

    @Test
    public void measurePerformance() throws Exception {
        AtomicInteger counter = new AtomicInteger();

        measure(lazy(counter::incrementAndGet), "(lambda lazy)");
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    public void shouldCallInitOnlyOnce() throws Exception {
        AtomicInteger counter = new AtomicInteger();

        checkInstantiatedOnce(lazy(counter::incrementAndGet));
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test(expected = InjectorException.class)
    public void shouldThrowExceptionIfNullIsPassedToLazy() throws Exception {
        lazy(null);
    }

    @Test(expected = InjectorException.class)
    public void shouldThrowExceptionIfNullIsPassedToFactoryLazy() throws Exception {
        factoryLazy(null);
    }

    @Test(expected = InjectorException.class)
    public void shouldThrowExceptionIfNullIsPassedToSingleton() throws Exception {
        singleton(null, false);
    }

    @Test(expected = InjectorException.class)
    public void shouldThrowExceptionIfNullIsPassedToEnhancing1() throws Exception {
        enhancing(null, () -> () -> 1);
    }

    @Test(expected = InjectorException.class)
    public void shouldThrowExceptionIfNullIsPassedToEnhancing() throws Exception {
        enhancing(() -> 1, null);
    }

    @Test
    public void shouldCreateLazySingleton() throws Exception {
        AtomicInteger counter = new AtomicInteger();

        Supplier<Integer> supplier = singleton(counter::incrementAndGet, false);
        assertThat(counter.get()).isEqualTo(0);

        Integer value1 = supplier.get();
        assertThat(value1).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);

        Integer value2 = supplier.get();
        assertThat(value2).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);

        assertThat(value1).isSameAs(value2);
    }

    @Test
    public void shouldProgressivelyEnhance() throws Exception {
        Supplier<Integer> supplier = enhancing(() -> 1, () -> () -> 2);

        assertThat(supplier.get()).isEqualTo(1);
        assertThat(supplier.get()).isEqualTo(1);
        assertThat(supplier.get()).isEqualTo(1);
        assertThat(supplier.get()).isEqualTo(2);
    }

    @SuppressWarnings("SpellCheckingInspection")
    private void measure(Supplier<Integer> supplier, String type) throws InterruptedException, java.util.concurrent.ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);

        List<Callable<Integer>> callables = IntStream.range(0, NUM_THREADS)
                                                     .mapToObj(n -> (Callable<Integer>) supplier::get)
                                                     .collect(Collectors.toList());

        long start = System.nanoTime();
        for(int i = 0; i < NUM_ITERATIONS; i++) {
            for (Future<Integer> future : pool.invokeAll(callables)) {
                assertThat(future.get()).isEqualTo(1);
            }
        }
        System.out.printf("Time %s : %.2fms\n",  type, (System.nanoTime() - start)/1000000.0);
    }

    private void checkInstantiatedOnce(Supplier<Integer> supplier) throws InterruptedException, java.util.concurrent.ExecutionException {
        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        CyclicBarrier barrier = new CyclicBarrier(NUM_THREADS);
        @SuppressWarnings("SpellCheckingInspection") List<Callable<Integer>> callables = IntStream.range(0, NUM_THREADS)
                                                     .mapToObj(n -> (Callable<Integer>) () -> {
                                                         barrier.await();
                                                         return supplier.get();
                                                     })
                                                     .collect(Collectors.toList());

        for(Future<Integer> future : pool.invokeAll(callables)) {
            assertThat(future.get()).isEqualTo(1);
        }
    }
}
