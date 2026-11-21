package com.mirakinetix.callsmith.ui;

import com.mirakinetix.callsmith.DesktopFx;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "callsmith.desktop.tests", matches = "true")
class IconDesktopTest {
    @Test
    void rendersApprovedPngAndFallsBackWhenApprovedImageIsMissing() throws Exception {
        DesktopFx.start();
        DesktopFx.fx(() -> {
            var approved = new IconView(Icon.SEND, 16);
            var fallback = new IconView(Icon.SEND, 16, "/icons/callsmith/does-not-exist.png");
            var root = new HBox(approved, fallback);
            new Scene(root);
            root.applyCss();
            root.layout();
            var image = assertInstanceOf(ImageView.class, approved.getChildrenUnmodifiable().getFirst());
            assertEquals(48, image.getImage().getWidth());
            assertEquals(16, approved.getWidth());
            var group = assertInstanceOf(Group.class, fallback.getChildrenUnmodifiable().getFirst());
            assertFalse(group.getChildren().isEmpty());
            assertEquals(16, fallback.getWidth());
            return null;
        });
    }
}
