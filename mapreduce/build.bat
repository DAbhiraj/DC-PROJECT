@echo off
echo ==========================================
echo  Building Hadoop MapReduce Docker Image
echo ==========================================
cd /d "%~dp0"
docker-compose build
echo.
echo Build complete! Run with: docker-compose up
pause
