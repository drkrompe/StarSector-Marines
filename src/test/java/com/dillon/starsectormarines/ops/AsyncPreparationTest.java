package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AsyncPreparationTest {
    private static final class Resource implements AutoCloseable {
        int closes;
        @Override public void close() { closes++; }
    }

    @Test void identicalRequestsBuildOnceAndTransferOwnershipOnlyOnce() {
        List<Runnable> jobs = new ArrayList<>();
        AsyncPreparation<Resource> work = new AsyncPreparation<>(jobs::add);
        Resource resource = new Resource();
        work.request("a", () -> resource);
        work.request("a", () -> { throw new AssertionError("duplicate"); });
        assertEquals(1, jobs.size());
        assertNull(work.peek());
        jobs.get(0).run();
        assertNull(work.take("different"));
        assertSame(resource, work.take("a"));
        assertNull(work.take("a"));
        work.close();
        assertEquals(0, resource.closes);
    }

    @Test void abandonedRunningBuildIsClosedInsteadOfPublished() {
        List<Runnable> jobs = new ArrayList<>();
        AsyncPreparation<Resource> work = new AsyncPreparation<>(jobs::add);
        Resource old = new Resource(), current = new Resource();
        work.request("old", () -> {
            work.request("new", () -> current);
            return old;
        });
        jobs.get(0).run();
        assertEquals(1, old.closes);
        assertNull(work.peek());
        jobs.get(1).run();
        assertSame(current, work.peek());
        work.close();
        assertEquals(1, current.closes);
    }

    @Test void canceledQueuedWorkNeverBuildsAndFailuresCanBeRetried() {
        List<Runnable> jobs = new ArrayList<>();
        AsyncPreparation<Resource> work = new AsyncPreparation<>(jobs::add);
        work.request("old", () -> { throw new AssertionError("canceled work ran"); });
        work.close(); jobs.get(0).run();
        assertNull(work.failure());
        work.request("new", () -> { throw new IllegalStateException("failed"); });
        jobs.get(1).run();
        assertInstanceOf(IllegalStateException.class, work.failure());
        work.close();
        Resource retry = new Resource();
        work.request("new", () -> retry); jobs.get(2).run();
        assertSame(retry, work.take("new"));
    }
}
