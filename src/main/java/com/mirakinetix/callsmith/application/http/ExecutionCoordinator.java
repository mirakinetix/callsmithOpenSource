package com.mirakinetix.callsmith.application.http;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

/** Coordinates the two user-initiated HTTP execution paths in one workspace. */
public final class ExecutionCoordinator implements AutoCloseable {
    public enum Operation { SEND, COLLECTION_RUN }

    private final ExecutorService executor;
    private Active active;
    private boolean closed;

    public ExecutionCoordinator(ExecutorService executor) {
        this.executor = executor;
    }

    public synchronized boolean tryStart(Operation operation, Runnable work, Runnable onFinished) {
        if (closed) throw new RejectedExecutionException("Request execution is shutting down.");
        if (active != null) return false;

        var execution = new Active(operation, new FutureTask<Void>(work, null));
        active = execution;
        try {
            executor.execute(() -> {
                try {
                    execution.task().run();
                } finally {
                    // FutureTask can report cancellation before the running task exits.
                    finish(execution);
                    onFinished.run();
                }
            });
        } catch (RuntimeException exception) {
            active = null;
            throw exception;
        }
        return true;
    }

    public synchronized Operation activeOperation() {
        return active == null ? null : active.operation();
    }

    public synchronized boolean cancel(Operation operation) {
        return active != null && active.operation() == operation && active.task().cancel(true);
    }

    private synchronized void finish(Active execution) {
        if (active == execution) active = null;
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            if (active != null) active.task().cancel(true);
        }
        executor.close();
    }

    private record Active(Operation operation, FutureTask<Void> task) {}
}
