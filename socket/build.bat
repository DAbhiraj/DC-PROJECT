@echo off
echo ==========================================
echo  Compiling Socket Matrix Multiplication
echo ==========================================
cd /d "%~dp0"

if not exist "build" mkdir build

echo Compiling Java source files...
javac -d build src\MatrixBlock.java src\MatrixUtils.java src\WorkerNode.java src\MasterNode.java src\SocketMatrixMultiply.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation FAILED!
    pause
    exit /b 1
)

echo.
echo Compilation successful!
echo Output in build\ directory
echo.
pause
