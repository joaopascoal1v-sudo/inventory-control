@echo off
cd /d "%~dp0"
chcp 65001 >nul
title Servidor Online - Inventory Control
echo ==================================================================
echo   INICIANDO SEU COMPUTADOR COMO SERVIDOR DO INVENTORY CONTROL
echo ==================================================================
echo.
echo [1/2] Reiniciando o sistema e servidor web na porta 8081...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8081 ^| findstr LISTENING') do taskkill /F /PID %%a >nul 2>&1
start "Inventory Control App" "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" -jar "%~dp0target\controle-estoque-1.0.0.jar"

timeout /t 3 /nobreak >nul

echo.
echo [2/2] Gerando link publico da Internet (HTTPS) para voce enviar...
echo Copie o link "https://....lhr.life" que vai aparecer abaixo e envie para quem quiser!
echo (Mantenha esta janela aberta enquanto quiser deixar o servidor online)
echo ==================================================================
echo.
ssh -o StrictHostKeyChecking=accept-new -R 80:localhost:8081 nokey@localhost.run
pause
