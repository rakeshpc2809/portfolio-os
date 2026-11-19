#!/usr/bin/env python3
"""On-demand CLI for parsing CAS PDFs and broker CSV statements into structured TaxEvent JSON.

Reads the file from --file <path>.
Reads the statement password from standard input (stdin) or the CAS_PDF_PASSWORD environment variable.
Never accepts passwords as CLI arguments to prevent leaking credentials in /proc and process lists.
Outputs JSON array of TaxEventSchema objects to stdout.
"""

import argparse
import json
import os
import sys
from pathlib import Path

# Add current directory to path so relative imports work when executed as a script
sys.path.insert(0, str(Path(__file__).resolve().parent))

from parsers.broker_csv_parser import BrokerCsvParser
from parsers.cas_parser import CasPdfParser
from parsers.sip_detector import detect_and_tag_sips


def get_password() -> str | None:
    # 1. First check environment variable
    env_pwd = os.environ.get("CAS_PDF_PASSWORD")
    if env_pwd:
        return env_pwd.strip()

    # 2. Check if stdin is piped
    if not sys.stdin.isatty():
        stdin_content = sys.stdin.read().strip()
        if stdin_content:
            return stdin_content

    return None


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Parse CAS PDF or broker CSV statement to TaxEvent JSON."
    )
    parser.add_argument(
        "--file",
        required=True,
        help="Path to the statement file (.pdf or .csv)",
    )
    args = parser.parse_args()

    file_path = Path(args.file)
    if not file_path.exists():
        sys.stderr.write(f"ERROR: Statement file does not exist: {file_path}\n")
        sys.exit(1)

    ext = file_path.suffix.lower()
    if ext not in (".pdf", ".csv"):
        sys.stderr.write(f"ERROR: Unsupported file format '{ext}'. Expected .pdf or .csv\n")
        sys.exit(1)

    password = get_password()

    try:
        if ext == ".pdf":
            cas_parser = CasPdfParser(str(file_path), password=password)
            raw_events = (
                cas_parser.parse_events()
                if hasattr(cas_parser, "parse_events")
                else cas_parser.parse()
            )
        else:
            broker_parser = BrokerCsvParser(str(file_path))
            raw_events = broker_parser.parse()

        tagged_events = detect_and_tag_sips(raw_events)

        # Output JSON array to stdout
        event_dicts = [e.model_dump(by_alias=True, mode="json") for e in tagged_events]
        json.dump(event_dicts, sys.stdout)
        sys.stdout.flush()

    except Exception as e:
        sys.stderr.write(f"ERROR: Failed to parse statement: {e}\n")
        sys.exit(2)


if __name__ == "__main__":
    main()
