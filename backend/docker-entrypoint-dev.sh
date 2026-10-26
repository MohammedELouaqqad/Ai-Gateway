#!/bin/bash
# Dev entrypoint : recompile src/ on change + Spring Boot DevTools restart.
set -euo pipefail

cd /app

echo "[backend-dev] Initial compile…"
./mvnw -q -DskipTests compile

# Background: poll Java sources and recompile (Docker FS events are unreliable)
(
  checksum=""
  while true; do
    new=$(find src -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yml' -o -name '*.properties' \) \
      -print0 2>/dev/null | sort -z | xargs -0 cat 2>/dev/null | md5sum | awk '{print $1}')
    if [ -n "$new" ] && [ "$new" != "$checksum" ]; then
      if [ -n "$checksum" ]; then
        echo "[backend-dev] Source change detected → compile…"
        ./mvnw -o -q -DskipTests compile && touch target/classes/.reloadtrigger || true
      fi
      checksum=$new
    fi
    sleep 2
  done
) &

echo "[backend-dev] Starting Spring Boot (devtools restart enabled)…"
exec ./mvnw -q spring-boot:run \
  -Dspring-boot.run.profiles="${SPRING_PROFILES_ACTIVE:-dev}" \
  -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=true -Dspring.devtools.restart.poll-interval=2s -Dspring.devtools.restart.quiet-period=1s"
