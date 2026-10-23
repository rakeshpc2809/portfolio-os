import re
import uuid
from decimal import Decimal
from typing import List, Optional
from datetime import datetime, date
from .models import TaxEventSchema, EventType

DATE_REGEX = re.compile(r"^(\d{2}-[A-Za-z]{3}-\d{4})\s+(.+)$")
# Added support for both CAMS and KFintech PAN formats in CAS
ISIN_REGEX = re.compile(r"ISIN:\s*([A-Z0-9]{12})", re.IGNORECASE)
FOLIO_REGEX = re.compile(r"Folio(?:\s+No)?[:\s]+([0-9a-zA-Z\/\-]+)", re.IGNORECASE)
CLOSING_BAL_REGEX = re.compile(r"Closing(?:\s+Unit)?\s+Balance[:\s]+([0-9,]+(?:\.[0-9]+)?)", re.IGNORECASE)
TOKEN_REGEX = re.compile(r"\((?:\d{1,3}(?:,\d{3})*|\d+)(?:\.\d+)?\)|\b\d{1,3}(?:,\d{3})*(?:\.\d+)?\b|\b\d+\.\d+\b")

class CasPdfParser:
    def __init__(self, pdf_path: str, password: Optional[str] = None):
        self.pdf_path = pdf_path
        self.password = password

    def parse_events(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        if not self.pdf_path:
            return events

        # Try specialized casparser library first
        try:
            import casparser
            data = casparser.read_cas_pdf(self.pdf_path, self.password or "")
            for folio in data.folios:
                folio_no = str(folio.folio).strip() if (hasattr(folio, "folio") and folio.folio) else None
                for scheme in folio.schemes:
                    isin = scheme.isin
                    scheme_name = scheme.scheme
                    asset_id = isin or scheme_name.replace(" ", "_").upper()[:20]
                    closing_units = None
                    if hasattr(scheme, "close") and scheme.close is not None:
                        try:
                            closing_units = Decimal(str(scheme.close))
                        except Exception:
                            closing_units = None
                    elif hasattr(scheme, "valuation") and hasattr(scheme.valuation, "units") and scheme.valuation.units is not None:
                        try:
                            closing_units = Decimal(str(scheme.valuation.units))
                        except Exception:
                            closing_units = None

                    for txn in scheme.transactions:
                        txn_type_str = str(txn.type).upper()
                        if any(x in txn_type_str for x in ["REDEMPTION", "SWITCH_OUT", "SWITCH OUT", "SELL", "STP_OUT", "STP OUT", "SWP", "SYSTEMATIC WITHDRAWAL"]):
                            event_type = EventType.DISPOSAL
                        elif "BONUS" in txn_type_str:
                            event_type = EventType.BONUS
                        elif "SPLIT" in txn_type_str:
                            event_type = EventType.SPLIT
                        else:
                            event_type = EventType.ACQUISITION

                        if not isinstance(txn.date, date):
                            raise ValueError(f"Unparseable transaction date encountered in scheme: {scheme_name}")
                        txn_date = txn.date

                        units = Decimal(str(abs(txn.units or 0)))
                        price = Decimal(str(abs(txn.nav or 0)))
                        amount = Decimal(str(abs(txn.amount or 0)))
                        if amount == Decimal("0") and units > 0 and price > 0:
                            amount = units * price

                        if units > Decimal("0"):
                            events.append(
                                TaxEventSchema(
                                    id=str(uuid.uuid4()),
                                    assetId=asset_id,
                                    assetName=scheme_name,
                                    isin=isin,
                                    folioNo=folio_no,
                                    eventType=event_type,
                                    eventDate=txn_date,
                                    units=units,
                                    pricePerUnit=price,
                                    grossAmount=amount,
                                    sourceDocumentId=self.pdf_path,
                                    ingestedAt=datetime.now(),
                                    declaredClosingUnits=closing_units
                                )
                            )
            if events:
                return events
        except ValueError:
            raise
        except Exception as e:
            print(f"casparser notice: {e}, falling back to custom line parser.")

        # Fallback to pdfplumber regex line parser
        try:
            import pdfplumber

            current_scheme = "Mutual Fund Scheme"
            current_isin: Optional[str] = None
            current_folio: Optional[str] = None
            current_closing_units: Optional[Decimal] = None

            with pdfplumber.open(self.pdf_path, password=self.password or "") as pdf:
                for page in pdf.pages:
                    text = page.extract_text() or ""
                    for line in text.splitlines():
                        line_str = line.strip()
                        if not line_str:
                            continue

                        folio_match = FOLIO_REGEX.search(line_str)
                        if folio_match:
                            current_folio = folio_match.group(1).strip()

                        isin_match = ISIN_REGEX.search(line_str)
                        if isin_match:
                            current_isin = isin_match.group(1)

                        if "ISIN:" in line_str or ("Fund" in line_str and "Registrar" in line_str):
                            current_scheme = line_str.split(" - ISIN:")[0].split("(Advisor")[0].strip()

                        close_match = CLOSING_BAL_REGEX.search(line_str)
                        if close_match:
                            try:
                                current_closing_units = Decimal(close_match.group(1).replace(",", "").strip())
                                for ev in reversed(events):
                                    if ev.isin == current_isin or ev.asset_name == current_scheme:
                                        if ev.declared_closing_units is None:
                                            ev.declared_closing_units = current_closing_units
                                    else:
                                        break
                            except Exception:
                                pass
                            continue

                        if any(
                            x in line_str
                            for x in [
                                "*** Stamp Duty ***",
                                "*** STT Paid ***",
                                "***Cancelled***",
                                "***Address Updated",
                                "Opening Unit Balance",
                                "CAMSCASWS",
                                "Consolidated Account Statement",
                                "NAV on",
                            ]
                        ):
                            continue

                        match = DATE_REGEX.match(line_str)
                        if match:
                            date_str, rest = match.groups()
                            try:
                                event_date = datetime.strptime(date_str, "%d-%b-%Y").date()
                            except ValueError as e:
                                raise ValueError(f"CRITICAL: Failed to parse date string '{date_str}' in CAS fallback parser. Raw line: {line_str}") from e

                            num_tokens = TOKEN_REGEX.findall(rest)

                            clean_nums = []
                            for tok in num_tokens:
                                is_neg = tok.startswith("(") and tok.endswith(")")
                                raw_val = tok.replace("(", "").replace(")", "").replace(",", "").strip()
                                try:
                                    val = Decimal(raw_val)
                                    if is_neg:
                                        val = -val
                                    clean_nums.append(val)
                                except Exception:
                                    pass

                            if len(clean_nums) >= 3:
                                amount = abs(clean_nums[0])
                                units = abs(clean_nums[1])
                                price = clean_nums[2]

                                line_upper = rest.upper()
                                if any(x in line_upper for x in ["REDEMPTION", "SWITCH OUT", "SWITCH_OUT", "SELL", "STP OUT", "STP_OUT", "SWP", "SYSTEMATIC WITHDRAWAL"]):
                                    event_type = EventType.DISPOSAL
                                elif "BONUS" in line_upper:
                                    event_type = EventType.BONUS
                                elif "SPLIT" in line_upper:
                                    event_type = EventType.SPLIT
                                else:
                                    event_type = EventType.ACQUISITION

                                events.append(
                                    TaxEventSchema(
                                        id=str(uuid.uuid4()),
                                        assetId=current_isin or current_scheme.replace(" ", "_").upper()[:20],
                                        assetName=current_scheme,
                                        isin=current_isin,
                                        folioNo=current_folio,
                                        eventType=event_type,
                                        eventDate=event_date,
                                        units=units,
                                        pricePerUnit=price,
                                        grossAmount=amount,
                                        sourceDocumentId=self.pdf_path,
                                        ingestedAt=datetime.now(),
                                        declaredClosingUnits=current_closing_units
                                    )
                                )
        except Exception as err:
            print(f"Fallback parser error: {err}")

        from .sip_detector import detect_and_tag_sips
        return detect_and_tag_sips(events)
