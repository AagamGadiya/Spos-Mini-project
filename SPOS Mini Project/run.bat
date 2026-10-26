@echo off
title Printer Spooler System - SPOS Mini Project
cls
echo ========================================================
echo    PRINTER SPOOLER SYSTEM - SPOS MINI PROJECT
echo    Demonstrating OS Synchronization ^& Mutual Exclusion
echo ========================================================
echo.

cd /d "%~dp0\backend"

echo [1/3] Compiling Java backend...
if not exist "out" mkdir "out"
javac -encoding UTF-8 -d out src\spooler\model\*.java src\spooler\util\*.java src\spooler\service\*.java src\spooler\controller\*.java src\spooler\server\*.java src\spooler\Main.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java compilation failed! Please check if JDK 21+ is in your PATH.
    pause
    exit /b %ERRORLEVEL%
)

echo [2/3] Compilation successful!
echo [3/3] Starting Printer Spooler Server on port 8080...
echo.
echo Dashboard URL: http://localhost:8080
echo.

start "" http://localhost:8080

java -cp out spooler.Main
pause
