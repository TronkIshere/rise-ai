#!/usr/bin/env bash
set -euo pipefail

: "${RISE_DB_ROOT_PASSWORD:?set RISE_DB_ROOT_PASSWORD}"
backup="${1:?usage: restore-mysql.sh <backup.sql.gz> [target-database]}"
target="${2:-rise_ai_restore_check}"
container="${RISE_MYSQL_CONTAINER:-rise-ai-mysql}"
source_database="${RISE_DB_NAME:-rise_ai}"

if [[ "${target}" == "${source_database}" && "${RISE_CONFIRM_OVERWRITE:-}" != "yes" ]]; then
  echo "refusing to overwrite ${source_database}; restore into another database or set RISE_CONFIRM_OVERWRITE=yes" >&2
  exit 2
fi

if [[ -f "${backup}.sha256" ]]; then
  sha256sum -c "${backup}.sha256"
fi
gzip -t "${backup}"

mysql_exec() {
  docker exec -i -e MYSQL_PWD="${RISE_DB_ROOT_PASSWORD}" "${container}" mysql -uroot "$@"
}

mysql_exec -e "DROP DATABASE IF EXISTS \`${target}\`; CREATE DATABASE \`${target}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gunzip -c "${backup}" | mysql_exec "${target}"

echo "restored ${backup} into ${target}"
mysql_exec -N -e "SELECT 'tables', COUNT(*) FROM information_schema.tables WHERE table_schema='${target}'; SELECT 'flyway_versions', COUNT(*) FROM \`${target}\`.flyway_schema_history WHERE success=1;"
