#!/usr/bin/env bash
set -Eeuo pipefail

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$DOCKER_DIR/.." && pwd)"
cd "$ROOT_DIR"

COMPOSE_FILE="$DOCKER_DIR/docker-compose.yaml"
ENV_FILE="$DOCKER_DIR/.env"
[[ -f "$ENV_FILE" ]] || ENV_FILE="$DOCKER_DIR/.env.example"
POSTGRES_ENV_FILE="$DOCKER_DIR/postgres/postgres.env"

if ! command -v docker >/dev/null 2>&1; then
  echo 'Docker CLI is required to start the PostgreSQL container.' >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo 'Docker Compose v2 and a running Docker Engine are required.' >&2
  exit 1
fi

# Load port mappings and PostgreSQL credentials
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
# shellcheck disable=SC1090
source "$POSTGRES_ENV_FILE"
set +a

# Stop any full Compose stack first to free the host API port. Named volumes stay intact.
docker compose --env-file "$ENV_FILE" \
  --file "$COMPOSE_FILE" \
  --project-directory "$DOCKER_DIR" \
  down --remove-orphans

# Start only PostgreSQL. The API itself runs as a normal host JVM process.
docker compose --env-file "$ENV_FILE" \
  --file "$COMPOSE_FILE" \
  --project-directory "$DOCKER_DIR" \
  up --detach --wait postgres

export SERVER_PORT="${APP_PORT:-8181}"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${POSTGRES_PORT:-5434}/${POSTGRES_DB:-nutrition}"
export SPRING_DATASOURCE_USERNAME="${POSTGRES_USER:-nutrition}"
export SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD:-change_me_locally}"

echo "Starting the API on the host; PostgreSQL remains in Docker."
echo "API: http://localhost:${SERVER_PORT}/api/nutrition-entries"
echo 'Press Ctrl+C to stop the host application. The database container and its volume remain running.'
exec "$ROOT_DIR/gradlew" --no-daemon bootRun