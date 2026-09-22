#!/usr/bin/env python3
"""
Noctalia System & Atmospheric HUD — Windows 8 Live Tile Edition
High-density system hardware gauges + Open-Meteo weather integration.
Renders with a distinct solid-color tile background (#1c1f29) and bottom-left badge.
"""

import json
import os
import shutil
import sys
import time
import urllib.request
from collections import deque
from datetime import datetime
from typing import Deque, Dict, Tuple

import psutil
from rich.align import Align
from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.text import Text
from rich import box

# -------------------------------------------------------------------------
# Configuration & Palette (Matugen Surface / Live Tile Palette)
# -------------------------------------------------------------------------
CONFIG = {
    # Default coordinates: Chennai, Tamil Nadu (13.0827° N, 80.2707° E)
    "lat": 13.0827,
    "lon": 80.2707,
    "weather_cache_ttl": 600,  # 10 minutes
    "poll_interval": 1.0,      # 1 second refresh rate for hardware
}

# Distinct Tile Theme: Solid dark obsidian/navy surface
COLOR_TILE_BG = "#181b24"
COLOR_ACCENT = "#aac2ff"
COLOR_PRIMARY = "#edefff"
COLOR_SECONDARY = "#f1eeff"
COLOR_MINT = "#99cc66"
COLOR_AMBER = "#ffaea4"
COLOR_SUBTEXT = "#c0c2cf"
COLOR_CARD_BG = "#222530"

BRAILLE_CHARS = [" ", "⡀", "⣀", "⣄", "⣤", "⣦", "⣶", "⣷", "⣿"]

# -------------------------------------------------------------------------
# Telemetry History Ring Buffers
# -------------------------------------------------------------------------
CPU_HISTORY: Deque[float] = deque(maxlen=20)
RAM_HISTORY: Deque[float] = deque(maxlen=20)

WEATHER_CACHE: Dict[str, object] = {
    "last_fetch": 0.0,
    "text": f"[{COLOR_SUBTEXT}]Fetching weather...[/]",
}


def generate_sparkline(data: Deque[float], width: int = 14) -> str:
    """Renders a mini Braille trendline from historical floating points."""
    if not data:
        return ""
    pts = list(data)[-width:]
    min_v, max_v = min(pts), max(pts)
    span = max_v - min_v or 1.0
    res = []
    for val in pts:
        idx = int(((val - min_v) / span) * (len(BRAILLE_CHARS) - 1))
        res.append(BRAILLE_CHARS[idx])
    return "".join(res)


def format_progress_bar(pct: float, length: int = 10) -> str:
    """Generates a compact ANSI meter bar."""
    filled = int(round(length * (pct / 100.0)))
    filled = max(0, min(length, filled))
    bar = "━" * filled + "┄" * (length - filled)
    return f"[{COLOR_ACCENT}]{bar}[/]"


def fetch_weather_cached() -> Text:
    """Polls Open-Meteo API with in-memory TTL cache."""
    now = time.time()
    if now - float(WEATHER_CACHE["last_fetch"]) < CONFIG["weather_cache_ttl"]:
        return WEATHER_CACHE["cached_text"]  # type: ignore

    lat, lon = CONFIG["lat"], CONFIG["lon"]
    url = (
        f"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
        "weather_code,wind_speed_10m&timezone=auto"
    )

    try:
        req = urllib.request.Request(url, headers={"User-Agent": "NoctaliaHUD/2.0"})
        with urllib.request.urlopen(req, timeout=3.0) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            curr = data.get("current", {})
            temp = curr.get("temperature_2m", "--")
            app_temp = curr.get("apparent_temperature", "--")
            humidity = curr.get("relative_humidity_2m", "--")
            wind = curr.get("wind_speed_10m", "--")
            wcode = curr.get("weather_code", 0)

            # WMO Weather interpretation codes
            icon, desc = "☀️", "Clear"
            if wcode in (1, 2, 3):
                icon, desc = "⛅", "Partly Cloudy" if wcode < 3 else "Overcast"
            elif wcode in (45, 48):
                icon, desc = "🌫️", "Foggy"
            elif wcode in (51, 53, 55, 61, 63, 65):
                icon, desc = "🌧️", "Rain"
            elif wcode in (71, 73, 75):
                icon, desc = "🌨️", "Snow"
            elif wcode in (95, 96, 99):
                icon, desc = "⛈️", "Thunderstorm"

            t = Text()
            t.append(f"{icon} {temp}°C", style=f"bold {COLOR_PRIMARY}")
            t.append(f" ({desc})\n", style=COLOR_SUBTEXT)
            t.append(f"Feels: {app_temp}°C  💧 {humidity}%  💨 {wind} km/h", style=f"{COLOR_SUBTEXT}")

            WEATHER_CACHE["last_fetch"] = now
            WEATHER_CACHE["cached_text"] = t
            return t
    except Exception:
        fallback = Text("⛅ Weather Offline", style=COLOR_SUBTEXT)
        WEATHER_CACHE["cached_text"] = fallback
        return fallback


