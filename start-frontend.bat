@echo off
echo ============================================================
echo Starting React Live Operations Dashboard...
echo ============================================================
cd /d %~dp0frontend
call npm run dev
pause
