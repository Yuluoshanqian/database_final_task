#!/usr/bin/env bash
set -Eeuo pipefail

db_host="${DB_HOST:-localhost}"
db_port="${DB_PORT:-3306}"
db_name="${DB_NAME:-test}"
db_user="${DB_USER:-root}"
db_password="${DB_PASSWORD:-}"
backup_dir="${BACKUP_DIR:-backups}"
retain_count="${BACKUP_RETAIN_COUNT:-10}"
trigger_type="scheduled"
actor_user_id=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --db-host|-DbHost)
      db_host="$2"
      shift 2
      ;;
    --db-port|-DbPort)
      db_port="$2"
      shift 2
      ;;
    --db-name|-DbName)
      db_name="$2"
      shift 2
      ;;
    --db-user|-DbUser)
      db_user="$2"
      shift 2
      ;;
    --db-password|-DbPassword)
      db_password="$2"
      shift 2
      ;;
    --backup-dir|-BackupDir)
      backup_dir="$2"
      shift 2
      ;;
    --retain-count|-RetainCount)
      retain_count="$2"
      shift 2
      ;;
    --trigger-type|-TriggerType)
      trigger_type="$2"
      shift 2
      ;;
    --actor-user-id|-ActorUserId)
      actor_user_id="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if ! [[ "$retain_count" =~ ^[0-9]+$ ]] || [[ "$retain_count" -lt 1 ]]; then
  retain_count=10
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql client was not found in PATH." >&2
  exit 1
fi

if ! command -v mysqldump >/dev/null 2>&1; then
  echo "mysqldump was not found in PATH." >&2
  exit 1
fi

sql_value() {
  if [[ $# -eq 0 || -z "${1+x}" ]]; then
    printf 'NULL'
    return
  fi
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\'/\'\'}"
  value="${value//$'\r'/ }"
  value="${value//$'\n'/ }"
  printf "'%s'" "$value"
}

mysql_exec() {
  local sql="$1"
  mysql \
    --defaults-extra-file="$defaults_file" \
    --batch \
    --raw \
    --skip-column-names \
    --database="$db_name" \
    --execute="$sql"
}

update_backup_record() {
  local record_id="$1"
  local status="$2"
  local file_size="${3:-}"
  local message="${4:-}"
  if [[ -z "$record_id" ]]; then
    return
  fi

  local file_size_sql="file_size_bytes = NULL"
  if [[ -n "$file_size" ]]; then
    file_size_sql="file_size_bytes = $file_size"
  fi

  mysql_exec "
UPDATE backup_records
   SET status = $(sql_value "$status"),
       ended_at = CURRENT_TIMESTAMP,
       $file_size_sql,
       message = $(sql_value "$message")
 WHERE id = $record_id
" >/dev/null
}

remove_old_backups() {
  local rows
  rows="$(mysql_exec "
SELECT id, backup_directory, file_name
  FROM backup_records
 WHERE database_name = $(sql_value "$db_name")
   AND status = 'success'
 ORDER BY started_at DESC, id DESC
 LIMIT $retain_count, 18446744073709551615
")"
  if [[ -z "$rows" ]]; then
    return
  fi

  while IFS=$'\t' read -r old_record_id old_dir old_file; do
    if [[ -z "${old_record_id:-}" || -z "${old_dir:-}" || -z "${old_file:-}" ]]; then
      continue
    fi
    old_path="${old_dir%/}/$old_file"
    if [[ -f "$old_path" ]]; then
      rm -f -- "$old_path"
    fi
    mysql_exec "
UPDATE backup_records
   SET status = 'deleted',
       message = $(sql_value "Pruned by retention policy; keeping latest $retain_count backup files.")
 WHERE id = $old_record_id
" >/dev/null
  done <<< "$rows"
}

mkdir -p -- "$backup_dir"
backup_dir="$(cd "$backup_dir" && pwd)"
timestamp="$(date '+%Y%m%d_%H%M%S')"
file_name="${db_name}_${timestamp}.sql"
backup_path="${backup_dir}/${file_name}"
defaults_file="$(mktemp "${TMPDIR:-/tmp}/mysql-client.XXXXXX.cnf")"
record_id=""

cleanup() {
  rm -f -- "$defaults_file"
}
trap cleanup EXIT

cat > "$defaults_file" <<EOF
[client]
host=$db_host
port=$db_port
user=$db_user
password=$db_password
default-character-set=utf8mb4
EOF
chmod 600 "$defaults_file"

actor_sql="NULL"
if [[ -n "$actor_user_id" ]]; then
  actor_sql="$actor_user_id"
fi

insert_sql="
INSERT INTO backup_records(database_name, file_name, backup_directory, status, trigger_type, created_by, started_at, message)
VALUES($(sql_value "$db_name"), $(sql_value "$file_name"), $(sql_value "$backup_dir"), 'started', $(sql_value "$trigger_type"), $actor_sql, CURRENT_TIMESTAMP, 'mysqldump started');
SELECT LAST_INSERT_ID();
"
record_id="$(mysql_exec "$insert_sql" | tail -n 1 | tr -d '\r')"

if ! dump_output="$(mysqldump \
    --defaults-extra-file="$defaults_file" \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    --default-character-set=utf8mb4 \
    --databases "$db_name" \
    --result-file="$backup_path" 2>&1)"; then
  update_backup_record "$record_id" "failed" "" "$dump_output" || true
  rm -f -- "$backup_path"
  echo "$dump_output" >&2
  exit 1
fi

file_size="$(wc -c < "$backup_path" | tr -d '[:space:]')"
update_backup_record "$record_id" "success" "$file_size" "Backup completed."
remove_old_backups
echo "Backup completed: $backup_path"
