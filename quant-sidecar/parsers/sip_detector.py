from typing import List
from collections import defaultdict
from .models import TaxEventSchema, EventType

def detect_and_tag_sips(events: List[TaxEventSchema]) -> List[TaxEventSchema]:
    """
    Auto-detects Systematic Investment Plans (SIPs) by grouping transactions by ISIN/Asset ID,
    checking date spacing (25 to 35 days for monthly recurring investments), and amount variation (<= 5%).
    Tags matching ACQUISITION events as EventType.SIP_INSTALMENT.
    """
    if not events:
        return events

    # Group ACQUISITION events by asset_id / isin
    acquisitions_by_asset = defaultdict(list)
    for idx, event in enumerate(events):
        if event.event_type in (EventType.ACQUISITION, EventType.SIP_INSTALMENT):
            asset_key = event.isin or event.asset_id
            acquisitions_by_asset[asset_key].append((idx, event))

    sip_indices = set()

    for asset_key, asset_events in acquisitions_by_asset.items():
        if len(asset_events) < 2:
            continue

        # Sort chronologically by date
        sorted_events = sorted(asset_events, key=lambda x: x[1].event_date)

        for i in range(len(sorted_events) - 1):
            idx1, ev1 = sorted_events[i]
            idx2, ev2 = sorted_events[i + 1]

            date_diff = (ev2.event_date - ev1.event_date).days
            amt1 = float(ev1.gross_amount)
            amt2 = float(ev2.gross_amount)

            amt_diff_pct = abs(amt1 - amt2) / max(amt1, amt2, 1.0)

            # Monthly SIP criteria: 25 to 35 days spacing AND <= 5% amount variation
            if 25 <= date_diff <= 35 and amt_diff_pct <= 0.05:
                sip_indices.add(idx1)
                sip_indices.add(idx2)

    # Return new events list with SIP_INSTALMENT tags applied
    updated_events = []
    for idx, event in enumerate(events):
        if idx in sip_indices:
            updated_events.append(event.model_copy(update={"event_type": EventType.SIP_INSTALMENT}))
        else:
            updated_events.append(event)

    return updated_events
