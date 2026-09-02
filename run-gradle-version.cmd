@echo off
rem Prints the resolved Gradle version (sanity check for the wrapper + JDK setup).

cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

call "%~dp0gradlew.bat" --version
exit /b %ERRORLEVEL%
