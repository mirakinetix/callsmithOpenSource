import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Shared Maven staging and installed-file verification for both native packagers. */
public final class PackageFiles {
    private static final String TEMURIN_VERSION = "25.0.5.1+2";
    private static final Set<String> JAVAFX = Set.of("javafx-base", "javafx-controls", "javafx-fxml", "javafx-graphics");
    private static final Set<String> RUNTIME_LIBRARIES = Set.of("jackson-annotations", "jackson-core",
            "jackson-databind", "sqlite-jdbc");
    private static final Pattern JAVA_FX_JAR = Pattern.compile("(javafx-[a-z]+)-(\\d+(?:\\.\\d+)*)(?:-(.+))?\\.jar");

    public static void main(String[] args) throws IOException {
        String javaVersion = Runtime.version().toString();
        if (!javaVersion.equals(TEMURIN_VERSION + "-LTS")
                || !"Eclipse Adoptium".equals(System.getProperty("java.vendor"))) {
            throw new IOException("Packaging requires Eclipse Temurin " + TEMURIN_VERSION + " but found " + javaVersion);
        }
        switch (args[0]) {
            case "jdk" -> System.out.println("Temurin " + javaVersion);
            case "stage" -> stage(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]),
                    Path.of(args[4]), args[5]);
            case "verify" -> verify(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
            case "windows-icon" -> verifyWindowsIcon(Path.of(args[1]), Path.of(args[2]));
            default -> throw new IllegalArgumentException("Unknown packaging command: " + args[0]);
        }
    }

    private static void stage(Path project, Path dependencies, Path appJar, Path input, String classifier)
            throws IOException {
        Path lib = Files.createDirectories(input.resolve("lib"));
        Path licenses = Files.createDirectories(input.resolve("licenses"));
        Files.copy(appJar, input.resolve(appJar.getFileName()));
        for (String file : List.of("LICENSE", "NOTICE", "BRANDING.md")) {
            Files.copy(project.resolve(file), licenses.resolve(file));
        }
        Path referenceNotices = project.resolve("LICENSES");
        Files.copy(referenceNotices.resolve("LUCIDE-LICENSE.txt"), licenses.resolve("LUCIDE-LICENSE.txt"));
        Files.copy(referenceNotices.resolve("THIRD-PARTY-NOTICES.md"), licenses.resolve("THIRD-PARTY-NOTICES.md"));
        if (classifier.equals("win")) {
            String manifest = "WINDOWS-CORRESPONDING-SOURCES.md";
            Files.copy(referenceNotices.resolve(manifest), licenses.resolve(manifest));
        }

        var versions = new TreeMap<String, String>();
        var javafxVersions = new HashSet<String>();
        try (var files = Files.list(dependencies)) {
            for (Path jar : files.sorted().toList()) {
                String filename = jar.getFileName().toString();
                if (!filename.endsWith(".jar")) throw new IOException("Unexpected Maven runtime file " + filename);
                var fx = JAVA_FX_JAR.matcher(filename);
                if (fx.matches()) {
                    String module = fx.group(1);
                    if (!JAVAFX.contains(module)) throw new IOException("Unreviewed JavaFX module " + module);
                    if (fx.group(3) == null) continue; // Maven's platform-neutral placeholders contain no runtime.
                    if (!classifier.equals(fx.group(3))) throw new IOException("Wrong JavaFX classifier in " + filename);
                    javafxVersions.add(fx.group(2));
                    record(versions, module, fx.group(2));
                } else {
                    String artifact = RUNTIME_LIBRARIES.stream().filter(name -> filename.startsWith(name + "-"))
                            .findFirst().orElseThrow(() -> new IOException("Unreviewed Maven runtime library " + filename));
                    String version = filename.substring(artifact.length() + 1, filename.length() - 4);
                    copyJarNotices(jar, artifact, version, licenses);
                    record(versions, artifact, version);
                }
                Files.copy(jar, lib.resolve(filename));
            }
        }

        if (!versions.keySet().containsAll(JAVAFX) || !versions.keySet().containsAll(RUNTIME_LIBRARIES)
                || javafxVersions.size() != 1) {
            throw new IOException("Maven did not resolve the complete platform runtime: " + versions);
        }
        String core = versions.get("jackson-core");
        int annotationsSeparator = core.lastIndexOf('.');
        if (!core.equals(versions.get("jackson-databind"))
                || !core.substring(0, annotationsSeparator).equals(versions.get("jackson-annotations"))) {
            throw new IOException("Maven resolved incompatible Jackson modules: " + versions);
        }
        String javafxVersion = javafxVersions.iterator().next();
        Path fxNotices = referenceNotices.resolve("OpenJFX-" + javafxVersion);
        Path fxDestination = Files.createDirectory(licenses.resolve(fxNotices.getFileName()));
        for (String file : List.of("LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION",
                "jpeg_fx.md", "mesa3d.md", "pipewire.md")) {
            Files.copy(fxNotices.resolve(file), fxDestination.resolve(file));
        }
        StringBuilder inventory = new StringBuilder("Callsmith bundled components\n");
        inventory.append("Application: ").append(appJar.getFileName()).append('\n');
        inventory.append("Java runtime: Eclipse Temurin ").append(TEMURIN_VERSION).append('\n');
        inventory.append("OpenJFX native classifier: ").append(classifier).append('\n');
        versions.forEach((name, version) -> inventory.append(name).append(' ').append(version).append('\n'));
        Files.writeString(licenses.resolve("COMPONENTS.txt"), inventory);
        System.out.print(inventory);
    }

    private static void record(TreeMap<String, String> versions, String artifact, String version) throws IOException {
        if (versions.putIfAbsent(artifact, version) != null) {
            throw new IOException("Duplicate Maven runtime artifact " + artifact);
        }
    }

    private static void copyJarNotices(Path jar, String artifact, String version, Path licenses) throws IOException {
        Path directory = Files.createDirectory(licenses.resolve(artifact));
        String group = artifact.equals("sqlite-jdbc") ? "org.xerial" : "com.fasterxml.jackson.core";
        try (var zip = new ZipFile(jar.toFile())) {
            var metadata = zip.getEntry("META-INF/maven/" + group + "/" + artifact + "/pom.properties");
            if (metadata == null) throw new IOException("Missing Maven metadata in " + jar.getFileName());
            Properties coordinates = new Properties();
            try (var input = zip.getInputStream(metadata)) {
                coordinates.load(input);
            }
            if (!version.equals(coordinates.getProperty("version")) || !artifact.equals(coordinates.getProperty("artifactId"))
                    || !group.equals(coordinates.getProperty("groupId"))) {
                throw new IOException("Incorrect Maven coordinates in " + jar.getFileName());
            }
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                String basename = name.substring(name.lastIndexOf('/') + 1);
                String upper = basename.toUpperCase(Locale.ROOT);
                if (entry.isDirectory() || !name.startsWith("META-INF/")
                        || !(upper.contains("LICENSE") || upper.contains("NOTICE") || upper.contains("COPYRIGHT"))) {
                    continue;
                }
                if (!basename.matches("[A-Za-z0-9_.-]+") || basename.equals(".") || basename.equals("..")) {
                    throw new IOException("Invalid notice entry in " + jar.getFileName());
                }
                try (var input = zip.getInputStream(entry)) {
                    Files.copy(input, directory.resolve(basename));
                }
            }
        }
        requireFile(directory.resolve("LICENSE"));
        requireFile(directory.resolve(artifact.equals("sqlite-jdbc") ? "LICENSE.zentus" : "NOTICE"));
    }

    private static void verify(Path staged, Path packaged, Path runtime) throws IOException {
        int verified = 0;
        try (var files = Files.walk(staged)) {
            for (Path input : files.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = staged.relativize(input);
                Path target = packaged.resolve(relative);
                requireFile(target);
                if (Files.mismatch(input, target) != -1) throw new IOException("Packaged file differs: " + relative);
                verified++;
            }
        }
        Path release = runtime.resolve("release");
        requireFile(release);
        if (!Files.readString(release).contains("JAVA_VERSION=\"25.0.5.1\"")) {
            throw new IOException("Bundled Java release does not match the packaging JDK");
        }
        requireFile(runtime.resolve("legal/java.base/LICENSE"));
        requireFile(runtime.resolve("legal/java.base/ASSEMBLY_EXCEPTION"));
        System.out.println("Verified " + verified + " installed application, library and notice files byte for byte.");
        System.out.println("Verified bundled Java release metadata and runtime legal files.");
        System.out.print(Files.readString(packaged.resolve("licenses/COMPONENTS.txt")));
    }

    private static void verifyWindowsIcon(Path icoFile, Path launcherFile) throws IOException {
        byte[] ico = Files.readAllBytes(icoFile);
        byte[] launcher = Files.readAllBytes(launcherFile);
        ByteBuffer entries = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
        if (entries.getShort() != 0 || entries.getShort() != 1) throw new IOException("Invalid approved ICO");
        int frames = Short.toUnsignedInt(entries.getShort());
        if (frames == 0 || 6L + frames * 16L > ico.length) throw new IOException("Invalid approved ICO frame table");
        for (int frame = 0; frame < frames; frame++) {
            int entry = 6 + frame * 16;
            int size = entries.getInt(entry + 8);
            int offset = entries.getInt(entry + 12);
            if (size <= 0 || offset < 0 || (long) offset + size > ico.length) {
                throw new IOException("Invalid approved ICO frame");
            }
            boolean found = false;
            for (int start = 0; start <= launcher.length - size; start++) {
                if (launcher[start] == ico[offset]
                        && Arrays.equals(launcher, start, start + size, ico, offset, offset + size)) {
                    found = true;
                    break;
                }
            }
            if (!found) throw new IOException("A frame from the approved ICO is absent from the launcher");
        }
        System.out.println("Verified all " + frames + " approved ICO frames in the Windows launcher.");
    }

    private static void requireFile(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IOException("Missing package file: " + file);
    }
}
