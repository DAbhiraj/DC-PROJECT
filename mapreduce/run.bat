@echo off
echo ==========================================
echo  Running Block Matrix Multiply (MapReduce)
echo ==========================================
cd /d "%~dp0"
docker-compose down -v 2>nul
docker-compose up --build
pause
