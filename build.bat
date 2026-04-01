@echo off
rem Zora Framework Build Script (Windows Batch)
rem Description: Build script for the Zora Java multi-module project on Windows

setlocal enabledelayedexpansion

rem Project information
set PROJECT_NAME=zora
set MVN_CMD=mvn

rem Check if Maven is installed
where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven (mvn) is not installed or not in PATH
    exit /b 1
)

for /f "tokens=1-6" %%a in ('mvn -v ^| findstr /r "Apache Maven"') do (
    echo [INFO] Maven is installed: %%a %%b %%c %%d %%e %%f
)

rem Read version from VERSION file
if not exist VERSION (
    echo [ERROR] VERSION file not found in project root
    exit /b 1
)
set /p VERSION=<VERSION
echo [INFO] Project version: %VERSION%

rem Functions
if "%1"=="" goto full_build
if "%1"=="clean" goto clean
if "%1"=="compile" goto compile
if "%1"=="test" goto test
if "%1"=="package" goto package
if "%1"=="install" goto install
if "%1"=="build" goto full_build
if "%1"=="quick" goto quick_build
if "%1"=="help" goto show_help
goto unknown

:unknown
echo [ERROR] Unknown command: %1
echo Use 'help' to see available commands
exit /b 1

:show_help
echo Zora Framework Build Script (Windows)
echo.
echo Usage: build.bat [COMMAND]
echo.
echo Commands:
echo   clean       Clean the project (remove target directories)
echo   compile     Compile all modules
echo   test        Run all tests
echo   package     Package all modules (skips tests)
echo   install     Install to local Maven repository (skips tests)
echo   build       Full clean build with tests and install
echo   quick       Quick build (skips tests)
echo   help        Show this help message
echo.
echo Examples:
echo   build.bat build     Full build
echo   build.bat clean     Only clean
echo   build.bat quick     Build without tests
goto end

:clean
echo [INFO] Cleaning project...
call %MVN_CMD% clean -Drevision=%VERSION%
echo [INFO] Clean completed
goto end

:compile
echo [INFO] Compiling project...
call %MVN_CMD% compile -Drevision=%VERSION%
echo [INFO] Compilation completed
goto end

:test
echo [INFO] Running tests...
call %MVN_CMD% test -Drevision=%VERSION%
echo [INFO] Tests completed
goto end

:package
echo [INFO] Packaging project...
call %MVN_CMD% package -Drevision=%VERSION% -DskipTests
echo [INFO] Packaging completed
goto end

:install
echo [INFO] Installing to local Maven repository...
call %MVN_CMD% install -Drevision=%VERSION% -DskipTests
echo [INFO] Installation completed
goto end

:full_build
echo [INFO] Starting full build of %PROJECT_NAME%...
call:clean
call:compile
call:test
call:package
call:install
echo [INFO] Full build completed successfully!
goto end

:quick_build
echo [INFO] Starting quick build (skipping tests)...
call:clean
call:compile
call:package
call:install
echo [INFO] Quick build completed successfully!
goto end

:end
endlocal
