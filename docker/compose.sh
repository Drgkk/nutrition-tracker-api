#!/usr/bin/env bash
set -Eeuo pipefail

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DOCKER_DIR/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="$DOCKER_DIR/docker-compose.yaml"
ENV_FILE="$DOCKER_DIR/.env"
[[ -f "$ENV_FILE" ]] || ENV_FILE="$DOCKER_DIR/.env.example"

compose() {
  docker compose --env-file "$ENV_FILE" \
    --file "$COMPOSE_FILE" \
    --project-directory "$DOCKER_DIR" \
    "$@"
}

read_env() {
  local key="$1" default="$2" value
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$ENV_FILE")"
  printf '%s' "${value:-$default}"
}

require_docker() {
  command -v docker >/dev/null 2>&1 || { echo 'Docker CLI is required.' >&2; exit 1; }
  docker compose version >/dev/null 2>&1 || { echo 'Docker Compose v2 is required.' >&2; exit 1; }
  docker info >/dev/null 2>&1 || { echo 'Docker Engine is not running or is not accessible.' >&2; exit 1; }
}

usage() {
  cat <<'HELP'
Usage:
  ./docker/compose.sh up [--build] [--skip-tests]  Start the stack (optionally rebuild images)
  ./docker/compose.sh down [--volumes]             Stop the stack (optionally delete data)
  ./docker/compose.sh build [--skip-tests]         Run checks and build service images
  ./docker/compose.sh ps                           Show service status
  ./docker/compose.sh logs [service]               Follow container logs
  ./docker/compose.sh exec SERVICE CMD             Run a command in a running service

Examples:
  ./docker/compose.sh up
  ./docker/compose.sh up --build
  ./docker/compose.sh up --build --skip-tests
  ./docker/compose.sh exec app bash
  ./docker/compose.sh exec postgres bash
  ./docker/compose.sh down --volumes
HELP
}

ACTION="${1:-up}"
shift || true

case "$ACTION" in
  up)
    require_docker
    DO_BUILD=false
    SKIP_TESTS=false
    for arg in "$@"; do
      case "$arg" in
        --build) DO_BUILD=true ;;
        --skip-tests) SKIP_TESTS=true ;;
        *)
          echo "Unknown option for 'up': $arg" >&2
          usage >&2
          exit 2
          ;;
      esac
    done

    COMPOSE_UP_ARGS=(--detach --wait)
    if [[ "$DO_BUILD" == true ]]; then
      if [[ "$SKIP_TESTS" == false ]]; then
        "$ROOT_DIR/gradlew" --no-daemon clean check
      else
        echo 'Skipping host-side tests. Rebuilding Docker images...'
      fi
      COMPOSE_UP_ARGS+=(--build)
    fi

    compose up "${COMPOSE_UP_ARGS[@]}"
    APP_PORT="$(read_env APP_PORT 8181)"
    PGADMIN_PORT="$(read_env PGADMIN_PORT 5435)"
    echo
    echo "Nutrition API:  http://localhost:${APP_PORT}/api/nutrition-entries"
    echo "Health:         http://localhost:${APP_PORT}/actuator/health"
    echo "pgAdmin:        http://localhost:${PGADMIN_PORT}"
    ;;
  down)
    require_docker
    compose down "$@"
    ;;
  build)
    require_docker
    SKIP_TESTS=false
    for arg in "$@"; do
      [[ "$arg" == "--skip-tests" ]] && SKIP_TESTS=true
    done
    if [[ "$SKIP_TESTS" == false ]]; then
      "$ROOT_DIR/gradlew" --no-daemon clean check
    fi
    compose build
    ;;
  ps)
    require_docker
    compose ps
    ;;
  logs)
    require_docker
    compose logs --follow "$@"
    ;;
  exec)
    require_docker
    if [[ $# -lt 2 ]]; then
      usage
      exit 2
    fi
    compose exec "$@"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac