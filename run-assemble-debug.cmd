@echo off
rem Assembles the debug APK using the project's Gradle wrapper.
rem Run from anywhere; this script resolves paths relative to itself.

cd /d "%~dp0"

if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Corporate proxy for Gradle/Maven dependency resolution (TI network).
rem Remove these two lines if you are not behind the TI proxy.
set "JAVA_OPTS=-Dhttp.proxyHost=webproxy.ext.ti.com -Dhttp.proxyPort=80 -Dhttps.proxyHost=webproxy.ext.ti.com -Dhttps.proxyPort=80"

call "%~dp0gradlew.bat" assembleDebug --stacktrace
exit /b %ERRORLEVEL%
