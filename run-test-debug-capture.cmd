@echo off
rem Same as run-test-debug.cmd but redirects full output to a log file
rem in this folder (test-debug.log) for later inspection.

cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "JAVA_OPTS=-Dhttp.proxyHost=webproxy.ext.ti.com -Dhttp.proxyPort=80 -Dhttps.proxyHost=webproxy.ext.ti.com -Dhttps.proxyPort=80"

call "%~dp0gradlew.bat" testDebugUnitTest --console=plain --stacktrace > "%~dp0test-debug.log" 2>&1
exit /b %ERRORLEVEL%
