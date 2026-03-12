@echo off
echo ==========================================
echo  Building MapReduce Simulator (Standalone)
echo ==========================================
cd /d "%~dp0"

if not exist "build" mkdir build

echo Compiling Java source files...
javac -d build src\MatrixGenerator.java src\StandaloneMapReduceSimulator.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation FAILED!
    pause
    exit /b 1
)

echo.
echo Compilation successful!
pause
