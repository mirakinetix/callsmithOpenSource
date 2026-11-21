package com.mirakinetix.callsmith.application.testing;

import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.RequestOptions;
import com.mirakinetix.callsmith.domain.request.SavedRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CollectionRunner {
    public static final int MAX_REQUESTS = 100;
    private static final Duration MAX_DURATION = Duration.ofMinutes(5);
    private final Duration requestTimeout;

    private final CollectionWorkspaceService workspace;
    private final AssertionRepository assertions;
    private final HttpRequestService http;
    private final AssertionEvaluator evaluator;
    private final AtomicBoolean running = new AtomicBoolean();

    public CollectionRunner(CollectionWorkspaceService workspace, AssertionRepository assertions,
                            HttpRequestService http, AssertionEvaluator evaluator) {
        this(workspace, assertions, http, evaluator, RequestOptions.DEFAULT_TIMEOUT);
    }

    CollectionRunner(CollectionWorkspaceService workspace, AssertionRepository assertions,
                     HttpRequestService http, AssertionEvaluator evaluator, Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        this.workspace = workspace;
        this.assertions = assertions;
        this.http = http;
        this.evaluator = evaluator;
    }

    public Summary run(Collection collection, String environment, BooleanSupplier cancelled,
                       Consumer<Integer> onStarted, Consumer<Step> onCompleted) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A collection run is already active.");
        }
        try {
            var plan = new ArrayList<Planned>();
            collect(workspace.loadCollection(collection), "", new HashSet<>(), plan, 0);
            onStarted.accept(plan.size());
            return execute(plan, environment, cancelled, onCompleted);
        } finally {
            running.set(false);
        }
    }

    private void collect(CollectionWorkspaceService.CollectionWorkspace source, String parent,
                         Set<String> visited, List<Planned> plan, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Collection runs support at most 64 folder levels.");
        for (var child : source.orderedChildren(parent)) {
            if (child.folder() != null) {
                if (!visited.add(child.folder().id())) throw new IllegalStateException("Invalid folder hierarchy.");
                collect(source, child.folder().id(), visited, plan, depth + 1);
            } else {
                if (plan.size() == MAX_REQUESTS) {
                    throw new IllegalArgumentException("A collection run is limited to 100 requests.");
                }
                var request = child.request();
                plan.add(new Planned(request, List.copyOf(assertions.find(request.id()))));
            }
        }
    }

    private Summary execute(List<Planned> plan, String environment, BooleanSupplier cancelled,
                            Consumer<Step> onCompleted) {
        long deadline = System.nanoTime() + MAX_DURATION.toNanos();
        List<Step> completed = new ArrayList<>();
        for (var item : plan) {
            if (stopping(cancelled)) return new Summary(plan.size(), completed, Completion.CANCELLED);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return new Summary(plan.size(), completed, Completion.TIME_LIMIT);
            var step = executeStep(completed.size() + 1, item, environment, remaining);
            if (step == null) return new Summary(plan.size(), completed, Completion.CANCELLED);
            completed.add(step);
            onCompleted.accept(step);
        }
        return new Summary(plan.size(), completed, stopping(cancelled) ? Completion.CANCELLED : Completion.FINISHED);
    }

    private Step executeStep(int number, Planned item, String environment, long remaining) {
        var request = item.request();
        if (item.definitions().size() > ResponseAssertion.MAX_PER_REQUEST) {
            return new Step(number, false, null, "Too many saved assertions.", List.of());
        }
        var options = new RequestOptions(Duration.ofNanos(Math.min(
                requestTimeout.toNanos(), remaining)), RequestOptions.Authentication.NONE,
                RequestOptions.BodyFormat.RAW, List.of(), null);
        try {
            var response = http.execute(new HttpRequest(request.method(), request.url(), request.headers(),
                    request.queryParameters(), request.body(), options), environment);
            var checks = evaluator.evaluate(response, item.definitions(), environment);
            boolean explicitStatus = checks.stream().anyMatch(check -> check.passed()
                    && (check.type() == ResponseAssertion.Type.STATUS_EQUALS
                    || check.type() == ResponseAssertion.Type.STATUS_RANGE));
            int status = response.statusCode();
            boolean acceptableStatus = (status >= 200 && status < 300) || explicitStatus;
            String detail = !acceptableStatus ? "HTTP status outside the accepted success range."
                    : checks.isEmpty() ? "No assertions configured." : "Assertions evaluated.";
            return new Step(number, acceptableStatus && checks.stream().allMatch(AssertionEvaluator.Result::passed),
                    status, detail, checks);
        } catch (HttpFailure exception) {
            if (exception.kind() == HttpFailure.Kind.INTERRUPTED || Thread.currentThread().isInterrupted()) return null;
            return new Step(number, false, null, exception.getMessage(), evaluator.noResponse(item.definitions()));
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) return null;
            return new Step(number, false, null, "Request could not be executed or its environment could not be resolved.",
                    evaluator.noResponse(item.definitions()));
        }
    }

    private static boolean stopping(BooleanSupplier cancelled) {
        return cancelled.getAsBoolean() || Thread.currentThread().isInterrupted();
    }

    private record Planned(SavedRequest request, List<ResponseAssertion> definitions) {}
    public enum Completion { FINISHED, CANCELLED, TIME_LIMIT }

    public record Step(int number, boolean passed, Integer status, String detail, List<AssertionEvaluator.Result> assertions) {
        public Step { assertions = List.copyOf(assertions); }
    }

    public record Summary(int total, List<Step> completed, Completion completion) {
        public Summary { completed = List.copyOf(completed); }
        public long passed() { return completed.stream().filter(Step::passed).count(); }
        public long failed() { return completed.size() - passed(); }
    }
}
