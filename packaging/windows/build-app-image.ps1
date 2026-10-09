$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$TargetDir = Join-Path $ProjectRoot "target"

$PackagingRoot = Join-Path $PSScriptRoot "build"
$InputDir = Join-Path $PackagingRoot "jpackage-input"
$RuntimeLibDir = Join-Path $InputDir "lib"

$RuntimeImage = Join-Path $PackagingRoot "runtime"
$OutputDir = Join-Path $PackagingRoot "dist"

$AppName = "Callsmith"
$AppVersion = "0.1.0"
$MainJar = "callsmith-$AppVersion.jar"
$MainClass = "com.mirakinetix.callsmith.CallsmithApplication"

$MavenRepository = Join-Path $env:USERPROFILE ".m2\repository"

Write-Host "========================================"
Write-Host "Callsmith Windows App Image"
Write-Host "Version: $AppVersion"
Write-Host "========================================"
Write-Host ""

# ------------------------------------------------------------
# 1. Build application
# ------------------------------------------------------------

Write-Host "[1/7] Building Callsmith..."

Push-Location $ProjectRoot
try {
    & mvn clean package -DskipTests

    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed."
    }
}
finally {
    Pop-Location
}

# ------------------------------------------------------------
# 2. Prepare packaging directories
# ------------------------------------------------------------

Write-Host ""
Write-Host "[2/7] Preparing packaging directories..."

