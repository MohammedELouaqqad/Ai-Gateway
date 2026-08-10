#!/bin/bash
# Crée le rôle + la base `keycloak` si absents.
# - Monté dans docker-entrypoint-initdb.d (1er boot volume)
# - Relancé par le service keycloak-db-init (volumes déjà existants)
set -euo pipefail

POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_DB="${POSTGRES_DB:-Ai_Gateway_DB}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
KEYCLOAK_DB_PASSWORD="${KEYCLOAK_DB_PASSWORD:-keycloak}"

psql_base=(psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER")
if [ -n "${PGHOST:-}" ]; then
  export PGPASSWORD="$POSTGRES_PASSWORD"
  psql_base+=(--host "$PGHOST")
fi

"${psql_base[@]}" --dbname "$POSTGRES_DB" <<-EOSQL
  DO \$\$
  BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'keycloak') THEN
      CREATE ROLE keycloak LOGIN PASSWORD '$KEYCLOAK_DB_PASSWORD';
    ELSE
      ALTER ROLE keycloak PASSWORD '$KEYCLOAK_DB_PASSWORD';
    END IF;
  END
  \$\$;

  SELECT 'CREATE DATABASE keycloak OWNER keycloak'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

  GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
EOSQL

"${psql_base[@]}" --dbname keycloak <<-EOSQL
  GRANT ALL ON SCHEMA public TO keycloak;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO keycloak;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO keycloak;
EOSQL

echo "Postgres ready: databases Ai_Gateway_DB + keycloak"
