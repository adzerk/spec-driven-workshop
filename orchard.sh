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
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(pwd)"

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
DOCKERFILE="${SCRIPT_DIR}/Dockerfile.orchard"

if [[ ! -f "$DOCKERFILE" ]]; then
    error "Dockerfile.orchard not found in ${SCRIPT_DIR}"
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
    DOCKERFILE_REAL="$(readlink -f "$DOCKERFILE")"
    docker build -t "$IMAGE_NAME" -f "$DOCKERFILE_REAL" "$SCRIPT_DIR"
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
CLAUDE_ENV=()
if [[ -n "${ANTHROPIC_API_KEY:-}" ]]; then
    CLAUDE_ENV+=(-e "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}")
fi

# Extract Claude Code OAuth credentials from macOS Keychain and pass them to the
# container via a temp file (not a -e env var, which would be visible in docker inspect).
# Claude Code stores its OAuth session in the keychain under "Claude Code-credentials".
# The entrypoint writes the credentials to ~/.claude/.credentials.json (Linux fallback).
# Authenticate on the host first: run `claude` and complete the OAuth flow.
# Note: OAuth tokens expire. Refreshed tokens are lost when the container exits,
# but Claude Code will use the refresh token for the duration of the session.
CREDS_ENV_FILE=""
cleanup_creds() { [[ -n "$CREDS_ENV_FILE" ]] && rm -f "$CREDS_ENV_FILE"; }
trap cleanup_creds EXIT

if [[ -z "${ANTHROPIC_API_KEY:-}" ]] && command -v security &>/dev/null; then
    CLAUDE_CREDS_JSON=$(security find-generic-password -s "Claude Code-credentials" -w 2>/dev/null || true)
    if [[ -n "$CLAUDE_CREDS_JSON" ]]; then
        CREDS_ENV_FILE=$(mktemp)
        printf 'CLAUDE_CODE_KEYCHAIN_CREDS=%s\n' "$CLAUDE_CREDS_JSON" > "$CREDS_ENV_FILE"
        chmod 600 "$CREDS_ENV_FILE"
        info "Extracted Claude Code OAuth credentials from macOS Keychain"
    else
        warn "No Claude Code credentials found in macOS Keychain."
        warn "Run 'claude' on the host and complete login first, or set ANTHROPIC_API_KEY."
    fi
fi

# Mount Claude Code user config (.claude.json) to a staging location.
# The entrypoint copies it to a writable path so Claude Code can function.
# Only the top-level config is mounted — not ~/.claude/ (settings, history, etc.)
# since credentials now come via the keychain injection above.
CLAUDE_CONFIG_MOUNT=()
if [[ -f "${HOME}/.claude.json" ]]; then
    CLAUDE_CONFIG_MOUNT=(-v "${HOME}/.claude.json:/tmp/claude-host-config.json:ro")
    info "Mounting Claude Code config from ~/.claude.json"
fi

docker run \
    --rm \
    -it \
    --name "$CONTAINER_NAME" \
    --hostname orchard \
    -v "${PROJECT_DIR}:/workspace" \
    ${CLAUDE_CONFIG_MOUNT[@]+"${CLAUDE_CONFIG_MOUNT[@]}"} \
    ${CREDS_ENV_FILE:+--env-file "$CREDS_ENV_FILE"} \
    --tmpfs /workspace/tooling/jdk-21.0.7+6:exec,uid=1000,gid=1000 \
    --tmpfs /workspace/tooling/openjml:exec,uid=1000,gid=1000 \
    -w /workspace \
    --security-opt no-new-privileges \
    --cap-drop ALL \
    --cap-add DAC_OVERRIDE \
    --cap-add FOWNER \
    ${CLAUDE_ENV[@]+"${CLAUDE_ENV[@]}"} \
    "$IMAGE_NAME" \
    "${@:-bash}"
