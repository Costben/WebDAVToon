@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "ANDROID_HOME=C:\Users\CostB\AppData\Local\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Building release APK...
call gradlew.bat :app:assembleRelease --stacktrace
if %ERRORLEVEL% NEQ 0 (
    echo Build failed with error %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
echo Build successful!
