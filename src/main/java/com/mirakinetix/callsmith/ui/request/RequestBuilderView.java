package com.mirakinetix.callsmith.ui.request;

import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableService;
import com.mirakinetix.callsmith.application.http.ExecutionCoordinator;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.application.testing.AssertionRepository;
import com.mirakinetix.callsmith.application.testing.AssertionEvaluator;
import com.mirakinetix.callsmith.ui.testing.AssertionDialog;
import com.mirakinetix.callsmith.domain.http.RequestOptions;
import com.mirakinetix.callsmith.ui.response.ResponsePreview;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.ui.Icon;
import com.mirakinetix.callsmith.ui.IconView;
import com.mirakinetix.callsmith.ui.response.ResponseInspector;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static com.mirakinetix.callsmith.application.http.ExecutionCoordinator.Operation.SEND;

public final class RequestBuilderView extends BorderPane {

    private final HttpRequestService httpRequestService;
    private final EnvironmentService environmentService;
    private final EnvironmentVariableService environmentVariableService;
    private final ExecutionCoordinator execution;

    private final ComboBox<String> methodSelector =
            new ComboBox<>();

    private final ComboBox<EnvironmentOption> environmentSelector =
            new ComboBox<>();

    private final TextField urlField =
            new TextField();

    private final Button sendButton =
            new Button(
                    "Send",
                    new IconView(
                            Icon.SEND,
                            16
                    )
            );

    private final Button manageEnvironmentsButton =
            new Button(
                    "Manage",
                    new IconView(
                            Icon.SETTINGS,
                            14
                    )
            );

    private Runnable manageEnvironmentsAction;
    private final VBox savedRequestActions = new VBox(6);
    private final Label requestName = new Label();
    private SavedRequest savedRequest;
    private Draft cleanDraft;
    private boolean showCancellation;
    private long responseGeneration;
    private final Button cancelSend = new Button("Cancel", new IconView(Icon.CANCEL));
    private final Button assertionButton = new Button("Assertions", new IconView(Icon.CHECK));
    private AssertionRepository assertions;
    private AssertionEvaluator assertionEvaluator;

    public void configureAssertions(AssertionRepository repository, AssertionEvaluator evaluator) {
        assertions = repository;
        assertionEvaluator = evaluator;
        assertionButton.setOnAction(event -> {
            if (savedRequest != null) AssertionDialog.show(getScene().getWindow(), savedRequest.id(), assertions);
        });
        assertionButton.setDisable(savedRequest == null);
    }

    private final Button sendOptionsButton = new Button("Send Options...", new IconView(Icon.SETTINGS));
    private RequestOptions sendOptions = RequestOptions.DEFAULT;

    private void resetSendOptions() {
        sendOptions = RequestOptions.DEFAULT;
        sendOptionsButton.setText("Send Options...");
    }

    public void cancelActiveRequest() {
        cancelRequest();
    }

    private void cancelRequest() {
        responseGeneration++;
        showCancellation = false;
        if (execution.cancel(SEND)) {
            sendButton.setText("Cancelling...");
        }
        cancelSend.setDisable(true);
        responseInspector.clear();
    }

    public record Draft(String method, String url, Map<String, String> headers,
                        Map<String, String> query, String body) {}

    public Draft draft() {
        return new Draft(methodSelector.getValue(), urlField.getText(),
                headersEditor.getValues(), queryParametersEditor.getValues(), requestBody.getText());
    }

    public SavedRequest savedRequest() {
        return savedRequest;
    }

    public void markSaved(SavedRequest request) {
        savedRequest = request;
        assertionButton.setDisable(request == null || assertions == null);
        cleanDraft = draft();
        requestName.setText(request == null ? "Unsaved request" : request.name());
    }

    public void updateSavedMetadata(SavedRequest request) {
        savedRequest = request;
        requestName.setText(request.name());
    }

