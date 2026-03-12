@echo off
echo ==========================================
echo  Running Socket Matrix Multiplication
echo ==========================================
echo.
echo  A[4x4] x B[4x4], block size=2, 2 workers
echo ==========================================
cd /d "%~dp0"

REM Compile first
if not exist "build" mkdir build
javac -d build src\MatrixBlock.java src\MatrixUtils.java src\WorkerNode.java src\MasterNode.java src\SocketMatrixMultiply.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation FAILED!
    pause
    exit /b 1
)

echo.
echo Running...
echo.
cd build
java SocketMatrixMultiply 4 4 4 2 2
cd ..
echo.
pause
