#!/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd -P)"
PACKAGING_ROOT="$PROJECT_ROOT/packaging/macos/build"
INPUT_DIR="$PACKAGING_ROOT/jpackage-input"
DEPENDENCIES="$PACKAGING_ROOT/maven-runtime"
RUNTIME_IMAGE="$PACKAGING_ROOT/runtime"
OUTPUT_DIR="$PACKAGING_ROOT/dist"
APP_ICON="$PROJECT_ROOT/packaging/macos/Callsmith.icns"
PACKAGE_FILES="$PROJECT_ROOT/packaging/PackageFiles.java"

JAVA="$JAVA_HOME/bin/java"
JLINK="$JAVA_HOME/bin/jlink"
JPACKAGE="$JAVA_HOME/bin/jpackage"
ARCH="$(uname -m)"
case "$ARCH" in
    arm64) JAVAFX_CLASSIFIER="mac-aarch64" ;;
    x86_64) JAVAFX_CLASSIFIER="mac" ;;
    *) echo "Unsupported macOS architecture: $ARCH" >&2; exit 1 ;;
esac
if [ ! -f "$APP_ICON" ]; then
    echo "Missing approved Callsmith.icns" >&2
    exit 1
fi
"$JAVA" "$PACKAGE_FILES" jdk

if [[ "$PACKAGING_ROOT" != "$PROJECT_ROOT/packaging/macos/build" || -L "$PACKAGING_ROOT" ]]; then
    echo "Unexpected packaging build directory: $PACKAGING_ROOT" >&2
    exit 1
fi
rm -rf -- "$PACKAGING_ROOT"
mkdir -p "$OUTPUT_DIR"

cd "$PROJECT_ROOT"
mvn -B -Dstyle.color=never -DskipTests package dependency:copy-dependencies \
    -DincludeScope=runtime "-DoutputDirectory=$DEPENDENCIES"
APP_VERSION="$(mvn -q -Dstyle.color=never org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate \
    -Dexpression=project.version -DforceStdout)"
if ! [[ "$APP_VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Unable to resolve the Maven application version." >&2
    exit 1
fi
MAIN_JAR="callsmith-$APP_VERSION.jar"
"$JAVA" "$PACKAGE_FILES" stage "$PROJECT_ROOT" "$DEPENDENCIES" \
    "$PROJECT_ROOT/target/$MAIN_JAR" "$INPUT_DIR" "$JAVAFX_CLASSIFIER"

MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.sql,java.sql.rowset,java.xml,jdk.crypto.ec,jdk.unsupported"
"$JLINK" --add-modules "$MODULES" --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$RUNTIME_IMAGE"

JAVAFX_MODULE_PATH='$APPDIR/lib'
"$JPACKAGE" \
    --type dmg \
    --name Callsmith \
    --icon "$APP_ICON" \
    --app-version "$APP_VERSION" \
    --input "$INPUT_DIR" \
    --main-jar "$MAIN_JAR" \
    --main-class com.mirakinetix.callsmith.CallsmithApplication \
    --runtime-image "$RUNTIME_IMAGE" \
    --dest "$OUTPUT_DIR" \
    --java-options "--module-path=$JAVAFX_MODULE_PATH" \
    --java-options "--add-modules=javafx.controls,javafx.fxml" \
    --java-options "--enable-native-access=javafx.graphics" \
    --java-options "--enable-native-access=ALL-UNNAMED,org.xerial.sqlitejdbc" \
    --mac-package-identifier com.mirakinetix.callsmith

DMG_FILE="$OUTPUT_DIR/Callsmith-$APP_VERSION.dmg"
test -s "$DMG_FILE"
MOUNTPOINT="$(mktemp -d "$PACKAGING_ROOT/dmg.XXXXXXXX")"
MOUNTED=0
cleanup() {
    if [[ "$MOUNTED" == 1 ]]; then hdiutil detach "$MOUNTPOINT" > /dev/null; fi
    rmdir "$MOUNTPOINT"
}
trap cleanup EXIT
hdiutil attach -readonly -nobrowse -mountpoint "$MOUNTPOINT" "$DMG_FILE"
MOUNTED=1
APP="$MOUNTPOINT/Callsmith.app/Contents"
RUNTIME="$APP/runtime/Contents/Home"
EVIDENCE="$PACKAGING_ROOT/package-evidence.txt"
{
    echo "macOS $ARCH disk image, version $APP_VERSION"
    file "$APP/MacOS/Callsmith" "$RUNTIME/bin/java"
    lipo -verify_arch "$ARCH" "$APP/MacOS/Callsmith"
    lipo -verify_arch "$ARCH" "$RUNTIME/bin/java"
    "$JAVA" "$PACKAGE_FILES" verify "$INPUT_DIR" "$APP/app" "$RUNTIME"
    cmp "$APP_ICON" "$APP/Resources/Callsmith.icns"
    echo "Verified packaged ICNS against the approved source."
    "$RUNTIME/bin/java" --version
    shasum -a 256 "$DMG_FILE"
} | tee "$EVIDENCE"
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then echo "version=$APP_VERSION" >> "$GITHUB_OUTPUT"; fi
echo "Callsmith DMG: $DMG_FILE"
