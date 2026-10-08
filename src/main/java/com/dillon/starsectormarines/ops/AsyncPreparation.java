package com.dillon.starsectormarines.ops;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** One owned background result. Superseded jobs may finish, but can never publish or leak their result. */
final class AsyncPreparation<T extends AutoCloseable> implements AutoCloseable {
    private final Executor executor;
    private Job current;

    AsyncPreparation(Executor executor) { this.executor = executor; }

    synchronized void request(Object key, Supplier<T> build) {
        if (current != null && Objects.equals(current.key, key)) return;
        close();
        Job job = new Job(key);
        current = job;
        executor.execute(() -> {
            synchronized (this) { if (current != job) return; }
            T value;
            try { value = build.get(); }
            catch (Throwable failure) {
                synchronized (this) { if (current == job) job.failure = failure; }
                return;
            }
            synchronized (this) {
                if (current == job) job.result = value;
                else dispose(value);
            }
        });
    }

    synchronized T peek() { return current == null ? null : current.result; }
    synchronized Throwable failure() { return current == null ? null : current.failure; }

    /** Transfer ownership once; a matching completed result is required. */
    synchronized T take(Object key) {
        if (current == null || !Objects.equals(current.key, key) || current.result == null) return null;
        T result = current.result;
        current = null;
        return result;
    }

    @Override public synchronized void close() {
        Job abandoned = current;
        current = null;
        if (abandoned != null && abandoned.result != null) dispose(abandoned.result);
    }

    private static void dispose(AutoCloseable value) {
        try { value.close(); }
        catch (Exception failure) { throw new IllegalStateException("Could not release prepared scenario", failure); }
    }

    private final class Job {
        final Object key;
        T result;
        Throwable failure;
        Job(Object key) { this.key = key; }
    }
}
