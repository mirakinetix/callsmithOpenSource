$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$PackagingRoot = Join-Path $PSScriptRoot "build"
$InputDir = Join-Path $PackagingRoot "jpackage-input"
$Dependencies = Join-Path $PackagingRoot "maven-runtime"
$RuntimeImage = Join-Path $PackagingRoot "runtime"
$OutputDir = Join-Path $PackagingRoot "dist"
$AppIcon = Join-Path $PSScriptRoot "Callsmith.ico"
$PackageFiles = Join-Path $ProjectRoot "packaging\PackageFiles.java"

$Java = Join-Path $env:JAVA_HOME "bin\java.exe"
$Jlink = Join-Path $env:JAVA_HOME "bin\jlink.exe"
$Jpackage = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
if (-not (Test-Path -LiteralPath $AppIcon -PathType Leaf)) { throw "Missing approved Callsmith.ico" }
& $Java $PackageFiles jdk
if ($LASTEXITCODE -ne 0) { throw "Packaging JDK verification failed." }
if ($env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw "Use an x64 Windows packaging host." }

if (Test-Path -LiteralPath $PackagingRoot) {
    $existing = Get-Item -LiteralPath $PackagingRoot
    if (-not $existing.PSIsContainer -or $existing.FullName -ne $PackagingRoot -or
            ($existing.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Unexpected packaging build directory: $PackagingRoot"
    }
    Remove-Item -LiteralPath $existing.FullName -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

Push-Location $ProjectRoot
try {
    & mvn -B '-Dstyle.color=never' -DskipTests package dependency:copy-dependencies '-DincludeScope=runtime' "-DoutputDirectory=$Dependencies"
    if ($LASTEXITCODE -ne 0) { throw "Maven packaging build failed." }
    $AppVersion = (& mvn -q '-Dstyle.color=never' 'org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate' '-Dexpression=project.version' '-DforceStdout' | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $AppVersion -notmatch '^\d+\.\d+\.\d+$') { throw "Unable to resolve the Maven application version." }
}
finally {
    Pop-Location
}

$MainJar = "callsmith-$AppVersion.jar"
& $Java $PackageFiles stage $ProjectRoot $Dependencies (Join-Path $ProjectRoot "target\$MainJar") $InputDir "win"
if ($LASTEXITCODE -ne 0) { throw "Package staging failed." }

$Modules = "java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.prefs,java.scripting,java.sql,java.sql.rowset,java.xml,jdk.crypto.ec,jdk.unsupported"
& $Jlink --add-modules $Modules --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output $RuntimeImage
if ($LASTEXITCODE -ne 0) { throw "jlink failed." }

$JavaFxModulePath = '--module-path=$APPDIR\lib'
$Arguments = @(
    "--type", "app-image",
    "--name", "Callsmith",
    "--icon", $AppIcon,
    "--app-version", $AppVersion,
    "--input", $InputDir,
    "--main-jar", $MainJar,
    "--main-class", "com.mirakinetix.callsmith.CallsmithApplication",
    "--runtime-image", $RuntimeImage,
    "--dest", $OutputDir,
    "--java-options", $JavaFxModulePath,
    "--java-options", "--add-modules=javafx.controls,javafx.fxml",
    "--java-options", "--enable-native-access=javafx.graphics",
    "--java-options", "--enable-native-access=ALL-UNNAMED,org.xerial.sqlitejdbc",
    "--win-console"
)
& $Jpackage @Arguments
if ($LASTEXITCODE -ne 0) { throw "jpackage failed." }

$Application = Join-Path $OutputDir "Callsmith"
$Executable = Join-Path $Application "Callsmith.exe"
$Runtime = Join-Path $Application "runtime"
$Evidence = Join-Path $PackagingRoot "package-evidence.txt"
if (-not (Test-Path -LiteralPath (Join-Path $Application "app\Callsmith.cfg") -PathType Leaf)) {
    throw "The jpackage launcher configuration is missing."
}
"Windows x64 application image, version $AppVersion" | Set-Content -LiteralPath $Evidence
& $Java $PackageFiles verify $InputDir (Join-Path $Application "app") $Runtime | Tee-Object -FilePath $Evidence -Append
if ($LASTEXITCODE -ne 0) { throw "Installed-file verification failed." }
& $Java $PackageFiles windows-icon $AppIcon $Executable | Tee-Object -FilePath $Evidence -Append
if ($LASTEXITCODE -ne 0) { throw "Windows launcher icon verification failed." }
& (Join-Path $Runtime "bin\java.exe") --version | Tee-Object -FilePath $Evidence -Append
if ($LASTEXITCODE -ne 0) { throw "Bundled Java runtime verification failed." }

if ($env:GITHUB_OUTPUT) { "version=$AppVersion" | Add-Content -LiteralPath $env:GITHUB_OUTPUT -Encoding utf8 }
Write-Host "Callsmith application image: $Application"
