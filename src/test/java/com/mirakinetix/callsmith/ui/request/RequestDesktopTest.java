package com.mirakinetix.callsmith.ui.request;

import com.mirakinetix.callsmith.DesktopFx;
import com.mirakinetix.callsmith.application.environment.EnvironmentRepository;
import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableRepository;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableService;
import com.mirakinetix.callsmith.application.http.ExecutionCoordinator;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.domain.http.RequestOptions;
import com.mirakinetix.callsmith.domain.http.SelectedUpload;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static com.mirakinetix.callsmith.domain.http.RequestOptions.AuthType.BEARER;
import static com.mirakinetix.callsmith.domain.http.RequestOptions.BodyFormat.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Opt in on a machine with a desktop. Drives JavaFX controls with an isolated loopback server. */
@EnabledIfSystemProperty(named = "callsmith.desktop.tests", matches = "true")
class RequestDesktopTest {
    @TempDir Path directory;

    @Test
    void sendsFormatsAndAuthCancelsAndDisplaysBinaryThroughEditorControls() throws Exception {
        DesktopFx.start();
        var slowStarted = new CountDownLatch(1);
        var releaseSlow = new CountDownLatch(1);
        var slowFinished = new CountDownLatch(1);
        var upload = new CompletableFuture<byte[]>();
        var endpoint = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            endpoint.setExecutor(workers);
            endpoint.createContext("/echo", exchange -> {
                try (exchange) {
                    byte[] bytes = exchange.getRequestBody().readAllBytes();
                    var media = exchange.getRequestHeaders().getFirst("Content-Type");
                    exchange.getResponseHeaders().set("Content-Type", media == null ? "text/plain" : media);
                    if (exchange.getRequestHeaders().getFirst("Authorization") != null) {
                        bytes = exchange.getRequestHeaders().getFirst("Authorization").getBytes(StandardCharsets.UTF_8);
                    }
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            endpoint.createContext("/slow", exchange -> {
                try (exchange) {
                    slowStarted.countDown();
                    releaseSlow.await(10, TimeUnit.SECONDS);
                    exchange.sendResponseHeaders(503, -1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    slowFinished.countDown();
                }
            });
            endpoint.createContext("/upload", exchange -> {
                try (exchange) {
                    upload.complete(exchange.getRequestBody().readAllBytes());
                    var bytes = new byte[]{0, (byte) 255, (byte) 128};
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            endpoint.start();

            Stage stage = fx(() -> {
                var view = new RequestBuilderView(new HttpRequestService(new JavaHttpRequestExecutor()),
                        new EnvironmentService(mock(EnvironmentRepository.class)),
                        new EnvironmentVariableService(mock(EnvironmentVariableRepository.class)), new ExecutionCoordinator(workers));
                view.setRequestActions(() -> {}, () -> {});
                var window = new Stage();
                var scene = new Scene(view, 1100, 720);
                scene.getStylesheets().add(getClass().getResource("/styles/brand.css").toExternalForm());
                window.setScene(scene);
                window.show();
                return window;
            });
            try {
                var view = (RequestBuilderView) stage.getScene().getRoot();
                String base = "http://localhost:" + endpoint.getAddress().getPort();
                send(view, base + "/echo", "{\"demo\":1}", options(JSON, RequestOptions.Authentication.NONE, null));
                awaitBody(view, "\"demo\" : 1");
                send(view, base + "/echo", "hello text", options(TEXT, RequestOptions.Authentication.NONE, null));
                awaitBody(view, "hello text");
                send(view, base + "/echo", "", options(RAW,
                        new RequestOptions.Authentication(BEARER, "", "fictional-ui-token"), null));
                awaitBody(view, "Bearer fictional-ui-token");

                send(view, base + "/slow", "", RequestOptions.DEFAULT);
                assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
                fx(() -> {
                    button(view, "#request-cancel").fire();
                    assertTrue(((Label) view.lookup("#response-status")).getText().contains("Cancelling"));
                    assertTrue(button(view, "#request-send").isDisabled());
                    return null;
                });
                DesktopFx.await(() -> ((Label) view.lookup("#response-status")).getText(),
                        value -> value.contains("Cancelled"), "completed cancellation");
                assertFalse(fx(() -> button(view, "#request-send").isDisabled()));
                send(view, base + "/echo", "new response", options(TEXT, RequestOptions.Authentication.NONE, null));
                awaitBody(view, "new response");
                releaseSlow.countDown();
                assertTrue(slowFinished.await(5, TimeUnit.SECONDS));
                awaitBody(view, "new response");

                byte[] fixture = {0, 4, (byte) 255};
                Path path = Files.write(directory.resolve("fixture.bin"), fixture);
                send(view, base + "/upload", "", options(MULTIPART, RequestOptions.Authentication.NONE,
                        SelectedUpload.select(path, "attachment")));
                awaitBody(view, "Binary or unsupported encoding");
                assertTrue(new String(upload.get(3, TimeUnit.SECONDS), StandardCharsets.ISO_8859_1)
                        .contains(new String(fixture, StandardCharsets.ISO_8859_1)));
            } finally {
                releaseSlow.countDown();
                fx(() -> { stage.hide(); return null; });
                endpoint.stop(0);
            }
        } finally {
            endpoint.stop(0);
        }
    }

    @Test
    void configuresAuthenticationBodyFormatAndTimeoutInOwnedSendOptionsDialog() throws Exception {
        DesktopFx.start();
        Stage owner = fx(() -> {
            var stage = new Stage();
            var scene = new Scene(new javafx.scene.layout.StackPane(), 490, 390);
            stage.setScene(scene);
            stage.show();
            return stage;
        });
        try {
            for (var auth : List.of(RequestOptions.AuthType.BASIC, BEARER,
                    RequestOptions.AuthType.API_HEADER, RequestOptions.AuthType.API_QUERY)) {
                var outcome = new CompletableFuture<RequestOptions>();
                var task = DesktopFx.action(() -> outcome.complete(RequestOptionsDialog.show(
                        owner, RequestOptions.DEFAULT).orElseThrow()));
                var pane = DesktopFx.dialog("Send Options");
                fx(() -> {
                    assertSame(owner, ((Stage) pane.getScene().getWindow()).getOwner());
                    chooseValue(pane, auth);
                    chooseValue(pane, FORM);
                    var secret = (javafx.scene.control.PasswordField) pane.lookup(".password-field");
                    secret.setText("{{fixture_password}}");
                    for (var node : pane.lookupAll(".text-field")) {
                        if (node instanceof TextField field && !(field instanceof javafx.scene.control.PasswordField)) {
                            if (field.getPromptText().startsWith("Username")) field.setText("fixture_name");
                            if (field.getText().equals("30")) field.setText("0");
                        }
                    }
                    DesktopFx.choose(pane, "Apply");
                    assertTrue(pane.getScene().getWindow().isShowing(), "Invalid timeout must keep the dialog open");
                    for (var node : pane.lookupAll(".text-field")) {
                        if (node instanceof TextField field && field.getText().equals("0")) field.setText("45");
                    }
                    ((TextArea) pane.lookup(".text-area")).setText("repeat=first\nrepeat=second");
                    DesktopFx.choose(pane, "Apply");
                    return null;
                });
                DesktopFx.finish(task);
                var options = outcome.get(3, TimeUnit.SECONDS);
                assertEquals(auth, options.authentication().type());
                assertEquals("{{fixture_password}}", options.authentication().credential());
                assertEquals(Duration.ofSeconds(45), options.timeout());
                assertEquals(List.of("first", "second"),
                        options.fields().stream().map(RequestOptions.Field::value).toList());
            }
        } finally {
            DesktopFx.closeWindows();
        }
    }

    @SuppressWarnings("unchecked")
    private static void chooseValue(javafx.scene.Parent root, Object value) {
        var combo = (javafx.scene.control.ComboBox<Object>) root.lookupAll(".combo-box").stream()
                .filter(node -> node instanceof javafx.scene.control.ComboBox<?> choices
                        && choices.getItems().contains(value)).findFirst().orElseThrow();
        combo.getSelectionModel().select(value);
    }

    private static void send(RequestBuilderView view, String url, String body, RequestOptions options) throws Exception {
        fx(() -> {
            view.clearRequest();
            ((TextField) view.lookup("#request-url")).setText(url);
            ((TextArea) view.lookup("#request-body")).setText(body);
            view.setSendOptions(options);
            button(view, "#request-send").fire();
            return null;
        });
    }

    private static void awaitBody(RequestBuilderView view, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            String body = fx(() -> ((TextArea) view.lookup("#response-body")).getText());
            if (body.contains(expected)) {
                assertFalse(fx(() -> button(view, "#request-send").isDisabled()));
                return;
            }
            Thread.sleep(30);
        } while (System.nanoTime() < deadline);
        fail("Expected response did not reach the JavaFX inspector");
    }

    private static Button button(RequestBuilderView view, String selector) {
        return (Button) view.lookup(selector);
    }

    private static RequestOptions options(RequestOptions.BodyFormat format, RequestOptions.Authentication auth,
                                          SelectedUpload upload) {
        return new RequestOptions(RequestOptions.DEFAULT_TIMEOUT, auth, format, List.of(), upload);
    }

    private static <T> T fx(Callable<T> operation) throws Exception {
        var task = new FutureTask<>(operation);
        Platform.runLater(task);
        return task.get(10, TimeUnit.SECONDS);
    }
}
