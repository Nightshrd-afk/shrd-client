@echo off
REM Runs the Shrd Launcher. Uses the bundled JDK in ..\.toolchain if there is one,
REM otherwise whatever java.exe is on PATH.
setlocal
set HERE=%~dp0
set JAR=%HERE%build\libs\shrd-launcher-1.0.0.jar

if not exist "%JAR%" (
	echo No launcher jar yet. Build it first:
	echo     gradlew.bat build
	exit /b 1
)

if defined JAVA_HOME (
	set JAVA=%JAVA_HOME%\bin\java.exe
	goto run
)

for /d %%D in ("%HERE%..\.toolchain\jdk-*") do (
	if exist "%%D\bin\java.exe" (
		set JAVA=%%D\bin\java.exe
		goto run
	)
)

set JAVA=java

:run
"%JAVA%" -Xmx512M -jar "%JAR%" %*
