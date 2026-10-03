package com.mirakinetix.callsmith.ui;

import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class IconView extends SVGPath {

    private static final String RESOURCE_PREFIX =
            "/icons/";

    private static final double DEFAULT_SIZE = 16;

    public IconView(String iconName) {
        this(iconName, DEFAULT_SIZE);
    }

    public IconView(
            String iconName,
            double size
    ) {
        setContent(loadPath(iconName));
        setFill(Color.web("#374151"));
        setScaleX(size / 24.0);
        setScaleY(size / 24.0);
    }

    public void setIconColor(
            Color color
    ) {
        setFill(color);
    }

    private String loadPath(
            String iconName
    ) {
        String resourceName =
                RESOURCE_PREFIX
                        + iconName
                        + ".path";

        try (InputStream inputStream =
                     IconView.class.getResourceAsStream(
                             resourceName
                     )) {

            if (inputStream == null) {
                throw new IllegalArgumentException(
                        "Icon resource not found: "
                                + resourceName
                );
            }

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load icon: "
                            + resourceName,
                    exception
            );
        }
    }
}