package com.mirakinetix.callsmith.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IconResourcesTest {
    @Test
    void approvedMappingsAndApplicationSizesDecode() throws Exception {
        for (Icon icon : Icon.values()) {
            String path = icon.approvedResource();
            if (path == null) continue;
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertNotNull(image, path);
                assertEquals(48, image.getWidth(), path);
                assertEquals(48, image.getHeight(), path);
            }
        }
        for (int size : List.of(16, 32, 48, 64, 128, 256)) {
            String path = "/icons/callsmith/app/Callsmith-" + size + "x" + size + ".png";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertEquals(size, image.getWidth(), path);
                assertEquals(size, image.getHeight(), path);
            }
        }
    }

    @Test
    void packagingUsesNativeAssetsWithExpectedContainers() throws Exception {
        Path windows = Path.of("packaging", "windows");
        byte[] ico = Files.readAllBytes(windows.resolve("Callsmith.ico"));
        var header = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, header.getShort());
        assertEquals(1, header.getShort());
        int count = Short.toUnsignedInt(header.getShort());
        var sizes = new ArrayList<Integer>();
        for (int index = 0; index < count; index++) {
            int size = Byte.toUnsignedInt(ico[6 + 16 * index]);
            sizes.add(size == 0 ? 256 : size);
        }
        assertEquals(List.of(16, 24, 32, 48, 64, 128, 256), sizes);
        String winScript = Files.readString(windows.resolve("build-app-image.ps1"));
        assertTrue(winScript.contains("\"Callsmith.ico\""));
        assertTrue(winScript.contains("--icon $AppIcon"));

        Path mac = Path.of("packaging", "macos");
        byte[] icns = Files.readAllBytes(mac.resolve("Callsmith.icns"));
        assertEquals("icns", new String(icns, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(icns.length, ByteBuffer.wrap(icns).getInt(4));
        String macScript = Files.readString(mac.resolve("build-app-image.sh"));
        assertTrue(macScript.contains("/packaging/macos/Callsmith.icns"));
        assertTrue(macScript.contains("--icon \"$APP_ICON\""));
    }
}
