#!/usr/bin/env bash
set -euo pipefail

: "${RISE_DB_ROOT_PASSWORD:?set RISE_DB_ROOT_PASSWORD}"
container="${RISE_MYSQL_CONTAINER:-rise-ai-mysql}"
database="${RISE_DB_NAME:-rise_ai}"
target_dir="${1:-./backups}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="${target_dir}/${database}-${stamp}.sql.gz"

mkdir -p "${target_dir}"
docker exec -e MYSQL_PWD="${RISE_DB_ROOT_PASSWORD}" "${container}" \
  mysqldump -uroot --single-transaction --routines --triggers --events --set-gtid-purged=OFF "${database}" \
  | gzip -9 > "${file}"

gzip -t "${file}"
sha256sum "${file}" > "${file}.sha256"
echo "${file}"
