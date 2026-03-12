#!/usr/bin/env bash
###############################################################################
# orchard.sh — macOS Docker orchard for the spec-driven workshop
#
# This is the macOS equivalent of bw-opencode. It launches a Docker container
# with only the project directory mounted, keeping your host system isolated.
#
# Usage:
#   ./orchard.sh              # Enter the orchard interactively
#   ./orchard.sh make check   # Run a specific command inside the orchard
#
# SECURITY RULES (from the workshop guide):
#   - Only the project directory is mounted (read-write)
#   - /var is NEVER mounted
#   - No home directory credentials are exposed
#   - Do git push/pull OUTSIDE this orchard
#   - Never put secrets, credentials, or passwords in the orchard
###############################################################################

set -euo pipefail

IMAGE_NAME="spec-workshop-orchard"
CONTAINER_NAME="spec-workshop-orchard-$$"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ── Colors for output ────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

info()  { echo -e "${GREEN}[orchard]${NC} $*"; }
warn()  { echo -e "${YELLOW}[orchard]${NC} $*"; }
error() { echo -e "${RED}[orchard]${NC} $*" >&2; }

# ── Pre-flight checks ────────────────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
    error "Docker is not installed or not in PATH."
    echo "  Install Docker Desktop for Mac: https://docs.docker.com/desktop/install/mac-install/"
    exit 1
fi

if ! docker info &>/dev/null 2>&1; then
    error "Docker daemon is not running. Please start Docker Desktop."
    exit 1
fi

# ── Build the image if it doesn't exist (or if Dockerfile changed) ────────────
DOCKERFILE="${PROJECT_DIR}/Dockerfile.orchard"

if [[ ! -f "$DOCKERFILE" ]]; then
    error "Dockerfile.orchard not found in ${PROJECT_DIR}"
    exit 1
fi

# Rebuild if image doesn't exist or Dockerfile is newer than image
NEEDS_BUILD=false
if ! docker image inspect "$IMAGE_NAME" &>/dev/null 2>&1; then
    NEEDS_BUILD=true
elif [[ "$DOCKERFILE" -nt "$(docker image inspect "$IMAGE_NAME" --format '{{.Created}}' 2>/dev/null || echo '2000-01-01')" ]]; then
    NEEDS_BUILD=true
fi

if $NEEDS_BUILD; then
    info "Planting orchard (this may take a few minutes the first time)..."
    docker build -t "$IMAGE_NAME" -f "$DOCKERFILE" "$PROJECT_DIR"
    info "Orchard ready to harvest."
else
    info "Using existing orchard."
fi

# ── Launch the container ──────────────────────────────────────────────────────
info "Entering the orchard..."
info "Project mounted at: /workspace"
warn "Remember: do git push/pull OUTSIDE the orchard!"
echo ""

# The macOS tooling JDK can't run on Linux. We overlay it with a tmpfs
# that the entrypoint populates with Linux JDK symlinks.
docker run \
    --rm \
    -it \
    --name "$CONTAINER_NAME" \
    --hostname orchard \
    -v "${PROJECT_DIR}:/workspace" \
    --tmpfs /workspace/tooling/jdk-21.0.7+6:exec,uid=1000,gid=1000 \
    --tmpfs /workspace/tooling/openjml:exec,uid=1000,gid=1000 \
    -w /workspace \
    --security-opt no-new-privileges \
    --cap-drop ALL \
    --cap-add DAC_OVERRIDE \
    --cap-add FOWNER \
    "$IMAGE_NAME" \
    "${@:-bash}"
