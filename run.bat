@echo off
REM ---------------------------------------------------------------------------
REM  Build and run the Emergency Dispatch System (Windows).
REM    run.bat          -> scripted demo + self-test
REM    run.bat test     -> only the correctness self-test
REM    run.bat menu     -> interactive console
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
    exit /b 1
)
echo Using JDK at: !JBIN!

if not exist out mkdir out
"!JBIN!javac.exe" -d out ^
    src\emergency\*.java ^
    src\emergency\geometry\*.java ^
    src\emergency\structures\*.java ^
    src\emergency\model\*.java ^
    src\emergency\system\*.java ^
    src\emergency\test\*.java
if errorlevel 1 (
    echo [ERROR] Compilation failed.
    exit /b 1
)

"!JBIN!java.exe" -cp out emergency.Main %*
endlocal