if (Test-Path $PackagingRoot) {
    Remove-Item $PackagingRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $RuntimeLibDir -Force | Out-Null
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

# ------------------------------------------------------------
# 3. Copy application JAR
# ------------------------------------------------------------

Write-Host ""
Write-Host "[3/7] Copying application JAR..."

$ApplicationJar = Join-Path $TargetDir $MainJar

if (-not (Test-Path $ApplicationJar)) {
    throw "Application JAR not found: $ApplicationJar"
}

Copy-Item $ApplicationJar $InputDir

# ------------------------------------------------------------
# 4. Copy runtime dependencies
#
# IMPORTANT:
# JavaFX platform-neutral *-25.jar files are intentionally
# NOT copied.
#
# The Windows *-25-win.jar files contain the real JavaFX
# module descriptors and native Windows libraries.
# ------------------------------------------------------------

Write-Host ""
Write-Host "[4/7] Copying runtime dependencies..."

$RuntimeDependencies = @(
    @{
        Name = "jackson-annotations-2.21.jar"
        Path = Join-Path $MavenRepository "com\fasterxml\jackson\core\jackson-annotations\2.21\jackson-annotations-2.21.jar"
    },
    @{
        Name = "jackson-core-2.21.5.jar"
        Path = Join-Path $MavenRepository "com\fasterxml\jackson\core\jackson-core\2.21.5\jackson-core-2.21.5.jar"
    },
    @{
        Name = "jackson-databind-2.21.5.jar"
        Path = Join-Path $MavenRepository "com\fasterxml\jackson\core\jackson-databind\2.21.5\jackson-databind-2.21.5.jar"
    },
    @{
        Name = "sqlite-jdbc-3.53.4.0.jar"
        Path = Join-Path $MavenRepository "org\xerial\sqlite-jdbc\3.53.4.0\sqlite-jdbc-3.53.4.0.jar"
    },
    @{
        Name = "javafx-base-25-win.jar"
        Path = Join-Path $MavenRepository "org\openjfx\javafx-base\25\javafx-base-25-win.jar"
    },
    @{
        Name = "javafx-controls-25-win.jar"
        Path = Join-Path $MavenRepository "org\openjfx\javafx-controls\25\javafx-controls-25-win.jar"
    },
    @{
        Name = "javafx-fxml-25-win.jar"
        Path = Join-Path $MavenRepository "org\openjfx\javafx-fxml\25\javafx-fxml-25-win.jar"
    },
    @{
        Name = "javafx-graphics-25-win.jar"
        Path = Join-Path $MavenRepository "org\openjfx\javafx-graphics\25\javafx-graphics-25-win.jar"
    }
)

foreach ($Dependency in $RuntimeDependencies) {

    if (-not (Test-Path $Dependency.Path)) {
        throw "Required dependency not found: $($Dependency.Path)"
    }

    Write-Host "  Copying $($Dependency.Name)"
    Copy-Item $Dependency.Path $RuntimeLibDir
}

# ------------------------------------------------------------
# 5. Create bundled Java runtime
# ------------------------------------------------------------

Write-Host ""
Write-Host "[5/7] Creating bundled Java runtime..."

$jlinkModules = @(
    "java.base",
    "java.desktop",
    "java.logging",
    "java.management",
    "java.naming",
    "java.net.http",
    "java.prefs",
    "java.scripting",
    "java.sql",
    "java.sql.rowset",
    "java.xml",
    "jdk.crypto.ec",
    "jdk.unsupported"
)

& jlink `
    --add-modules ($jlinkModules -join ",") `
    --strip-debug `
    --no-header-files `
    --no-man-pages `
    --compress=zip-6 `
    --output $RuntimeImage

if ($LASTEXITCODE -ne 0) {
    throw "jlink failed."
}

$BundledJava = Join-Path $RuntimeImage "bin\java.exe"

if (-not (Test-Path $BundledJava)) {
    throw "Bundled Java runtime was not created correctly: $BundledJava"
}

# ------------------------------------------------------------
# 6. Create Windows application image
# ------------------------------------------------------------

Write-Host ""
Write-Host "[6/7] Creating Windows application image..."

$JavaFxModulePath = '--module-path=$APPDIR\lib'

& jpackage `
    --type app-image `
    --name $AppName `
    --app-version $AppVersion `
    --input $InputDir `
    --main-jar $MainJar `
    --main-class $MainClass `
    --runtime-image $RuntimeImage `
    --dest $OutputDir `
    --java-options $JavaFxModulePath `
    --java-options "--add-modules=javafx.controls,javafx.fxml" `
    --java-options "--enable-native-access=javafx.graphics" `
    --java-options "--enable-native-access=ALL-UNNAMED" `
    --win-console

if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed."
}

# ------------------------------------------------------------
# 7. Verify package
# ------------------------------------------------------------

Write-Host ""
Write-Host "[7/7] Verifying application image..."

$ApplicationImage = Join-Path $OutputDir $AppName
$Executable = Join-Path $ApplicationImage "Callsmith.exe"
$PackagedRuntime = Join-Path $ApplicationImage "runtime\bin\java.exe"
$PackagedAppDir = Join-Path $ApplicationImage "app"
$PackagedLibDir = Join-Path $PackagedAppDir "lib"
$PackagedConfig = Join-Path $PackagedAppDir "Callsmith.cfg"

if (-not (Test-Path $ApplicationImage)) {
    throw "Application image was not created: $ApplicationImage"
}

if (-not (Test-Path $Executable)) {
    throw "Callsmith executable was not created: $Executable"
}

if (-not (Test-Path $PackagedRuntime)) {
    throw "Bundled Java runtime was not included: $PackagedRuntime"
}

if (-not (Test-Path $PackagedConfig)) {
    throw "jpackage configuration was not created: $PackagedConfig"
}

$ExpectedPackagedLibraries = @(
    "javafx-base-25-win.jar",
    "javafx-controls-25-win.jar",
    "javafx-fxml-25-win.jar",
    "javafx-graphics-25-win.jar",
    "jackson-annotations-2.21.jar",
    "jackson-core-2.21.5.jar",
    "jackson-databind-2.21.5.jar",
    "sqlite-jdbc-3.53.4.0.jar"
)

foreach ($Library in $ExpectedPackagedLibraries) {

    $PackagedLibrary = Join-Path $PackagedLibDir $Library

    if (-not (Test-Path $PackagedLibrary)) {
        throw "Expected packaged library is missing: $PackagedLibrary"
    }
}

Write-Host ""
Write-Host "========================================"
Write-Host "PACKAGE CREATED SUCCESSFULLY"
Write-Host "========================================"
Write-Host ""
Write-Host "Application image:"
Write-Host $ApplicationImage
Write-Host ""
Write-Host "Executable:"
Write-Host $Executable
Write-Host ""
Write-Host "Bundled Java:"
Write-Host $PackagedRuntime
Write-Host ""
Write-Host "Packaged libraries:"
Get-ChildItem $PackagedLibDir -Filter "*.jar" |
    Select-Object Name, Length |
    Format-Table -AutoSize
Write-Host ""
Write-Host "The application image is ready for manual testing."
Write-Host ""