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
#   - git push/pull from inside this orchard is discouraged, even with
#     GH_TOKEN exported (below) — prefer doing push/pull OUTSIDE the orchard
#   - Never put secrets, credentials, or passwords in the orchard
#
# Optional GitHub tokens (export on the host before running; never passed via
# -e, so they don't leak into this process's argv on multi-user hosts):
#   GH_TOKEN     — fine-grained PAT (repo read + PR read/write). Enables `gh`
#                  CLI and git push/pull inside the orchard. Read directly
#                  from the environment by `gh` — never written to disk.
#                  The PAT itself should be scoped WITHOUT "Contents: write"
#                  so push is rejected by GitHub even though this script
#                  wires it up — push/pull is a token-configuration concern,
#                  not something this script enforces.
#   GITHUB_TOKEN — classic PAT (read:packages scope). Used once, transiently,
#                  to install the @adzerk-scoped libjs-cli package, then
#                  discarded for the rest of the session.
###############################################################################

set -euo pipefail

IMAGE_NAME="spec-workshop-orchard"

# Resolve $0 to its real location, following symlinks (e.g. a `orchard` shim
# on PATH pointing at this script from elsewhere) — `dirname "$0"` alone
# would resolve to the symlink's own directory, not this script's, and
# silently point Dockerfile.orchard lookups at the wrong place.
_src="${BASH_SOURCE[0]:-$0}"
while [[ -h "$_src" ]]; do
    _dir="$(cd -P "$(dirname "$_src")" && pwd)"
    _src="$(readlink "$_src")"
    [[ "$_src" != /* ]] && _src="${_dir}/${_src}"
done
SCRIPT_DIR="$(cd -P "$(dirname "$_src")" && pwd)"
unset _src _dir

PROJECT_DIR="$(pwd)"
CONTAINER_NAME="SWO-$(basename "$PROJECT_DIR")-$$"

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

# Rebuild if image doesn't exist or Dockerfile is newer than image.
# -nt can't compare a file against a Docker timestamp string, so we extract
# epoch seconds from both sides and compare numerically.
NEEDS_BUILD=false
if ! docker image inspect "$IMAGE_NAME" &>/dev/null 2>&1; then
    NEEDS_BUILD=true
else
    DOCKERFILE_MTIME=$(stat -f %m "$DOCKERFILE")
    IMAGE_CREATED=$(docker image inspect "$IMAGE_NAME" --format '{{.Created}}' 2>/dev/null)
    # Strip fractional seconds and timezone suffix (handles both "…Z" and "….nnnZ" forms)
    IMAGE_MTIME=$(date -j -f "%Y-%m-%dT%H:%M:%S" "${IMAGE_CREATED%%[.Z]*}" "+%s" 2>/dev/null || echo 0)
    if [[ "$DOCKERFILE_MTIME" -gt "$IMAGE_MTIME" ]]; then
        NEEDS_BUILD=true
    fi
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
if [[ -n "${GH_TOKEN:-}" ]]; then
    # git push/pull will work here IF the PAT is scoped to allow it — but push/pull
    # from inside the orchard is discouraged; scope the fine-grained PAT without
    # "Contents: write" so it can't, and do push/pull outside the orchard instead.
    info "GH_TOKEN detected — git push/pull and 'gh' commands will work inside the orchard (push/pull discouraged; scope the PAT to prevent it)."
else
    warn "Remember: do git push/pull OUTSIDE the orchard! (or export GH_TOKEN to enable it inside)"
fi
echo ""

# The macOS tooling JDK can't run on Linux. We overlay it with a tmpfs
# that the entrypoint populates with Linux JDK symlinks.
AGENT_ENV=()
if [[ -n "${ANTHROPIC_API_KEY:-}" ]]; then
    AGENT_ENV+=(-e "ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}")
fi
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
    AGENT_ENV+=(-e "OPENAI_API_KEY=${OPENAI_API_KEY}")
fi

# Git identity: pulled from the host machine's global git config and set
# globally inside the orchard by the entrypoint. The container's $HOME is
# ephemeral (torn down with the container on exit — see --rm below), so
# without this, every session needs `git config --global user.name/email`
# set by hand before a commit (e.g. from VS Code's Source Control panel)
# will work. Not a secret, so plain -e is fine (unlike GH_TOKEN below).
HOST_GIT_USER_NAME=$(git config --global user.name 2>/dev/null || true)
HOST_GIT_USER_EMAIL=$(git config --global user.email 2>/dev/null || true)
if [[ -n "$HOST_GIT_USER_NAME" ]]; then
    AGENT_ENV+=(-e "ORCHARD_GIT_USER_NAME=${HOST_GIT_USER_NAME}")
fi
if [[ -n "$HOST_GIT_USER_EMAIL" ]]; then
    AGENT_ENV+=(-e "ORCHARD_GIT_USER_EMAIL=${HOST_GIT_USER_EMAIL}")
fi
if [[ -z "$HOST_GIT_USER_NAME" || -z "$HOST_GIT_USER_EMAIL" ]]; then
    warn "Host git identity (user.name/user.email) not fully set — commits inside the orchard may fail until you set it there."
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

# GitHub tokens: routed through the same env-file mechanism (never -e), so the
# raw values never appear in this `docker run` invocation's argv — which is
# visible via `ps` to other users on multi-user hosts.
#   GH_TOKEN     — fine-grained PAT. Used by `gh` CLI, git's credential helper,
#                  and PR read/write. Read directly from the environment by
#                  `gh` — never written to disk (see orchard-entry.sh). git
#                  push/pull via this token is discouraged; the PAT should be
#                  scoped without "Contents: write" so GitHub itself rejects
#                  push, rather than relying on this script to prevent it.
#   GITHUB_TOKEN — classic PAT. Used once, transiently, to install the
#                  @adzerk-scoped libjs-cli package from GitHub Packages,
#                  then unset for the rest of the session.
if [[ -n "${GH_TOKEN:-}" || -n "${GITHUB_TOKEN:-}" ]]; then
    [[ -z "$CREDS_ENV_FILE" ]] && CREDS_ENV_FILE=$(mktemp)
    [[ -n "${GH_TOKEN:-}" ]] && printf 'GH_TOKEN=%s\n' "${GH_TOKEN}" >> "$CREDS_ENV_FILE"
    [[ -n "${GITHUB_TOKEN:-}" ]] && printf 'GITHUB_TOKEN=%s\n' "${GITHUB_TOKEN}" >> "$CREDS_ENV_FILE"
    chmod 600 "$CREDS_ENV_FILE"
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

# Mount Codex auth.json to a staging location (read-only).
# The entrypoint copies it to ~/.codex/auth.json inside the container.
# Codex stores OAuth tokens in plaintext — no Keychain decryption needed.
CODEX_AUTH_MOUNT=()
if [[ -z "${OPENAI_API_KEY:-}" ]] && [[ -f "${HOME}/.codex/auth.json" ]]; then
    CODEX_AUTH_MOUNT=(-v "${HOME}/.codex/auth.json:/tmp/codex-host-auth.json:ro")
    info "Mounting Codex auth from ~/.codex/auth.json"
fi

# ── Per-project persistent Claude volume ─────────────────────────────────────
# Each PWD gets its own named volume so ~/.claude (chat history, settings) is
# preserved across container sessions and compartmentalized per project.
# Volume name: orchard-claude-<basename>-<8-char hash of full path>
_vol_suffix=$(printf '%s' "$PROJECT_DIR" | md5 -q | cut -c1-8)
CLAUDE_VOLUME="orchard-claude-$(basename "$PROJECT_DIR")-${_vol_suffix}"
unset _vol_suffix
if ! docker volume inspect "$CLAUDE_VOLUME" &>/dev/null 2>&1; then
    docker volume create "$CLAUDE_VOLUME" > /dev/null
    # Docker creates new volume mount points as root:root. Fix ownership so the
    # orchard user can write into it without needing elevated capabilities.
    docker run --rm \
        -v "${CLAUDE_VOLUME}:/home/orchard/.claude" \
        --user root \
        --entrypoint "" \
        "$IMAGE_NAME" \
        chown orchard:orchard /home/orchard/.claude
    info "Created persistent Claude volume: ${CLAUDE_VOLUME}"
else
    info "Using existing Claude volume: ${CLAUDE_VOLUME}"
fi

# Extra bind mounts injected by callers (e.g. orchardw.sh).
# ORCHARD_EXTRA_MOUNTS: newline-separated list of "host:container" pairs.
# Bare host paths (no colon) fall back to /repos/<basename>.
EXTRA_MOUNTS=()
EXTRA_CONTAINER_PATHS=()
if [[ -n "${ORCHARD_EXTRA_MOUNTS:-}" ]]; then
    while IFS= read -r _pair; do
        [[ -z "$_pair" ]] && continue
        _host="${_pair%%:*}"
        _container="${_pair#*:}"
        [[ "$_container" == "$_pair" ]] && _container="/repos/$(basename "$_host")"
        if [[ -d "$_host" ]]; then
            EXTRA_MOUNTS+=(-v "${_host}:${_container}")
            EXTRA_CONTAINER_PATHS+=("$_container")
        fi
    done <<< "$ORCHARD_EXTRA_MOUNTS"
fi

# Generate orchard.code-workspace when extra repos are mounted so VS Code
# opens all roots automatically via "Dev Containers: Attach to Running Container".
WORKSPACE_FILE="${PROJECT_DIR}/orchard.code-workspace"
if [[ ${#EXTRA_CONTAINER_PATHS[@]} -gt 0 ]]; then
    {
        printf '{\n  "folders": [\n    { "path": "/workspace" },\n    { "path": "/repos" }\n  ]\n}\n'
    } > "$WORKSPACE_FILE"
    info "Generated orchard.code-workspace with /repos root"
    # Keep the generated file out of git
    _GITIGNORE="${PROJECT_DIR}/.gitignore"
    if [[ -f "$_GITIGNORE" ]] && ! grep -qxF 'orchard.code-workspace' "$_GITIGNORE"; then
        echo 'orchard.code-workspace' >> "$_GITIGNORE"
        info "Added orchard.code-workspace to .gitignore"
    elif [[ ! -f "$_GITIGNORE" ]]; then
        echo 'orchard.code-workspace' > "$_GITIGNORE"
    fi
    unset _GITIGNORE
else
    [[ -f "$WORKSPACE_FILE" ]] && rm -f "$WORKSPACE_FILE"
fi
unset WORKSPACE_FILE

docker run \
    --rm \
    -it \
    --name "$CONTAINER_NAME" \
    --hostname orchard \
    -v "${PROJECT_DIR}:/workspace" \
    ${CLAUDE_CONFIG_MOUNT[@]+"${CLAUDE_CONFIG_MOUNT[@]}"} \
    ${CODEX_AUTH_MOUNT[@]+"${CODEX_AUTH_MOUNT[@]}"} \
    ${EXTRA_MOUNTS[@]+"${EXTRA_MOUNTS[@]}"} \
    -v "${CLAUDE_VOLUME}:/home/orchard/.claude" \
    ${CREDS_ENV_FILE:+--env-file "$CREDS_ENV_FILE"} \
    --tmpfs /workspace/tooling/jdk-21.0.7+6:exec,uid=1000,gid=1000 \
    --tmpfs /workspace/tooling/openjml:exec,uid=1000,gid=1000 \
    -w /workspace \
    --security-opt no-new-privileges \
    --cap-drop ALL \
    --cap-add DAC_OVERRIDE \
    --cap-add FOWNER \
    ${AGENT_ENV[@]+"${AGENT_ENV[@]}"} \
    -e "ORCHARD_PROJECT=$(basename "$PROJECT_DIR")" \
    -e 'PROMPT_COMMAND=PS1="(\[\033[1;32m\]\u@\h\[\033[0m\])[\[\033[1;34m\]${ORCHARD_PROJECT}\[\033[0m\]] \w\$ "' \
    "$IMAGE_NAME" \
    "${@:-bash}"
