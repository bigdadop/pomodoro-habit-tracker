<#
  Pomodoro Habit Tracker - one-command start for Windows PowerShell

  Usage (from the project folder):
      powershell -ExecutionPolicy Bypass -File .\run.ps1

  What it does:
    1. finds Conda (Miniforge, Miniconda or Anaconda), even without "conda init"
    2. creates the Conda environment from environment.yml (first run only)
    3. builds the application with Maven
    4. starts it at http://127.0.0.1:8080 - press Ctrl+C to stop it

  Optional settings, for example:   $env:APP_PORT = "8081"; .\run.ps1
#>

$EnvName = "pomodoro-habit-tracker"
$Jar = Join-Path "target" "pomodoro-habit-tracker.jar"

# Always work in the project folder, no matter where the script is called from.
Set-Location -Path $PSScriptRoot

# Windows PowerShell 5.1 only runs on Windows; PowerShell 7 tells us with $IsWindows.
$OnWindows = ($PSVersionTable.PSVersion.Major -le 5) -or $IsWindows

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Stop-WithError([string]$Message) {
    Write-Host ""
    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}

# -----------------------------------------------------------------------------
# 1. Find Conda
# -----------------------------------------------------------------------------
function Find-Conda {
    # a) Conda that is already set up in this window
    if ($env:CONDA_EXE -and (Test-Path $env:CONDA_EXE)) {
        return $env:CONDA_EXE
    }
    # b) conda on the PATH
    $names = if ($OnWindows) { @("conda.exe", "conda.bat") } else { @("conda") }
    foreach ($name in $names) {
        $command = Get-Command $name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($command) {
            return $command.Path
        }
    }
    # c) the usual install folders
    if ($OnWindows) {
        $folders = @("$env:USERPROFILE\miniforge3", "$env:USERPROFILE\miniconda3", "$env:USERPROFILE\anaconda3",
                     "$env:LOCALAPPDATA\miniforge3", "$env:LOCALAPPDATA\miniconda3", "$env:LOCALAPPDATA\anaconda3",
                     "$env:ProgramData\miniforge3", "$env:ProgramData\miniconda3", "$env:ProgramData\anaconda3",
                     "C:\miniforge3", "C:\miniconda3", "C:\anaconda3")
        $relativePath = "Scripts\conda.exe"
    } else {
        $folders = @("$HOME/miniforge3", "$HOME/miniconda3", "$HOME/anaconda3", "/opt/conda")
        $relativePath = "bin/conda"
    }
    foreach ($folder in $folders) {
        $candidate = Join-Path $folder $relativePath
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    return $null
}

$Conda = Find-Conda
if (-not $Conda) {
    Stop-WithError ("Conda was not found.`n" +
        "  This project uses Conda to install Java 21 and Maven for you, so Conda must be installed once.`n" +
        "  Install Miniforge (free): https://conda-forge.org/download/`n" +
        "  Then open a new PowerShell window and run this script again.`n" +
        "  (Alternative without Conda: install Docker Desktop and run: docker compose up --build)")
}
Write-Step "Using Conda: $Conda"

# -----------------------------------------------------------------------------
# 2. Create the Conda environment the first time
# -----------------------------------------------------------------------------
$existing = & $Conda env list | Where-Object { ($_.Trim() -split '\s+')[0] -eq $EnvName }
if ($existing) {
    Write-Step "Conda environment '$EnvName' found - no installation needed."
} else {
    Write-Step "Creating the Conda environment '$EnvName' from environment.yml."
    Write-Host "    This happens only once. It downloads Java 21 and Maven and can take a few minutes."
    & $Conda env create --file environment.yml
    if ($LASTEXITCODE -ne 0) {
        Stop-WithError ("Could not create the Conda environment.`n" +
            "  Check your internet connection and run this script again.`n" +
            "  If a half-finished environment was left behind, remove it first with:`n" +
            "    conda env remove -n $EnvName")
    }
}

# -----------------------------------------------------------------------------
# 3. Activate it (this works even without "conda init")
# -----------------------------------------------------------------------------
(& $Conda "shell.powershell" "hook") | Out-String | Invoke-Expression
conda activate $EnvName
if ($env:CONDA_DEFAULT_ENV -ne $EnvName) {
    Stop-WithError "Could not activate the Conda environment '$EnvName'."
}
if (-not (Get-Command java -ErrorAction SilentlyContinue) -or -not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Stop-WithError ("Java or Maven is missing in the Conda environment '$EnvName'.`n" +
        "  Repair it with:  conda env update -n $EnvName -f environment.yml --prune")
}

# -----------------------------------------------------------------------------
# 4. Build the application
# -----------------------------------------------------------------------------
Write-Step "Building the application with Maven (the first build also downloads Maven plugins)..."
mvn -B -q -DskipTests package
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $Jar)) {
    Stop-WithError "The Maven build failed. Read the messages above for the reason."
}

# -----------------------------------------------------------------------------
# 5. Start it (runs in this window; Ctrl+C stops it)
# -----------------------------------------------------------------------------
$port = if ($env:APP_PORT) { $env:APP_PORT } else { "8080" }
Write-Step "Starting Pomodoro Habit Tracker at http://127.0.0.1:$port  (press Ctrl+C to stop)"
try {
    java -jar $Jar
} finally {
    Write-Host ""
    Write-Host "Pomodoro Habit Tracker has stopped. Your data is saved in the data folder."
}
