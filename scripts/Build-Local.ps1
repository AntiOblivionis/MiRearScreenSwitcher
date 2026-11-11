param(
    [string]$FlutterSdk = $env:FLUTTER_ROOT,
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet('debug', 'release')][string]$Mode = 'release',
    [switch]$Lint
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$FlutterSdk) { $FlutterSdk = Join-Path $projectRoot '.tools/flutter' }
if (!(Test-Path (Join-Path $FlutterSdk 'bin/flutter.bat'))) { throw 'Pass -FlutterSdk with a Flutter 3.32.8 SDK or newer compatible version.' }
if (!$AndroidSdk -or !(Test-Path $AndroidSdk)) { throw 'Pass -AndroidSdk with an Android SDK directory.' }
if (!$JavaHome -or !(Test-Path (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Pass -JavaHome with a JDK 17 or 21 directory.' }
$saved = @{}
foreach ($name in @('JAVA_HOME', 'ANDROID_HOME', 'GRADLE_USER_HOME', 'JAVA_TOOL_OPTIONS', 'GRADLE_OPTS')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $AndroidSdk
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-home'
    $temporaryDirectory = Join-Path $projectRoot '.tools/tmp'
    New-Item -ItemType Directory -Force $temporaryDirectory | Out-Null
    # Local process only: avoid Windows packaged-app temporary-directory socket remapping.
    $env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$temporaryDirectory -Dorg.gradle.internal.instrumentation.agent=false -XX:ActiveProcessorCount=4 -XX:+UseSerialGC"
    # Kotlin's incremental cache cannot relativize a Pub cache on another Windows drive.
    $env:GRADLE_OPTS = '-Dorg.gradle.project.kotlin.incremental=false -Dorg.gradle.project.kotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=1 -Dorg.gradle.vfs.watch=false'
    Push-Location $projectRoot
    try {
        & (Join-Path $FlutterSdk 'bin/flutter.bat') pub get
        if ($LASTEXITCODE -ne 0) { throw 'Flutter dependency resolution failed' }
        & (Join-Path $FlutterSdk 'bin/flutter.bat') build apk "--$Mode" --split-per-abi --target-platform android-arm64
        if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
        # Flutter writes unescaped Windows drive colons; Gradle reads them, but Android Lint rejects them.
        $propertiesFile = Join-Path $projectRoot 'android/local.properties'
        $properties = [IO.File]::ReadAllText($propertiesFile) -replace '=([A-Za-z]):', '=$1\:'
        [IO.File]::WriteAllText($propertiesFile, $properties)
        if ($Lint) {
            Push-Location (Join-Path $projectRoot 'android')
            try {
                & ./gradlew.bat :app:lintDebug --max-workers=1 --no-watch-fs '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process'
                if ($LASTEXITCODE -ne 0) { throw 'Android Lint failed' }
            } finally { Pop-Location }
        }
    } finally { Pop-Location }
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
}
