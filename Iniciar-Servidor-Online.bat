@echo off
cd /d "%~dp0"
chcp 65001 >nul
title Servidor Online 24h - Inventory Control

echo ==================================================================
echo   INICIANDO SEU COMPUTADOR COMO SERVIDOR DO INVENTORY CONTROL
echo ==================================================================
echo.

set WEB_ONLY=true
set WEB_PORT=8081
set API_PORT=8080

echo [1/3] Limpando sessoes anteriores nas portas 8080 e 8081...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8081 ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
timeout /t 2 /nobreak >nul

set "JAVA_CMD=java"
if exist "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" set "JAVA_CMD=C:\Program Files\Java\jdk-21.0.10\bin\java.exe"
if exist "%~dp0target\jpackage\Inventory Control\runtime\bin\java.exe" set "JAVA_CMD=%~dp0target\jpackage\Inventory Control\runtime\bin\java.exe"

set "JAR_PATH=%~dp0target\controle-estoque-1.0.0.jar"
if not exist "%JAR_PATH%" if exist "%LOCALAPPDATA%\Programs\Inventory Control\app\controle-estoque-1.0.0.jar" set "JAR_PATH=%LOCALAPPDATA%\Programs\Inventory Control\app\controle-estoque-1.0.0.jar"

echo [2/3] Iniciando o servidor Web local na porta 8081...
start /B "Servidor Inventory Control" "%JAVA_CMD%" -jar "%JAR_PATH%"

timeout /t 3 /nobreak >nul
start "" "http://localhost:8081/login"

echo.
echo [3/3] Conectando Tunel Publico HTTPS com reconexao automatica...
echo Copie o link "https://....lhr.life/login" abaixo e envie para quem quiser!
echo (Mantenha esta janela aberta enquanto quiser deixar o servidor no ar)
echo ==================================================================
echo.

:loop_tunel
ssh -T -o StrictHostKeyChecking=accept-new -o ServerAliveInterval=15 -o ServerAliveCountMax=4 -o TCPKeepAlive=yes -o ExitOnForwardFailure=yes -R 80:localhost:8081 nokey@localhost.run
echo.
echo [AVISO] O tunel gratuito reiniciou a sessao. Reconectando automaticamente em 2 segundos...
timeout /t 2 /nobreak >nul
goto loop_tunel
