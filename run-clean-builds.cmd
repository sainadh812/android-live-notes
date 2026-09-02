@echo off
rem Clean build: debug + release APKs, unit tests, lint, all in one pass.
rem Useful for a full pre-commit sanity check.

cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "JAVA_OPTS=-Dhttp.proxyHost=webproxy.ext.ti.com -Dhttp.proxyPort=80 -Dhttps.proxyHost=webproxy.ext.ti.com -Dhttps.proxyPort=80"

call "%~dp0gradlew.bat" clean assembleDebug assembleRelease testDebugUnitTest lintDebug --console=plain --stacktrace
exit /b %ERRORLEVEL%
