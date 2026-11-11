param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$sourceRoot = Join-Path $projectRoot 'android/app/src/main/java/com/tgwgroup/MiRearScreenSwitcher'
$output = Join-Path $projectRoot 'build/core-tests'
New-Item -ItemType Directory -Force $output | Out-Null
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$sources = @('CommandRunner.java', 'TaskSnapshot.java', 'PhysicalDisplayId.java', 'RearAnimationManager.java', 'TaskServiceClient.java', 'LiquidMotion.java', 'LiquidSurface.java') | ForEach-Object { Join-Path $sourceRoot $_ }
$sources += Get-ChildItem (Join-Path $projectRoot 'tests/android-stubs') -Filter '*.java' -Recurse | ForEach-Object FullName
$sources += Get-ChildItem (Join-Path $projectRoot 'tests') -Filter '*Test.java' | ForEach-Object FullName
& $javac -encoding UTF-8 --release 11 -d $output $sources
if ($LASTEXITCODE -ne 0) { throw 'Core regression compilation failed' }
& $java -cp $output com.tgwgroup.MiRearScreenSwitcher.CoreRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Core regression tests failed' }

& $java -cp $output com.tgwgroup.MiRearScreenSwitcher.ConnectionRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Connection lifecycle tests failed' }

& $java -cp $output com.tgwgroup.MiRearScreenSwitcher.LiquidRegressionTest
if ($LASTEXITCODE -ne 0) { throw 'Liquid regression tests failed' }
