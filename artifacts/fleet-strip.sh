#!/usr/bin/env bash
# fleet-strip.sh — Windows 8 Solid Live Tile Fleet Strip
# Solid Matugen container background (#222530) with bottom-left badge

BG="\033[48;2;34;37;48m"
FG="\033[38;2;255;255;255m"
RESET="\033[0m"

services=("portfolio-os-core" "portfolio-os-quant" "ollama" "power-monitor")
labels=("core" "quant" "ollama" "power")

render() {
    # Clear screen with solid tile background color
    printf "${BG}\033[2J\033[H"
    
    # Status line
    printf "${BG}  "
    for i in "${!services[@]}"; do
        s="${services[$i]}"
        lbl="${labels[$i]}"
        if systemctl --user is-active --quiet "$s" 2>/dev/null; then
            dot="\033[1;32m●\033[0m${BG}"
        else
            dot="\033[1;31m●\033[0m${BG}"
        fi
        printf "%b \033[1;37m%s\033[0m${BG}   " "$dot" "$lbl"
    done
    printf "\n"
    
    # Windows 8 Bottom-Left Tile Badge
    printf "${BG}  \033[1;38;2;170;194;255m🛡️ FLEET\033[0m${BG}\n"
    # Paint any remaining lines in the pane with background color
    lines=$(tput lines 2>/dev/null || echo 10)
    for ((l=0; l<lines; l++)); do
        printf "${BG}\033[K\n"
    done
    printf "${RESET}"
}

if [ "$1" == "--once" ]; then
    render
    exit 0
fi

# Hide cursor
printf "\033[?25l"
trap 'printf "\033[?25h\033[0m\033[2J\033[H"; exit 0' SIGINT SIGTERM EXIT

while true; do
    render
    sleep 2
done
