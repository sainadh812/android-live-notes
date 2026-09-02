@echo off
rem Runs the live-network OpenAI connectivity instrumented test on a connected
rem device/emulator. Requires ADB device attached and network reachable.

cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "JAVA_OPTS=-Dhttp.proxyHost=webproxy.ext.ti.com -Dhttp.proxyPort=80 -Dhttps.proxyHost=webproxy.ext.ti.com -Dhttps.proxyPort=80"

call "%~dp0gradlew.bat" connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sainadh.livenotes.OpenAiConnectionInstrumentedTest --console=plain --stacktrace
exit /b %ERRORLEVEL%
