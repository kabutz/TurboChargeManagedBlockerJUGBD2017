package eu.javaspecialists.performance.managedblocker;

import java.math.*;
import java.util.*;
import java.util.concurrent.*;

// 1. test100_000_000() time = 55152
// 2. test100_000_000() time = 28813
// 3. test100_000_000() time = 17573
// 4. test100_000_000() time = 12742
// 5. test100_000_000() time = 9941
// 6. test100_000_000() time = 9063


// TODO: Sign up to Heinz's Newsletter: www.javaspecialists.eu
public class Fibonacci {
    public BigInteger f(int n) {
        Map<Integer, BigInteger> cache = new ConcurrentHashMap<>();
        cache.put(0, BigInteger.ZERO);
        cache.put(1, BigInteger.ONE);

        return f(n, cache);
    }

    private final BigInteger RESERVED = BigInteger.valueOf(-1000);

    private BigInteger f(int n, Map<Integer, BigInteger> cache) {
        BigInteger result = cache.putIfAbsent(n, RESERVED);
        if (result == null) { // I was the first to put it in - I must do the work
            int half = (n + 1) / 2;

            RecursiveTask<BigInteger> f0_task = new RecursiveTask<BigInteger>() {
                protected BigInteger compute() {
                    return f(half - 1, cache);
                }
            };
            f0_task.fork();
            BigInteger f1 = f(half, cache);
            BigInteger f0 = f0_task.join();

            long time = n > 1000 ? System.currentTimeMillis() : 0;
            try {
                if (n % 2 == 1) {
                    result = f0.multiply(f0).add(f1.multiply(f1));
                } else {
                    result = f0.shiftLeft(1).add(f1).multiply(f1);
                }
                synchronized (RESERVED) {
                    cache.put(n, result);
                    RESERVED.notifyAll();
                }
            } finally {
                time = n > 1000 ? System.currentTimeMillis() - time : 0;
                if (time > 50) {
                    System.out.printf("f(%d) took %d%n", n, time);
                }
            }
        } else if (result == RESERVED) { // someone else is busy with this number - wait
            try {
                FibonacciReservedBlocker blocker = new FibonacciReservedBlocker(n, cache);
                ForkJoinPool.managedBlock(blocker);
                result = blocker.result;
                assert result != RESERVED;
                assert result != null;
            } catch (InterruptedException e) {
                throw new CancellationException("interrupted");
            }

        }
        return result;
    }

    private final class FibonacciReservedBlocker implements ForkJoinPool.ManagedBlocker {
        private final int n;
        private final Map<Integer, BigInteger> cache;
        private BigInteger result;

        public FibonacciReservedBlocker(int n, Map<Integer, BigInteger> cache) {
            this.n = n;
            this.cache = cache;
        }

        public boolean isReleasable() { // condition predicate
            return (result = cache.get(n)) != RESERVED;
        }

        public boolean block() throws InterruptedException {
            synchronized (RESERVED) {
                while (!isReleasable()) RESERVED.wait();
            }
            return true;
        }
    }
}
