package com.mirakinetix.callsmith.infrastructure.persistence;

import java.nio.file.Path;

public final class ApplicationDataDirectory {

    private static final String APPLICATION_DIRECTORY = "Mirakinetix";
    private static final String PRODUCT_DIRECTORY = "Callsmith";

    private ApplicationDataDirectory() {
    }

    public static Path resolve() {
        String osName =
                System.getProperty("os.name", "")
                        .toLowerCase();

        if (osName.contains("win")) {
            return resolveWindowsDirectory();
        }

        if (osName.contains("mac")) {
            return Path.of(
                    System.getProperty("user.home"),
                    "Library",
                    "Application Support",
                    APPLICATION_DIRECTORY,
                    PRODUCT_DIRECTORY
            );
        }

        return resolveLinuxDirectory();
    }

    private static Path resolveWindowsDirectory() {
        String localAppData =
                System.getenv("LOCALAPPDATA");

        if (localAppData != null
                && !localAppData.isBlank()) {
            return Path.of(
                    localAppData,
                    APPLICATION_DIRECTORY,
                    PRODUCT_DIRECTORY
            );
        }

        return Path.of(
                System.getProperty("user.home"),
                "AppData",
                "Local",
                APPLICATION_DIRECTORY,
                PRODUCT_DIRECTORY
        );
    }

    private static Path resolveLinuxDirectory() {
        String xdgDataHome =
                System.getenv("XDG_DATA_HOME");

        if (xdgDataHome != null
                && !xdgDataHome.isBlank()) {
            return Path.of(
                    xdgDataHome,
                    APPLICATION_DIRECTORY,
                    PRODUCT_DIRECTORY
            );
        }

        return Path.of(
                System.getProperty("user.home"),
                ".local",
                "share",
                APPLICATION_DIRECTORY,
                PRODUCT_DIRECTORY
        );
    }
}