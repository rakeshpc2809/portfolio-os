#!/usr/bin/env bash
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${REPO_DIR}/data/backups"
DB_PATH="${REPO_DIR}/data/tax_ledger.db"

mkdir -p "${BACKUP_DIR}"

if [[ ! -f "${DB_PATH}" ]]; then
    echo "[-] Ledger database not found at ${DB_PATH}"
    exit 0
fi

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
DEST_DB="${BACKUP_DIR}/tax_ledger_${TIMESTAMP}.db"

echo "[*] Creating atomic SQLite backup..."
sqlite3 "${DB_PATH}" "VACUUM INTO '${DEST_DB}'"

if [[ -n "${LEDGER_BACKUP_ENCRYPT:-}" && -n "${API_AUTH_TOKEN:-}" ]]; then
    echo "[*] Encrypting backup with openssl enc (AES-256-CBC)..."
    openssl enc -aes-256-cbc -salt -pbkdf2 -iter 100000 -in "${DEST_DB}" -out "${DEST_DB}.enc" -pass env:API_AUTH_TOKEN
    rm -f "${DEST_DB}"
    gzip -9 "${DEST_DB}.enc"
    echo "[+] Encrypted backup created: ${DEST_DB}.enc.gz"
else
    gzip -9 "${DEST_DB}"
    echo "[+] Compressed backup created: ${DEST_DB}.gz"
fi

# Retention: Prune backups older than 30 days
find "${BACKUP_DIR}" -type f \( -name "tax_ledger_*.gz" -o -name "tax_ledger_*.db" \) -mtime +30 -delete
echo "[+] Backup complete."
