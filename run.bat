@echo off
cd /d "%~dp0"
call mvn -q -DskipTests package || exit /b 1
java -jar target\flight-reservation.jar %*
