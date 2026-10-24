@echo off
rem ===========================================================================
rem  Pomodoro Habit Tracker - one-command start for Windows (Command Prompt)
rem
rem  Usage:  run.bat          (or double-click run.bat in File Explorer)
rem
rem  What it does:
rem    1. finds Conda (Miniforge, Miniconda or Anaconda), even without "conda init"
rem    2. creates the Conda environment from environment.yml (first run only)
rem    3. builds the application with Maven
rem    4. starts it at http://127.0.0.1:8080 - press Ctrl+C to stop it
rem
rem  Optional: another port. First type   set APP_PORT=8081   then run.bat
rem ===========================================================================
setlocal EnableExtensions

set "ENV_NAME=pomodoro-habit-tracker"
set "JAR=target\pomodoro-habit-tracker.jar"

rem Always work in the project folder, no matter where the script is called from.
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem 1. Find Conda
rem ---------------------------------------------------------------------------
set "CONDA_BAT="
call :find_conda
if not defined CONDA_BAT goto :no_conda
echo.
echo ==^> Using Conda: %CONDA_BAT%

rem ---------------------------------------------------------------------------
rem 2. Create the Conda environment the first time
rem ---------------------------------------------------------------------------
call "%CONDA_BAT%" env list | findstr /B /C:"%ENV_NAME% " >nul
if errorlevel 1 goto :create_env
echo.
echo ==^> Conda environment "%ENV_NAME%" found - no installation needed.
goto :activate

:create_env
echo.
echo ==^> Creating the Conda environment "%ENV_NAME%" from environment.yml.
echo     This happens only once. It downloads Java 21 and Maven and can take a few minutes.
call "%CONDA_BAT%" env create --file environment.yml
if errorlevel 1 goto :create_failed

rem ---------------------------------------------------------------------------
rem 3. Activate it (this works even without "conda init")
rem ---------------------------------------------------------------------------
:activate
call "%CONDA_BAT%" activate %ENV_NAME%
if errorlevel 1 goto :activate_failed
where java >nul 2>nul
if errorlevel 1 goto :tools_missing
where mvn >nul 2>nul
if errorlevel 1 goto :tools_missing

rem ---------------------------------------------------------------------------
rem 4. Build the application ("call" is needed because mvn is a .cmd script)
rem ---------------------------------------------------------------------------
echo.
echo ==^> Building the application with Maven (the first build also downloads Maven plugins)...
call mvn -B -q -DskipTests package
if errorlevel 1 goto :build_failed
if not exist "%JAR%" goto :build_failed

rem ---------------------------------------------------------------------------
rem 5. Start it (runs in this window; Ctrl+C stops it)
rem ---------------------------------------------------------------------------
set "SHOW_PORT=8080"
if defined APP_PORT set "SHOW_PORT=%APP_PORT%"
echo.
echo ==^> Starting Pomodoro Habit Tracker at http://127.0.0.1:%SHOW_PORT%  (press Ctrl+C to stop)
java -jar "%JAR%"
echo.
echo Pomodoro Habit Tracker has stopped. Your data is saved in the data folder.
endlocal
exit /b 0

rem ---------------------------------------------------------------------------
rem Helpers
rem ---------------------------------------------------------------------------
:find_conda
rem a) Conda that is already set up in this window (for example the Anaconda Prompt)
if defined CONDA_EXE if exist "%CONDA_EXE%" (
    for %%I in ("%CONDA_EXE%") do call :check_conda_folder "%%~dpI.."
)
if defined CONDA_BAT exit /b 0
rem b) conda on the PATH
for /f "delims=" %%I in ('where conda.exe conda.bat 2^>nul') do (
    if not defined CONDA_BAT call :check_conda_folder "%%~dpI.."
)
if defined CONDA_BAT exit /b 0
rem c) the usual install folders
for %%D in ("%USERPROFILE%\miniforge3" "%USERPROFILE%\miniconda3" "%USERPROFILE%\anaconda3"
            "%LOCALAPPDATA%\miniforge3" "%LOCALAPPDATA%\miniconda3" "%LOCALAPPDATA%\anaconda3"
            "%ProgramData%\miniforge3" "%ProgramData%\miniconda3" "%ProgramData%\anaconda3"
            "C:\miniforge3" "C:\miniconda3" "C:\anaconda3") do (
    if not defined CONDA_BAT call :check_conda_folder "%%~D"
)
exit /b 0

:check_conda_folder
rem %1 is a folder that might be a Conda installation.
if not exist "%~1\condabin\conda.bat" exit /b 0
for %%R in ("%~1") do set "CONDA_BAT=%%~fR\condabin\conda.bat"
exit /b 0

:no_conda
echo.
echo ERROR: Conda was not found.
echo   This project uses Conda to install Java 21 and Maven for you, so Conda must be installed once.
echo   Install Miniforge (free): https://conda-forge.org/download/
echo   Then open a new Command Prompt and run run.bat again.
echo   (Alternative without Conda: install Docker Desktop and run: docker compose up --build)
goto :failed

:create_failed
echo.
echo ERROR: Could not create the Conda environment.
echo   Check your internet connection and run run.bat again.
echo   If a half-finished environment was left behind, remove it first with:
echo     conda env remove -n %ENV_NAME%
goto :failed

:activate_failed
echo.
echo ERROR: Could not activate the Conda environment "%ENV_NAME%".
goto :failed

:tools_missing
echo.
echo ERROR: Java or Maven is missing in the Conda environment "%ENV_NAME%".
echo   Repair it with:  conda env update -n %ENV_NAME% -f environment.yml --prune
goto :failed

:build_failed
echo.
echo ERROR: The Maven build failed. Read the messages above for the reason.
goto :failed

:failed
echo.
rem Keep the window open when the script was started by double-clicking it.
pause
endlocal
exit /b 1
