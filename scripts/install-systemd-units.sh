#!/usr/bin/env bash
set -euo pipefail

SYSTEMD_DIR="${HOME}/.config/systemd/user"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mkdir -p "${SYSTEMD_DIR}"

echo "[*] Installing Portfolio OS systemd user units..."
ln -sf "${REPO_DIR}/systemd/portfolio-os-quant.service" "${SYSTEMD_DIR}/portfolio-os-quant.service"
ln -sf "${REPO_DIR}/systemd/portfolio-os-core.service" "${SYSTEMD_DIR}/portfolio-os-core.service"
ln -sf "${REPO_DIR}/systemd/portfolio-os.target" "${SYSTEMD_DIR}/portfolio-os.target"
ln -sf "${REPO_DIR}/systemd/portfolio-pc-sync.service" "${SYSTEMD_DIR}/portfolio-pc-sync.service"
ln -sf "${REPO_DIR}/systemd/portfolio-pc-sync.timer" "${SYSTEMD_DIR}/portfolio-pc-sync.timer"

systemctl --user daemon-reload
systemctl --user enable portfolio-pc-sync.timer
echo "[+] Units installed, timer enabled, and daemon reloaded successfully."
