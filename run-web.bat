@echo off
REM ---------------------------------------------------------------------------
REM  Build and run the Emergency Dispatch Web Frontend (Control Center).
REM  Automatically compiles, starts the embedded Java server, and opens the UI.
REM ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set "JBIN="
where javac >nul 2>nul
if %errorlevel%==0 (
    for /f "delims=" %%P in ('where javac') do set "JBIN=%%~dpP"
) else (
    for /d %%D in ("%LOCALAPPDATA%\Programs\Microsoft\jdk-*") do set "JBIN=%%D\bin\"
)

if "!JBIN!"=="" (
    echo [ERROR] No JDK found. Install a JDK 8 or newer and ensure javac is on PATH.
    pause
    exit /b 1
)
echo [OK] Using JDK at: !JBIN!

if not exist out mkdir out
echo [1/2] Compiling Emergency Dispatch System...
"!JBIN!javac.exe" -d out ^
    src\emergency\*.java ^
    src\emergency\geometry\*.java ^
    src\emergency\structures\*.java ^
    src\emergency\model\*.java ^
    src\emergency\system\*.java ^
    src\emergency\web\*.java ^
    src\emergency\test\*.java
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

echo [2/2] Launching Emergency Control Center at http://localhost:8080 ...
"!JBIN!java.exe" -cp out emergency.Main web %*
endlocal
