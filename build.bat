@echo off
rem Builds Shrd Client. Uses the bundled toolchain in .toolchain when present,
rem then JAVA_HOME, then whatever java is on PATH.
setlocal

set "ROOT=%~dp0"

for /d %%D in ("%ROOT%.toolchain\jdk-*") do set "SHRD_JDK=%%~fD"

if defined SHRD_JDK (
  set "JAVA_HOME=%SHRD_JDK%"
) else if defined JAVA_HOME (
  echo Using JAVA_HOME=%JAVA_HOME%
) else (
  echo No .toolchain JDK and no JAVA_HOME set.
  echo Install JDK 25 ^(https://adoptium.net^) and set JAVA_HOME, then run this again.
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

for /d %%D in ("%ROOT%.toolchain\gradle-*") do set "SHRD_GRADLE=%%~fD\bin\gradle.bat"

if defined SHRD_GRADLE (
  call "%SHRD_GRADLE%" build %*
) else (
  call "%ROOT%gradlew.bat" build %*
)

if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo.
echo Built: %ROOT%build\libs\shrd-client-1.0.0.jar
echo Drop that jar in %%APPDATA%%\.minecraft\mods next to Fabric API.
endlocal
