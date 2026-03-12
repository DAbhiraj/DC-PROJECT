@echo off
echo Starting Master Node
echo  A[4x4] x B[4x4], block=2, workers on ports 5001 5002
cd /d "%~dp0\build"
java MasterNode 4 4 4 2 5001 5002
pause
