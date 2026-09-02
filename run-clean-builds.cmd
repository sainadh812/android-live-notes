@echo off
cd /d C:\Users\91812\AppData\Local\hermes\hermes-agent\android-live-notes
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call C:\Users\91812\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat clean assembleDebug assembleRelease testDebugUnitTest lintDebug --console=plain --stacktrace
exit /b %ERRORLEVEL%
