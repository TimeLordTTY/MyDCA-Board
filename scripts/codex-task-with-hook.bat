@echo off
setlocal
chcp 65001 >nul 2>&1

if "%~1"=="" (
  echo Usage: codex-task-with-hook.bat "your task prompt"
  exit /b 1
)

powershell -ExecutionPolicy Bypass -File "%~dp0codex-task-with-hook.ps1" -TaskPrompt "%~1"
exit /b %errorlevel%
