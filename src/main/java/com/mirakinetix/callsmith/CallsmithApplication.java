package com.mirakinetix.callsmith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.collection.CollectionExportService;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportPersistenceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableResolver;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableService;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.application.testing.AssertionEvaluator;
import com.mirakinetix.callsmith.application.testing.CollectionRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteAssertionRepository;
import com.mirakinetix.callsmith.infrastructure.collection.PostmanCollectionExporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.InsomniaCollectionImporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.OpenApiImporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.PostmanCollectionImporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.SqliteCollectionImportPersistenceService;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.mirakinetix.callsmith.infrastructure.persistence.ApplicationDataDirectory;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteCollectionFolderRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteCollectionRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentVariableRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteSavedRequestRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteSecretStorage;
import com.mirakinetix.callsmith.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallsmithApplication extends Application {

    private ExecutorService requestExecutor;
    private SqliteSecretStorage secretStorage;

    @Override
    public void start(Stage stage) {
        startWorkspace(stage, ApplicationDataDirectory.resolve().resolve("callsmith.db"));
    }

    void startWorkspace(Stage stage, Path databasePath) {

        SqliteDatabase sqliteDatabase =
                new SqliteDatabase(databasePath);

        CallsmithDatabase callsmithDatabase =
                new CallsmithDatabase(
                        sqliteDatabase,
                        new MigrationRunner()
                );

        callsmithDatabase.initialize();

        SqliteEnvironmentRepository environmentRepository =
                new SqliteEnvironmentRepository(
                        sqliteDatabase
                );

        secretStorage = new SqliteSecretStorage(sqliteDatabase);

        SqliteEnvironmentVariableRepository
                environmentVariableRepository =
                new SqliteEnvironmentVariableRepository(
                        sqliteDatabase, secretStorage
                );

        EnvironmentService environmentService =
                new EnvironmentService(
                        environmentRepository
                );

        EnvironmentVariableService
                environmentVariableService =
                new EnvironmentVariableService(
                        environmentVariableRepository
                );

        EnvironmentVariableResolver variableResolver =
                new EnvironmentVariableResolver(
                        environmentVariableRepository
                );

        SqliteCollectionRepository collectionRepository =
                new SqliteCollectionRepository(
                        sqliteDatabase
                );

        SqliteCollectionFolderRepository folderRepository =
                new SqliteCollectionFolderRepository(
                        sqliteDatabase
                );

        SqliteSavedRequestRepository savedRequestRepository =
                new SqliteSavedRequestRepository(
                        sqliteDatabase,
                        new JacksonJsonSerializer()
                );

        CollectionWorkspaceService collectionWorkspaceService =
                new CollectionWorkspaceService(
                        collectionRepository,
                        folderRepository,
                        savedRequestRepository
                );

        ObjectMapper objectMapper =
                new ObjectMapper();

        PostmanCollectionImporter postmanImporter =
                new PostmanCollectionImporter(
                        objectMapper
                );

        OpenApiImporter openApiImporter =
                new OpenApiImporter(
                        objectMapper
                );

        InsomniaCollectionImporter insomniaImporter =
                new InsomniaCollectionImporter(
                        objectMapper
                );

        CollectionImportService collectionImportService =
                new CollectionImportService(
                        List.of(
                                postmanImporter,
                                openApiImporter,
                                insomniaImporter
                        )
                );

        CollectionExportService collectionExportService =
                new CollectionExportService(
                        collectionWorkspaceService,
                        List.of(
                                new PostmanCollectionExporter(
                                        objectMapper
                                )
                        )
                );

        JacksonJsonSerializer jsonSerializer =
                new JacksonJsonSerializer();

        CollectionImportPersistenceService
                collectionImportPersistenceService =
                new SqliteCollectionImportPersistenceService(
                        sqliteDatabase,
                        jsonSerializer
                );

        JavaHttpRequestExecutor httpRequestExecutor =
                new JavaHttpRequestExecutor();

        HttpRequestService httpRequestService =
                new HttpRequestService(
                        httpRequestExecutor,
                        variableResolver
                );

        requestExecutor =
                Executors.newVirtualThreadPerTaskExecutor();

        var assertionRepository = new SqliteAssertionRepository(sqliteDatabase, objectMapper);
        var assertionEvaluator = new AssertionEvaluator(variableResolver);
        var runner = new CollectionRunner(collectionWorkspaceService, assertionRepository,
                httpRequestService, assertionEvaluator);

        MainWindow mainWindow =
                new MainWindow(
                        httpRequestService,
                        environmentService,
                        environmentVariableService,
                        collectionWorkspaceService,
                        collectionExportService,
                        collectionImportService,
                        collectionImportPersistenceService,
                        secretStorage,
                        assertionRepository,
                        assertionEvaluator,
                        runner,
                        requestExecutor
                );

        mainWindow.show(stage);
    }

    @Override
    public void stop() {
        if (secretStorage != null) {
            secretStorage.lock();
        }
        if (requestExecutor != null) {
            requestExecutor.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}