def get_hardware_telemetry() -> Tuple[float, float, str, str, str]:
    """Extracts CPU, RAM, Temp, Battery, and Uptime."""
    cpu_pct = psutil.cpu_percent(interval=None)
    mem = psutil.virtual_memory()
    CPU_HISTORY.append(cpu_pct)
    RAM_HISTORY.append(mem.percent)

    # Temperatures
    temp_str = "--°C"
    try:
        temps = psutil.sensors_temperatures()
        if "coretemp" in temps:
            for entry in temps["coretemp"]:
                if entry.label.startswith("Package") or "Package" in entry.label:
                    temp_str = f"{int(entry.current)}°C"
                    break
        elif temps:
            first_key = list(temps.keys())[0]
            temp_str = f"{int(temps[first_key][0].current)}°C"
    except Exception:
        temp_str = "--°C"

    # Battery & Power
    battery_str = "AC"
    try:
        bat = psutil.sensors_battery()
        if bat:
            icon = "⚡" if bat.power_plugged else "🔋"
            battery_str = f"{icon} {int(bat.percent)}%"
    except Exception:
        battery_str = "AC"

    uptime_sec = time.time() - psutil.boot_time()
    hrs = int(uptime_sec // 3600)
    mins = int((uptime_sec % 3600) // 60)
    uptime_str = f"{hrs}h {mins}m"

    return cpu_pct, mem.percent, temp_str, battery_str, uptime_str


# -------------------------------------------------------------------------
# UI Layout Builder (Solid Tile with Bottom-Left Badge)
# -------------------------------------------------------------------------
def render_tile(console: Console) -> None:
    cpu_pct, mem_pct, temp_val, bat_val, up_val = get_hardware_telemetry()
    weather_info = fetch_weather_cached()

    cpu_spark = generate_sparkline(CPU_HISTORY, width=8)
    ram_spark = generate_sparkline(RAM_HISTORY, width=8)

    # Vitals Row
    vitals_table = Table.grid(expand=True)
    vitals_table.add_column(justify="left")
    vitals_table.add_column(justify="right")
    vitals_table.add_row(
        Text(f"UP: {up_val}", style=f"bold {COLOR_ACCENT}"),
        Text(f"PWR: {bat_val}  PKG: {temp_val}", style=f"bold {COLOR_AMBER}")
    )

    # Hardware Meters
    hw_table = Table.grid(expand=True, padding=(0, 1))
    hw_table.add_column("Key", ratio=1)
    hw_table.add_column("Bar", ratio=3)
    hw_table.add_column("Val", justify="right", ratio=2)

    hw_table.add_row(
        Text("CPU", style=f"bold {COLOR_PRIMARY}"),
        format_progress_bar(cpu_pct, length=8),
        Text(f"{cpu_pct:>4.0f}% {cpu_spark}", style=COLOR_MINT)
    )
    
    mem_used_gb = psutil.virtual_memory().used / (1024**3)
    hw_table.add_row(
        Text("RAM", style=f"bold {COLOR_PRIMARY}"),
        format_progress_bar(mem_pct, length=8),
        Text(f"{mem_used_gb:>4.1f}G {ram_spark}", style=COLOR_ACCENT)
    )

    # Weather Box
    weather_box = Panel(
        Align.left(weather_info),
        border_style=COLOR_CARD_BG,
        style=f"on {COLOR_CARD_BG}",
        box=box.ROUNDED,
        padding=(0, 1),
    )

    # Tile Body
    body = Table.grid(expand=True, padding=(0, 0))
    body.add_column()
    body.add_row(vitals_table)
    body.add_row(hw_table)
    body.add_row(weather_box)
    body.add_row(Text("⏱️ HUD", style=f"bold {COLOR_ACCENT}"))

    # Outer Tile Panel (Solid Color Block)
    tile = Panel(
        body,
        style=f"on {COLOR_TILE_BG}",
        border_style=COLOR_TILE_BG,
        box=box.SIMPLE,
        padding=(0, 1),
    )

    # Clear terminal with tile background and paint rows
    term_rows = shutil.get_terminal_size((40, 24)).lines
    sys.stdout.write("\033[48;2;24;27;36m\033[2J\033[H")
    console.print(tile)
    # Ensure remaining screen lines have the background color
    term_cols = shutil.get_terminal_size((40, 24)).columns
    bg_fill = "\033[48;2;24;27;36m" + " " * term_cols
    sys.stdout.write("\033[H")
    for _ in range(term_rows):
        sys.stdout.write(f"{bg_fill}\n")
    sys.stdout.write("\033[H")
    console.print(tile)
    sys.stdout.flush()


def main() -> None:
    console = Console(color_system="truecolor", highlight=False)
    psutil.cpu_percent(interval=None)

    # Hide cursor
    sys.stdout.write("\033[?25l")
    try:
        while True:
            render_tile(console)
            time.sleep(CONFIG["poll_interval"])
    except KeyboardInterrupt:
        pass
    finally:
        sys.stdout.write("\033[?25h\033[0m\033[2J\033[H")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
