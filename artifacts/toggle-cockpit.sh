#!/usr/bin/env bash
export WAYLAND_DISPLAY="${WAYLAND_DISPLAY:-wayland-1}"
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/1000}"

# Check directly via Niri IPC if the cockpit window is currently open
dashboard_id=$(niri msg windows 2>/dev/null | grep -B 2 'ghostty-dashboard' | grep 'Window ID' | awk '{print $3}' | tr -d ':' | tail -n 1)

if [ -n "$dashboard_id" ]; then
    # Window is already open -> Switch to Workspace 1, focus, and ensure fullscreen
    niri msg action focus-workspace 1
    niri msg action focus-window --id "$dashboard_id"
    niri msg action fullscreen-window
else
    # Ghostty was closed -> Clean up any stale/orphaned zellij session to prevent dead/zombie re-attachment
    zellij delete-session dashboard --force >/dev/null 2>&1 || true
    
    # Launch fresh cockpit Ghostty with class and crash-retry resilience
    (
        max_retries=5
        retry=0
        while [ $retry -lt $max_retries ]; do
            ghostty --class="cockpit-dashboard" --title=ghostty-dashboard \
                -e fish -c "zellij attach dashboard 2>/dev/null || zellij --new-session-with-layout dashboard --session dashboard"
            
            # If exited cleanly, stop retrying
            if [ $? -eq 0 ]; then
                break
            fi
            
            retry=$((retry + 1))
            sleep 1
        done
    ) >/dev/null 2>&1 &
    
    # Wait and latch to Workspace 1 in fullscreen
    (
        for i in {1..15}; do
            sleep 0.15
            win_id=$(niri msg windows 2>/dev/null | grep -B 2 'ghostty-dashboard' | grep 'Window ID' | awk '{print $3}' | tr -d ':' | tail -n 1)
            if [ -n "$win_id" ]; then
                niri msg action focus-workspace 1
                niri msg action focus-window --id "$win_id"
                niri msg action fullscreen-window
                break
            fi
        done
    ) >/dev/null 2>&1 &
fi
