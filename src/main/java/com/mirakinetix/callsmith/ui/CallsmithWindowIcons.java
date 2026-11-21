package com.mirakinetix.callsmith.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class CallsmithWindowIcons {
    private CallsmithWindowIcons() {}

    public static void install(Stage stage) {
        stage.getIcons().setAll(Images.ICONS);
    }

    private static final class Images {
        private static final List<Image> ICONS = Stream.of(16, 32, 48, 64, 128, 256)
                .map(size -> "/icons/callsmith/app/Callsmith-" + size + "x" + size + ".png")
                .map(CallsmithWindowIcons.class::getResource)
                .filter(Objects::nonNull)
                .map(URL::toExternalForm)
                .map(Image::new)
                .filter(image -> !image.isError())
                .toList();
    }
}
