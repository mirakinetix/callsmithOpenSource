package com.mirakinetix.callsmith;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class DesktopFx {
    private static boolean started;

    private DesktopFx() {}

    public static synchronized void start() throws Exception {
        if (started) return;
        var ready = new CompletableFuture<Void>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            ready.complete(null);
        });
        ready.get(10, TimeUnit.SECONDS);
        started = true;
    }

    public static <T> T fx(Callable<T> action) throws Exception {
        var task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(8, TimeUnit.SECONDS);
    }

    public static FutureTask<Void> action(Runnable action) {
        var task = new FutureTask<Void>(action, null);
        Platform.runLater(task);
        return task;
    }

    public static <T> T await(Callable<T> lookup, Predicate<T> ready, String description) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        do {
            T value = fx(lookup);
            if (ready.test(value)) return value;
            Thread.sleep(25);
        } while (System.nanoTime() < until);
        throw new AssertionError("Timed out waiting for " + description);
    }

    public static DialogPane dialog(String title) throws Exception {
        return await(() -> Window.getWindows().stream()
                .filter(window -> window instanceof Stage stage && title.equals(stage.getTitle()))
                .map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .findFirst().orElse(null), pane -> pane != null, title);
    }

    public static Button button(Parent root, String text) {
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    public static void choose(DialogPane dialog, String text) {
        var buttonType = dialog.getButtonTypes().stream().filter(type -> type.getText().equals(text))
                .findFirst().orElseThrow();
        ((Button) dialog.lookupButton(buttonType)).fire();
    }

    public static void finish(FutureTask<?> action) throws Exception {
        action.get(8, TimeUnit.SECONDS);
    }

    public static void closeWindows() throws Exception {
        fx(() -> {
            for (Window window : java.util.List.copyOf(Window.getWindows()).reversed()) window.hide();
            return null;
        });
    }
}
