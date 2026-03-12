@echo off
echo Starting Worker Node on port %1
cd /d "%~dp0\build"
if "%1"=="" (
    java WorkerNode 5001
) else (
    java WorkerNode %1
)
pause
