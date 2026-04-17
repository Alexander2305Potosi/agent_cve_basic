@echo off
REM Script helper para ejecutar CVE Resolver Agent en Windows
REM Uso: run_agent.bat [ruta_al_folder]

setlocal enabledelayedexpansion

REM Obtener el directorio donde está este script
set SCRIPT_DIR=%~dp0

REM Configurar Python (ajustar si es necesario)
set PYTHON=python

REM Verificar argumentos
if "%~1"=="" (
    echo Uso: %~nx0 ^<ruta_al_folder^>
    echo Ejemplo: %~nx0 "C:\proyectos\supplier_documents_backend"
    echo.
    echo IMPORTANTE: Usar comillas dobles si la ruta contiene espacios
    exit /b 1
)

REM La ruta se pasa entre comillas para manejar espacios
set FOLDER_PATH=%~1

echo ==========================================
echo CVE Resolver Agent - Windows Launcher
echo ==========================================
echo Folder: %FOLDER_PATH%
echo.

REM Ejecutar el agente con la ruta proporcionada
%PYTHON% "%SCRIPT_DIR%cve_resolver_agent.py" --folder "%FOLDER_PATH%" --apply

if errorlevel 1 (
    echo.
    echo Error al ejecutar el agente
    pause
    exit /b 1
)

echo.
echo Completado!
pause
