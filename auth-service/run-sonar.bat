@echo off
setlocal

set "BACKEND_ROOT=%~dp0.."
pushd "%BACKEND_ROOT%"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%BACKEND_ROOT%\scripts\run-sonar-module.ps1" -Module auth-service -ProjectKey auth-service -ProjectName auth-service
set "EXIT_CODE=%ERRORLEVEL%"
popd

endlocal
exit /b %EXIT_CODE%
