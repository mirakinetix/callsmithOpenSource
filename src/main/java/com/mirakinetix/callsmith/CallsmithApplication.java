package com.mirakinetix.callsmith;

import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableResolver;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableService;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.mirakinetix.callsmith.infrastructure.persistence.ApplicationDataDirectory;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentVariableRepository;
import com.mirakinetix.callsmith.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallsmithApplication extends Application {

    private ExecutorService requestExecutor;

    @Override
    public void start(Stage stage) {
        Path databasePath =
                ApplicationDataDirectory.resolve()
                        .resolve("callsmith.db");

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

        SqliteEnvironmentVariableRepository
                environmentVariableRepository =
                new SqliteEnvironmentVariableRepository(
                        sqliteDatabase
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

        JavaHttpRequestExecutor httpRequestExecutor =
                new JavaHttpRequestExecutor();

        HttpRequestService httpRequestService =
                new HttpRequestService(
                        httpRequestExecutor,
                        variableResolver
                );

        requestExecutor =
                Executors.newVirtualThreadPerTaskExecutor();

        MainWindow mainWindow =
                new MainWindow(
                        httpRequestService,
                        environmentService,
                        requestExecutor
                );

        mainWindow.show(stage);
    }

    @Override
    public void stop() {
        if (requestExecutor != null) {
            requestExecutor.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}