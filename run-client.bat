@echo off
rem Launches a development Minecraft client with Shrd Client loaded.
rem Use this to test changes without copying the jar into .minecraft.
setlocal

set "ROOT=%~dp0"

for /d %%D in ("%ROOT%.toolchain\jdk-*") do set "SHRD_JDK=%%~fD"

if defined SHRD_JDK (
  set "JAVA_HOME=%SHRD_JDK%"
) else if not defined JAVA_HOME (
  echo No .toolchain JDK and no JAVA_HOME set. Install JDK 25 first.
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

for /d %%D in ("%ROOT%.toolchain\gradle-*") do set "SHRD_GRADLE=%%~fD\bin\gradle.bat"

if defined SHRD_GRADLE (
  call "%SHRD_GRADLE%" runClient
) else (
  call "%ROOT%gradlew.bat" runClient
)

endlocal
