#!/bin/bash

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TARGET_DIR="$PROJECT_ROOT/target"

PACKAGING_ROOT="$PROJECT_ROOT/packaging/macos/build"
INPUT_DIR="$PACKAGING_ROOT/jpackage-input"
RUNTIME_LIB_DIR="$INPUT_DIR/lib"

RUNTIME_IMAGE="$PACKAGING_ROOT/runtime"
OUTPUT_DIR="$PACKAGING_ROOT/dist"

APP_NAME="Callsmith"
APP_VERSION="0.1.0"
MAIN_JAR="callsmith-${APP_VERSION}.jar"
MAIN_CLASS="com.mirakinetix.callsmith.CallsmithApplication"

ARCH="$(uname -m)"

case "$ARCH" in
    arm64)
        JAVAFX_CLASSIFIER="mac-aarch64"
        ;;
    x86_64)
        JAVAFX_CLASSIFIER="mac"
        ;;
    *)
        echo "Unsupported macOS architecture: $ARCH"
        exit 1
        ;;
esac

echo "========================================"
echo "Callsmith macOS Application Package"
echo "Version: $APP_VERSION"
echo "Architecture: $ARCH"
echo "JavaFX classifier: $JAVAFX_CLASSIFIER"
echo "========================================"
echo ""

# ------------------------------------------------------------
# 1. Build application
# ------------------------------------------------------------

echo "[1/7] Building Callsmith..."

cd "$PROJECT_ROOT"

mvn clean package -DskipTests

# ------------------------------------------------------------
# 2. Prepare packaging directories
# ------------------------------------------------------------

echo ""
echo "[2/7] Preparing packaging directories..."

rm -rf "$PACKAGING_ROOT"

mkdir -p "$RUNTIME_LIB_DIR"
mkdir -p "$OUTPUT_DIR"

# ------------------------------------------------------------
# 3. Copy application JAR
# ------------------------------------------------------------

echo ""
echo "[3/7] Copying application JAR..."

APPLICATION_JAR="$TARGET_DIR/$MAIN_JAR"

if [ ! -f "$APPLICATION_JAR" ]; then
    echo "Application JAR not found: $APPLICATION_JAR"
    exit 1
fi

cp "$APPLICATION_JAR" "$INPUT_DIR/"

# ------------------------------------------------------------
# 4. Copy runtime dependencies
#
# JavaFX platform-neutral *-25.jar files are intentionally
# excluded.
#
# macOS JavaFX modules are selected according to the native
# architecture of the GitHub Actions runner.
# ------------------------------------------------------------

echo ""
echo "[4/7] Copying runtime dependencies..."

DEPENDENCY_DIR="$TARGET_DIR/macos-packaging-lib"

rm -rf "$DEPENDENCY_DIR"
mkdir -p "$DEPENDENCY_DIR"

mvn dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="$DEPENDENCY_DIR"

copy_dependency() {
    local FILE="$1"

    if [ ! -f "$DEPENDENCY_DIR/$FILE" ]; then
        echo "Required dependency not found:"
        echo "$DEPENDENCY_DIR/$FILE"
        exit 1
    fi

    echo "  Copying $FILE"
    cp "$DEPENDENCY_DIR/$FILE" "$RUNTIME_LIB_DIR/"
}

copy_dependency "jackson-annotations-2.21.jar"
copy_dependency "jackson-core-2.21.5.jar"
copy_dependency "jackson-databind-2.21.5.jar"
copy_dependency "sqlite-jdbc-3.53.4.0.jar"

copy_dependency "javafx-base-25-${JAVAFX_CLASSIFIER}.jar"
copy_dependency "javafx-controls-25-${JAVAFX_CLASSIFIER}.jar"
copy_dependency "javafx-fxml-25-${JAVAFX_CLASSIFIER}.jar"
copy_dependency "javafx-graphics-25-${JAVAFX_CLASSIFIER}.jar"

# ------------------------------------------------------------
# 5. Create bundled Java runtime
# ------------------------------------------------------------

echo ""
echo "[5/7] Creating bundled Java runtime..."

JLINK_MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.sql,java.sql.rowset,java.xml,jdk.crypto.ec,jdk.unsupported"

jlink \
    --add-modules "$JLINK_MODULES" \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --output "$RUNTIME_IMAGE"

BUNDLED_JAVA="$RUNTIME_IMAGE/bin/java"

if [ ! -f "$BUNDLED_JAVA" ]; then
    echo "Bundled Java runtime was not created correctly:"
    echo "$BUNDLED_JAVA"
    exit 1
fi

# ------------------------------------------------------------
# 6. Create macOS application image
# ------------------------------------------------------------

echo ""
echo "[6/7] Creating macOS DMG..."

JAVAFX_MODULE_PATH="\$APPDIR/lib"

jpackage \
    --type dmg \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --input "$INPUT_DIR" \
    --main-jar "$MAIN_JAR" \
    --main-class "$MAIN_CLASS" \
    --runtime-image "$RUNTIME_IMAGE" \
    --dest "$OUTPUT_DIR" \
    --java-options "--module-path=$JAVAFX_MODULE_PATH" \
    --java-options "--add-modules=javafx.controls,javafx.fxml" \
    --java-options "--enable-native-access=javafx.graphics" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    --mac-package-identifier "com.mirakinetix.callsmith"

# ------------------------------------------------------------
# 7. Verify package
# ------------------------------------------------------------

echo ""
echo "[7/7] Verifying package..."

DMG_FILE="$OUTPUT_DIR/${APP_NAME}-${APP_VERSION}.dmg"

if [ ! -f "$DMG_FILE" ]; then
    echo "DMG was not created:"
    echo "$DMG_FILE"
    exit 1
fi

echo ""
echo "========================================"
echo "PACKAGE CREATED SUCCESSFULLY"
echo "========================================"
echo ""
echo "Architecture:"
echo "$ARCH"
echo ""
echo "JavaFX classifier:"
echo "$JAVAFX_CLASSIFIER"
echo ""
echo "DMG:"
echo "$DMG_FILE"
echo ""
echo "Size:"
du -h "$DMG_FILE"
echo ""