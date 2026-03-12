@echo off
echo ==========================================
echo  Running MapReduce Simulator (Standalone)
echo ==========================================
cd /d "%~dp0"

if not exist "build" mkdir build

echo Compiling...
javac -d build src\MatrixGenerator.java src\StandaloneMapReduceSimulator.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation FAILED!
    pause
    exit /b 1
)

echo.
echo Running MapReduce simulation with A[4x4] x B[4x4], block size=2
echo.
cd build
java StandaloneMapReduceSimulator 4 4 4 2
cd ..
echo.
pause