    public boolean allowDiscard() {
        if (draft().equals(cleanDraft) && sendOptions.equals(RequestOptions.DEFAULT)) {
            return true;
        }
        var dialog = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Discard unsaved changes or temporary send options for this request?",
                javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.CANCEL);
        dialog.initOwner(getScene().getWindow());
        dialog.getDialogPane().getStylesheets().addAll(getScene().getStylesheets());
        return dialog.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                == javafx.scene.control.ButtonType.YES;
    }

    public void clearRequest() {
        resetSendOptions();
        methodSelector.setValue("GET");
        urlField.clear();
        headersEditor.setValues(Map.of());
        queryParametersEditor.setValues(Map.of());
        requestBody.clear();
        cancelRequest();
        markSaved(null);
    }

    public void setRequestActions(Runnable save, Runnable saveAs) {
        cancelSend.setDisable(true);
        cancelSend.setOnAction(event -> {
            cancelRequest();
            showCancellation = true;
            responseInspector.displayValidationError("Cancelling...");
        });
        sendOptionsButton.setOnAction(event -> RequestOptionsDialog.show(
                getScene().getWindow(), sendOptions).ifPresent(this::setSendOptions));
        Button create = new Button("New Request", new IconView(Icon.NEW_REQUEST));
        create.setOnAction(event -> {
            if (allowDiscard()) {
                clearRequest();
            }
        });
        Button saveButton = new Button("Save", new IconView(Icon.SAVE));
        saveButton.setOnAction(event -> save.run());
        Button saveAsButton = new Button("Save As", new IconView(Icon.SAVE_AS));
        saveAsButton.setOnAction(event -> saveAs.run());
        var actions = new javafx.scene.layout.FlowPane(6, 4, create, saveButton, saveAsButton, sendOptionsButton);
        if (assertions != null) actions.getChildren().add(assertionButton);
        savedRequestActions.getChildren().setAll(requestName, actions);
        markSaved(null);
    }

    private final KeyValueEditor queryParametersEditor =
            new KeyValueEditor(
                    "Parameter name",
                    "Parameter value"
            );

    private final KeyValueEditor headersEditor =
            new KeyValueEditor(
                    "Header name",
                    "Header value"
            );

    private final TextArea requestBody =
            new TextArea();

    private final ResponseInspector responseInspector =
            new ResponseInspector();

    private final VBox variablesPanel =
            new VBox(4);

    public RequestBuilderView(
            HttpRequestService httpRequestService,
            EnvironmentService environmentService,
            EnvironmentVariableService environmentVariableService,
            ExecutionCoordinator execution
    ) {
        this.httpRequestService = httpRequestService;
        this.environmentService = environmentService;
        this.environmentVariableService = environmentVariableService;
        this.execution = execution;

        configureControls();
        loadEnvironments();
        buildLayout();
    }

    void setSendOptions(RequestOptions options) {
        sendOptions = options;
        sendOptionsButton.setText(options.equals(RequestOptions.DEFAULT) ? "Send Options..." : "Send Options (set)...");
    }

    private void configureControls() {
        urlField.setId("request-url");
        methodSelector.setId("request-method");
        queryParametersEditor.setId("request-query");
        headersEditor.setId("request-headers");
        requestBody.setId("request-body");
        sendButton.setId("request-send");
        cancelSend.setId("request-cancel");
        methodSelector.getItems().addAll(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "HEAD",
                "OPTIONS"
        );

        methodSelector
                .getSelectionModel()
                .select("GET");

        environmentSelector.setPromptText(
                "Environment"
        );

        environmentSelector.setPrefWidth(220);

        environmentSelector.setOnAction(
                event -> updateActiveEnvironment()
        );

        manageEnvironmentsButton.setOnAction(
                event -> {
                    if (manageEnvironmentsAction != null) {
                        manageEnvironmentsAction.run();
                    }
                }
        );

        urlField.setPromptText(
                "https://example.com/api"
        );

        requestBody.setPromptText(
                "Request body"
        );

        requestBody.setWrapText(true);

        sendButton.setOnAction(
                event -> executeRequest()
        );

        variablesPanel.setPadding(
                new Insets(8)
        );

        variablesPanel.setStyle(
                "-fx-border-color: #D5DFEC;" +
                "-fx-border-radius: 4;" +
                "-fx-background-radius: 4;"
        );
    }

    private void loadEnvironments() {
        List<Environment> environments =
                environmentService.findAll();

        environmentSelector.getItems().clear();

        environmentSelector.getItems().add(
                EnvironmentOption.none()
        );

        environments.forEach(
                environment ->
                        environmentSelector.getItems().add(
                                new EnvironmentOption(
                                        environment.id(),
                                        environment.name()
                                )
                        )
        );

        Optional<Environment> activeEnvironment =
                environmentService.findActive();

        if (activeEnvironment.isPresent()) {
            selectEnvironment(
                    activeEnvironment.get().id()
            );

            refreshVariables(
                    activeEnvironment.get().id()
            );
        } else {
            environmentSelector
                    .getSelectionModel()
                    .selectFirst();

            refreshVariables(null);
        }
    }

    public void refreshEnvironments() {
        loadEnvironments();
    }

    public void setManageEnvironmentsAction(
            Runnable action
    ) {
        this.manageEnvironmentsAction = action;
    }

    private void selectEnvironment(
            String environmentId
    ) {
        for (EnvironmentOption option :
                environmentSelector.getItems()) {

            if (option.environmentId()
                    .equals(environmentId)) {

                environmentSelector
                        .getSelectionModel()
                        .select(option);

                return;
            }
        }

        environmentSelector
                .getSelectionModel()
                .selectFirst();
    }

    private void updateActiveEnvironment() {
        EnvironmentOption selected =
                environmentSelector
                        .getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {
            return;
        }

        if (selected.environmentId().isBlank()) {
            environmentService.clearActive();
            refreshVariables(null);
            return;
        }

        environmentService.setActive(
                selected.environmentId()
        );

        refreshVariables(
                selected.environmentId()
        );
    }

    private void refreshVariables(
            String environmentId
    ) {
        variablesPanel.getChildren().clear();

        Label title =
                new Label("Variables");

        title.setStyle(
                "-fx-font-weight: bold;"
        );

        variablesPanel.getChildren().add(
                title
        );

        if (environmentId == null
                || environmentId.isBlank()) {

            variablesPanel.getChildren().add(
                    new Label("No environment selected")
            );

            return;
        }

        List<EnvironmentVariable> variables;
        try {
            variables = environmentVariableService.findSummariesByEnvironmentId(environmentId);
        } catch (IllegalStateException exception) {
            variablesPanel.getChildren().add(new Label("Environment variables could not be read."));
            return;
        }

        if (variables.isEmpty()) {
            variablesPanel.getChildren().add(
                    new Label("No variables defined")
            );

            return;
        }

        for (EnvironmentVariable variable :
                variables) {

            HBox row =
                    new HBox(8);

            Label name =
                    new Label(
                            "{{" + variable.name() + "}}"
                    );

            name.setMinWidth(180);

            Label value =
                    new Label(
                            variable.secret()
                                    ? "********"
                                    : variable.value()
                    );

            value.setMaxWidth(
                    Double.MAX_VALUE
            );

            HBox.setHgrow(
                    value,
                    Priority.ALWAYS
            );

            row.getChildren().addAll(
                    name,
                    value
            );

            variablesPanel.getChildren().add(
                    row
            );
        }
    }

    private void buildLayout() {
        Label environmentLabel =
                new Label(
                        "Environment",
                        new IconView(
                                Icon.ENVIRONMENT,
                                16
                        )
                );

        HBox environmentBar =
                new HBox(
                        8,
                        environmentLabel,
                        environmentSelector,
                        manageEnvironmentsButton
                );

        HBox.setHgrow(
                environmentSelector,
                Priority.ALWAYS
        );

        HBox requestBar =
                new HBox(
                        8,
                        methodSelector,
                        urlField,
                        sendButton,
                        cancelSend
                );

        HBox.setHgrow(
                urlField,
                Priority.ALWAYS
        );

        HBox requestContentTitle =
                new HBox(
                        8,
                        new Label("Request")
                );

        VBox requestContent =
                new VBox(
                        8,
                        requestContentTitle,
                        savedRequestActions,
                        requestBar,
                        variablesPanel,
                        new Label("Query Parameters", new IconView(Icon.QUERY)),
                        queryParametersEditor,
                        new Label("Headers", new IconView(Icon.HEADERS)),
                        headersEditor,
                        new Label("Body", new IconView(Icon.BODY)),
                        requestBody
                );

        requestContent.setPadding(
                new Insets(12)
        );

        VBox.setVgrow(
                requestBody,
                Priority.ALWAYS
        );

        ScrollPane requestScrollPane =
                new ScrollPane(requestContent);

        requestScrollPane.setFitToWidth(true);
        requestScrollPane.setFitToHeight(true);

        VBox requestSection =
                new VBox(
                        8,
                        environmentBar,
                        requestScrollPane
                );

        requestSection.setPadding(
                new Insets(12)
        );

        VBox.setVgrow(
                requestScrollPane,
                Priority.ALWAYS
        );

        ScrollPane responseScrollPane =
                new ScrollPane(responseInspector);

        responseScrollPane.setFitToWidth(true);
        responseScrollPane.setFitToHeight(true);

        VBox.setVgrow(
                responseScrollPane,
                Priority.ALWAYS
        );

        SplitPane requestResponsePane =
                new SplitPane(
                        requestSection,
                        responseScrollPane
                );

        requestResponsePane.setOrientation(
                javafx.geometry.Orientation.VERTICAL
        );

        requestResponsePane.setDividerPositions(
                0.5
        );

        setCenter(
                requestResponsePane
        );
    }

    public void loadRequest(
            SavedRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Saved request is required"
            );
        }

        resetSendOptions();
        methodSelector.setValue(
                request.method()
        );

        urlField.setText(
                request.url()
        );

        queryParametersEditor.setValues(
                request.queryParameters()
        );

        headersEditor.setValues(
                request.headers()
        );

        requestBody.setText(
                request.body() == null
                        ? ""
                        : request.body()
        );

        cancelRequest();
        markSaved(request);
    }

    public void loadSavedRequest(
            SavedRequest request
    ) {
        loadRequest(request);
    }

    private void executeRequest() {
        String method =
                methodSelector.getValue();

        String url =
                urlField.getText().trim();

        String body =
                requestBody.getText();

        if (url.isEmpty()) {
            responseInspector.displayValidationError(
                    "Enter a URL"
            );

            return;
        }

        Map<String, String> queryParameters =
                queryParametersEditor.getValues();

        Map<String, String> headers =
                headersEditor.getValues();

        HttpRequest request =
                new HttpRequest(
                        method,
                        url,
                        headers,
                        queryParameters,
                        body.isEmpty()
                                ? null
                                : body,
                        sendOptions
                );

        String environmentId = getSelectedEnvironmentId();
        String assertionRequestId = savedRequest == null ? null : savedRequest.id();

        long generation = responseGeneration + 1;
        var result = new AtomicReference<Runnable>();
        try {
            boolean started = execution.tryStart(SEND,
                    () -> result.set(executeInBackground(request, environmentId, assertionRequestId)),
                    () -> Platform.runLater(() -> finishSend(generation, result.get())));
            if (!started) return; // The modal collection dialog already shows its active run.
        } catch (RejectedExecutionException exception) {
            responseInspector.displayValidationError("Request execution is shutting down.");
            return;
        }
        responseGeneration = generation;
        sendButton.setDisable(true);
        cancelSend.setDisable(false);
        responseInspector.displaySending();
    }

    private Runnable executeInBackground(HttpRequest request, String environmentId, String assertionRequestId) {
        try {
            HttpResponse response = httpRequestService.execute(request, environmentId);
            ResponsePreview preview = ResponsePreview.prepare(response);
            String checks = assertionReport(assertionRequestId, response, environmentId);
            return () -> {
                responseInspector.displayResponse(response, preview);
                responseInspector.displayAssertions(checks);
            };
        } catch (RuntimeException exception) {
            String checks = assertionReport(assertionRequestId, null, environmentId);
            return () -> {
                responseInspector.displayError(exception);
                responseInspector.displayAssertions(checks);
            };
        }
    }

    private void finishSend(long generation, Runnable displayResult) {
        sendButton.setText("Send");
        sendButton.setDisable(false);
        cancelSend.setDisable(true);
        if (showCancellation) {
            showCancellation = false;
            responseInspector.displayValidationError("Cancelled");
        } else if (generation == responseGeneration && displayResult != null) {
            displayResult.run();
        }
    }

    private String assertionReport(String requestId, HttpResponse response, String environmentId) {
        if (requestId == null || assertions == null) return "";
        try {
            var definitions = assertions.find(requestId);
            var results = response == null ? assertionEvaluator.noResponse(definitions)
                    : assertionEvaluator.evaluate(response, definitions, environmentId);
            return results.stream()
                    .map(AssertionEvaluator.Result::reportLine).collect(java.util.stream.Collectors.joining("\n"));
        } catch (RuntimeException exception) {
            return "FAIL — Saved assertions could not be evaluated.";
        }
    }

    public String getSelectedEnvironmentId() {
        EnvironmentOption selected =
                environmentSelector
                        .getSelectionModel()
                        .getSelectedItem();

        if (selected == null
                || selected.environmentId().isBlank()) {
            return null;
        }

        return selected.environmentId();
    }

    private record EnvironmentOption(
            String environmentId,
            String name
    ) {

        private static EnvironmentOption none() {
            return new EnvironmentOption(
                    "",
                    "No Environment"
            );
        }

        @Override
        public String toString() {
            return name;
        }
    }
}