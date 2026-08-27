@echo off
reg add HKCU\Console /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1
cd /d "%~dp0"
javac --release 8 Console.java
start "By DF Console" cmd /k "java Console"
