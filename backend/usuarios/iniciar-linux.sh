#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if [ ! -f .env ]; then
  echo "Falta .env: copia .env.example a .env y configura las credenciales" >&2
  exit 1
fi
set -a
. ./.env
set +a
exec ./mvnw spring-boot:run
