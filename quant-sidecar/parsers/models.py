from enum import Enum
from datetime import date, datetime
from decimal import Decimal
from typing import Optional
from pydantic import BaseModel, Field

class EventType(str, Enum):
    ACQUISITION = "ACQUISITION"
    SIP_INSTALMENT = "SIP_INSTALMENT"
    DISPOSAL = "DISPOSAL"
    BONUS = "BONUS"
    SPLIT = "SPLIT"
    DIVIDEND_REINVEST = "DIVIDEND_REINVEST"
    SGB_INTEREST = "SGB_INTEREST"
    SGB_MATURITY = "SGB_MATURITY"
    MERGER = "MERGER"

class TaxEventSchema(BaseModel):
    id: str
    asset_id: str = Field(..., alias="assetId")
    asset_name: str = Field(..., alias="assetName")
    isin: Optional[str] = None
    event_type: EventType = Field(..., alias="eventType")
    event_date: date = Field(..., alias="eventDate")
    units: Decimal
    price_per_unit: Decimal = Field(..., alias="pricePerUnit")
    gross_amount: Decimal = Field(..., alias="grossAmount")
    source_document_id: str = Field(..., alias="sourceDocumentId")
    ingested_at: datetime = Field(default_factory=datetime.utcnow, alias="ingestedAt")

    class Config:
        populate_by_name = True

    def unit_delta(self) -> Decimal:
        if self.event_type == EventType.DISPOSAL or self.event_type == EventType.SGB_MATURITY:
            return -self.units
        elif self.event_type == EventType.SGB_INTEREST:
            return Decimal("0.0")
        return self.units

import uuid

def generate_deterministic_event_id(isin: Optional[str], asset_name: str, event_date: date, event_type: EventType, units: Decimal, amount: Decimal) -> str:
    asset_key = isin if isin else asset_name.replace(" ", "_").upper()[:20]
    raw_key = f"{asset_key}|{event_date.isoformat()}|{event_type.value if hasattr(event_type, 'value') else str(event_type)}|{units:.4f}|{amount:.2f}"
    return str(uuid.uuid5(uuid.NAMESPACE_DNS, raw_key))

