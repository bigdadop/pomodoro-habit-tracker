#!/usr/bin/env bash
# =============================================================================
#  Pomodoro Habit Tracker - one-command start for Linux and macOS
#
#  Usage:   ./run.sh
#
#  What it does:
#    1. finds Conda (even if "conda init" was never run in this terminal)
#    2. creates the Conda environment from environment.yml (first run only)
#    3. builds the application with Maven
#    4. starts it at http://127.0.0.1:8080 - press Ctrl+C to stop it
#
#  Optional settings, for example:   APP_PORT=8081 ./run.sh
#    APP_PORT      port number (default 8080)
#    APP_HOST      network address (default 127.0.0.1 = only this computer)
#    APP_DATA_DIR  folder for the data file (default: data)
# =============================================================================

# Re-run with bash if someone started this script with "sh run.sh".
if [ -z "${BASH_VERSION:-}" ]; then
    exec bash "$0" "$@"
fi

set -euo pipefail

ENV_NAME="pomodoro-habit-tracker"
JAR="target/pomodoro-habit-tracker.jar"

# Always work in the project folder, no matter where the script is called from.
cd "$(dirname "${BASH_SOURCE[0]}")"

info() { printf '\n==> %s\n' "$*"; }
fail() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

# -----------------------------------------------------------------------------
# 1. Find Conda
# -----------------------------------------------------------------------------
find_conda() {
    # a) Conda that is already set up in this terminal
    if [ -n "${CONDA_EXE:-}" ] && [ -x "$CONDA_EXE" ]; then
        echo "$CONDA_EXE"
        return 0
    fi
    # b) conda on the PATH
    if command -v conda >/dev/null 2>&1; then
        command -v conda
        return 0
    fi
    # c) the usual install folders (Miniforge, Miniconda, Anaconda, Homebrew)
    local dir
    for dir in "$HOME/miniforge3" "$HOME/miniconda3" "$HOME/anaconda3" "$HOME/mambaforge" \
               "$HOME/opt/miniconda3" "$HOME/opt/anaconda3" \
               /opt/homebrew/Caskroom/miniforge/base /usr/local/Caskroom/miniforge/base \
               /opt/homebrew/Caskroom/miniconda/base /usr/local/Caskroom/miniconda/base \
               /opt/miniforge3 /opt/miniconda3 /opt/anaconda3 /opt/conda; do
        if [ -x "$dir/bin/conda" ]; then
            echo "$dir/bin/conda"
            return 0
        fi
    done
    return 1
}

CONDA="$(find_conda)" || fail "Conda was not found.
  This project uses Conda to install Java 21 and Maven for you, so Conda must be installed once.
  Install Miniforge (free): https://conda-forge.org/download/
  Then open a new terminal and run ./run.sh again.
  (Alternative without Conda: install Docker and run: docker compose up --build)"
info "Using Conda: $CONDA"

# -----------------------------------------------------------------------------
# 2. Create the Conda environment the first time
# -----------------------------------------------------------------------------
env_exists() {
    "$CONDA" env list | awk '{print $1}' | grep -x "$ENV_NAME" >/dev/null
}

if env_exists; then
    info "Conda environment '$ENV_NAME' found - no installation needed."
else
    info "Creating the Conda environment '$ENV_NAME' from environment.yml."
    echo "    This happens only once. It downloads Java 21 and Maven and can take a few minutes."
    "$CONDA" env create --file environment.yml || fail "Could not create the Conda environment.
  Check your internet connection and run ./run.sh again.
  If a half-finished environment was left behind, remove it first with:
    conda env remove -n $ENV_NAME"
fi

# -----------------------------------------------------------------------------
# 3. Activate it (this works even without "conda init")
# -----------------------------------------------------------------------------
set +u   # Conda's own scripts use variables that may not be set
eval "$("$CONDA" shell.bash hook)" || fail "Could not set up Conda in this script."
conda activate "$ENV_NAME" || fail "Could not activate the Conda environment '$ENV_NAME'."
set -u

if ! command -v java >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
    fail "Java or Maven is missing in the Conda environment '$ENV_NAME'.
  Repair it with:  conda env update -n $ENV_NAME -f environment.yml --prune"
fi
info "Java: $(java -version 2>&1 | sed -n 1p)"

# -----------------------------------------------------------------------------
# 4. Build the application
# -----------------------------------------------------------------------------
info "Building the application with Maven (the first build also downloads Maven plugins)..."
mvn -B -q -DskipTests package || fail "The Maven build failed. Read the messages above for the reason."
[ -f "$JAR" ] || fail "The build finished but $JAR was not created."

# -----------------------------------------------------------------------------
# 5. Start it (runs in the foreground; Ctrl+C stops it)
# -----------------------------------------------------------------------------
url_host="${APP_HOST:-127.0.0.1}"
if [ "$url_host" = "0.0.0.0" ]; then
    url_host="127.0.0.1"
fi
info "Starting Pomodoro Habit Tracker at http://$url_host:${APP_PORT:-8080}  (press Ctrl+C to stop)"

set +e
java -jar "$JAR"
status=$?
set -e

case "$status" in
    0 | 130 | 143)
        echo
        echo "Pomodoro Habit Tracker has stopped. Your data is saved in the data folder."
        ;;
    *)
        fail "The application stopped with an error (exit code $status). Read the messages above."
        ;;
esac
