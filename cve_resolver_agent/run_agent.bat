@echo off
REM Script helper para ejecutar CVE Resolver Agent en Windows
REM Uso: run_agent.bat [ruta_al_folder] [--debug]

setlocal enabledelayedexpansion

REM Obtener el directorio donde está este script
set SCRIPT_DIR=%~dp0

REM Configurar Python (ajustar si es necesario)
set PYTHON=python

REM Verificar argumentos
if "%~1"=="" (
    echo Uso: %~nx0 ^<ruta_al_folder^> [--debug]
    echo Ejemplo: %~nx0 "C:\proyectos\supplier_documents_backend"
    echo Ejemplo con debug: %~nx0 "C:\proyectos\supplier_documents_backend" --debug
    echo.
    echo IMPORTANTE: Usar comillas dobles si la ruta contiene espacios
    exit /b 1
)

REM La ruta se pasa entre comillas para manejar espacios
set FOLDER_PATH=%~1
set DEBUG_FLAG=%~2

echo ==========================================
echo CVE Resolver Agent - Windows Launcher
echo ==========================================
echo Folder: %FOLDER_PATH%
if "%~2"=="--debug" (
    echo Modo: DEBUG activado
    set DEBUG_ARG=--debug
)
echo.
echo Para cancelar: Presiona Ctrl+C y espera a que termine limpiamente
echo.

REM Configurar JAVA_HOME si está instalado en ubicaciones comunes
if not defined JAVA_HOME (
    if exist "C:\Program Files\Microsoft\openjdk\jdk-21.0.6.7-hotspot" (
        set JAVA_HOME=C:\Program Files\Microsoft\openjdk\jdk-21.0.6.7-hotspot
        echo JAVA_HOME auto-configurado: %JAVA_HOME%
    ) else if exist "C:\Program Files\Java\jdk-21" (
        set JAVA_HOME=C:\Program Files\Java\jdk-21
        echo JAVA_HOME auto-configurado: %JAVA_HOME%
    )
)

REM Ejecutar el agente con la ruta proporcionada
%PYTHON% "%SCRIPT_DIR%cve_resolver_agent.py" --folder "%FOLDER_PATH%" --apply %DEBUG_ARG%

if errorlevel 1 (
    echo.
    echo Error al ejecutar el agente
    echo.
    echo Si la compilación falla, verifica:
    echo 1. Java está instalado y JAVA_HOME está configurado
    echo 2. El proyecto tiene gradlew.bat o gradle instalado
    echo 3. Ejecuta con --debug para mas informacion
    pause
    exit /b 1
)

echo.
echo Completado!
pause
