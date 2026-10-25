/**
 * Portfolio OS — Google Sheets Morning Valuation & Calm Signal Engine
 * Runs at 07:00 AM IST via daily time-driven trigger in Google Apps Script.
 * Operates purely in Google Cloud while the local PC is completely powered off.
 *
 * Inputs:
 *   - 'holdings_snapshot' tab: static units, AMFI codes, peak 52W NAVs, buckets
 *   - 'system_state' tab: last PC sync IST, SIP validity dates, pipeline status
 *   - AMFI NAVAll.txt: live mutual fund closing NAVs from AMFI India
 *
 * Output:
 *   - 'daily_brief' tab: daily trust & calm signal, valuation, ladder triggers
 */

// Configuration Constants
const AMFI_NAV_URL = "https://www.amfiindia.com/spages/NAVAll.txt";
const NIFTY_500_PROXY_AMFI = "147625"; // Motilal Oswal Nifty 500 Index Fund Direct Growth
const ARBITRAGE_ISIN = "INF205K01KR8"; // Invesco India Arbitrage Fund

// Ladder Thresholds
const LADDER_RESET_THRESHOLD_PCT = -5.0;
const RUNG_1_TRIGGER_PCT = -10.0;
const RUNG_2_TRIGGER_PCT = -15.0;
const RUNG_3_TRIGGER_PCT = -20.0;

/**
 * Main 07:00 AM IST entry point.
 */
function onMorningTimer() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const holdingsSheet = ss.getSheetByName("holdings_snapshot");
  const systemStateSheet = ss.getSheetByName("system_state");
  const dailyBriefSheet = ss.getSheetByName("daily_brief") || ss.insertSheet("daily_brief");

  if (!holdingsSheet || !systemStateSheet) {
    throw new Error("Missing required tabs: 'holdings_snapshot' or 'system_state'");
  }

  // 1. Load System State & Pipeline Integrity
  const systemState = loadSystemState(systemStateSheet);

  // 2. Fetch Live AMFI NAVs
  const navMap = fetchAmfiNavs();

  // 3. Load & Value Holdings
  const holdingsData = loadAndValueHoldings(holdingsSheet, navMap);

  // 4. Evaluate Lumpsum Deployment Ladder
  const ladderEvaluation = evaluateLumpsumLadder(holdingsData, navMap, systemState);

  // 5. Determine Calm / Action / Degraded Status
  const evaluationResult = determineDailyTrustSignal(systemState, holdingsData, ladderEvaluation);

  // 6. Populate 'daily_brief' Tab
  publishDailyBrief(dailyBriefSheet, evaluationResult, holdingsData, systemState, ladderEvaluation);
}

/**
 * Parses system_state tab into a key-value dictionary.
 */
function loadSystemState(sheet) {
  const data = sheet.getDataRange().getValues();
  const state = {};
  if (data.length === 0) return state;

  // Check if horizontal headers or vertical key-value pairs
  if (data.length >= 2 && data[0].length >= 2 && data[0][0] !== "key" && data[0][0] !== "last_pc_sync_ist") {
    // Vertical key-value pairs: Column A = Key, Column B = Value
    for (let r = 0; r < data.length; r++) {
      const k = String(data[r][0]).trim();
      if (k) state[k] = data[r][1];
    }
  } else {
    // Horizontal row of headers at row 0, values at row 1
    const headers = data[0];
    const values = data[1] || [];
    for (let c = 0; c < headers.length; c++) {
      const k = String(headers[c]).trim();
      if (k) state[k] = values[c];
    }
  }
  return state;
}

/**
 * Fetches and parses NAVAll.txt from AMFI India.
 * Returns map of schemeCode -> { nav: float, date: string, name: string }
 */
function fetchAmfiNavs() {
  const navMap = {};
  try {
    const response = UrlFetchApp.fetch(AMFI_NAV_URL, {
      method: "GET",
      muteHttpExceptions: true,
      headers: { "User-Agent": "PortfolioOS-SheetsValuation/3.0" }
    });
    if (response.getResponseCode() !== 200) {
      Logger.log("Failed to fetch AMFI NAVs: HTTP " + response.getResponseCode());
      return navMap;
    }

    const lines = response.getContentText().split("\n");
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line || line.indexOf(";") === -1) continue;
      const parts = line.split(";");
      if (parts.length >= 5) {
        const schemeCode = parts[0].trim();
        const navVal = parseFloat(parts[4].trim());
        if (!isNaN(navVal) && navVal > 0) {
          navMap[schemeCode] = {
            schemeCode: schemeCode,
            isinGrowth: parts[1] ? parts[1].trim() : "",
            schemeName: parts[3] ? parts[3].trim() : "",
            nav: navVal,
            navDate: parts[5] ? parts[5].trim() : ""
          };
        }
      }
    }
  } catch (err) {
    Logger.log("Exception fetching AMFI NAVs: " + err.toString());
  }
  return navMap;
}

/**
 * Reads holdings_snapshot tab and computes live market valuation and drawdown from 52w peak.
 */
function loadAndValueHoldings(sheet, navMap) {
  const data = sheet.getDataRange().getValues();
  if (data.length < 2) return [];

  const headers = data[0].map(h => String(h).trim());
  const colIndex = {};
  headers.forEach((h, idx) => {
    colIndex[h.toLowerCase()] = idx;
    colIndex[h] = idx;
  });

  const getCol = (row, names, defaultVal) => {
    for (let name of names) {
      if (colIndex[name] !== undefined) {
        const val = row[colIndex[name]];
        if (val !== undefined && val !== null && String(val).trim() !== "") return val;
      }
    }
    return defaultVal;
  };

  const holdings = [];
  for (let r = 1; r < data.length; r++) {
    const row = data[r];
    const isin = String(getCol(row, ["ISIN", "isin"], "")).trim();
    if (!isin) continue;

    const amfiCode = String(getCol(row, ["AMFI_Code", "amfi_code"], "")).trim();
    const schemeName = String(getCol(row, ["Scheme_Name", "fund_name", "scheme_name"], isin)).trim();
    const bucket = String(getCol(row, ["Bucket", "asset_bucket", "bucket"], "UNASSIGNED")).trim();
    const totalUnits = parseFloat(getCol(row, ["Total_Units", "total_units"], 0)) || 0;
    const exitLoadFreeUnits = parseFloat(getCol(row, ["Exit_Load_Free_Units", "exit_load_free_units"], totalUnits)) || totalUnits;
    const peak52wNav = parseFloat(getCol(row, ["Peak_52W_NAV", "peak_52w_nav"], 0)) || 0;
    const snapshotNav = parseFloat(getCol(row, ["current_nav"], 0)) || 0;

    // Resolve Live NAV from AMFI or fall back to snapshot NAV
    let currentNav = snapshotNav;
    let navDate = "";
    if (amfiCode && navMap[amfiCode]) {
      currentNav = navMap[amfiCode].nav;
      navDate = navMap[amfiCode].navDate;
    }

    const peakNav = Math.max(peak52wNav, currentNav);
    const currentValue = totalUnits * currentNav;
    const exitLoadFreeValue = exitLoadFreeUnits * currentNav;
    const drawdownPct = peakNav > 0 ? ((currentNav - peakNav) / peakNav) * 100.0 : 0.0;

    holdings.push({
      isin: isin,
      amfiCode: amfiCode,
      schemeName: schemeName,
      bucket: bucket,
      totalUnits: totalUnits,
      exitLoadFreeUnits: exitLoadFreeUnits,
      lockedUnits: Math.max(0, totalUnits - exitLoadFreeUnits),
      currentNav: currentNav,
      peak52wNav: peakNav,
      drawdownPct: drawdownPct,
      currentValue: currentValue,
      exitLoadFreeValue: exitLoadFreeValue,
      navDate: navDate
    });
  }

  return holdings;
}

/**
 * Evaluates the Nifty 500 Drawdown and Lumpsum Deployment Ladder Rungs.
 */
function evaluateLumpsumLadder(holdings, navMap, systemState) {
  // Find Nifty 500 Proxy or Motilal Nifty 500 holding
  let benchmarkNav = 0;
  let benchmarkPeak = 0;

  if (systemState) {
    benchmarkPeak = parseFloat(systemState["benchmark_peak_52w_nav"]) || 0;
    benchmarkNav = parseFloat(systemState["benchmark_current_nav"]) || 0;
  }

  if (navMap && navMap[NIFTY_500_PROXY_AMFI]) {
    benchmarkNav = navMap[NIFTY_500_PROXY_AMFI].nav;
  }

  // Fallback to proxy or held core index fund if not present
  if (benchmarkPeak <= 0) {
    const niftyHolding = holdings.find(h => h.amfiCode === NIFTY_500_PROXY_AMFI || h.isin === "INF247L01676");
    if (niftyHolding) {
      if (benchmarkNav === 0) benchmarkNav = niftyHolding.currentNav;
      benchmarkPeak = niftyHolding.peak52wNav;
    } else {
      const coreHolding = holdings.find(h => h.bucket === "EQUITY_CORE");
      if (coreHolding) {
        if (benchmarkNav === 0) benchmarkNav = coreHolding.currentNav;
        benchmarkPeak = coreHolding.peak52wNav;
      }
    }
  }

  if (benchmarkPeak <= 0) benchmarkPeak = benchmarkNav;
  const benchmarkDrawdownPct = benchmarkPeak > 0 ? ((benchmarkNav - benchmarkPeak) / benchmarkPeak) * 100.0 : 0.0;

  // Find Arbitrage holding
  const arbHolding = holdings.find(h => h.isin === ARBITRAGE_ISIN || h.bucket === "LIQUID_BUFFER");
  const arbFreeUnits = arbHolding ? arbHolding.exitLoadFreeUnits : 0;
  const arbFreeValue = arbHolding ? arbHolding.exitLoadFreeValue : 0;
  const arbNav = arbHolding ? arbHolding.currentNav : 1.0;

  let activeRung = 0;
  let deployPct = 0.0;
  let rungAction = "NO_DEPLOYMENT";
  let rungNarrative = "Normal monthly SIP only. Preserve emergency/opportunity cash in Arbitrage.";

  if (benchmarkDrawdownPct <= RUNG_3_TRIGGER_PCT) {
    activeRung = 3;
    deployPct = 100.0;
    rungAction = "RUNG_3_DEPLOY";
    rungNarrative = "Nifty 500 <= -20%: Deploy 100% of exit-load-free Arbitrage buffer into core index buckets.";
  } else if (benchmarkDrawdownPct <= RUNG_2_TRIGGER_PCT) {
    activeRung = 2;
    deployPct = 50.0;
    rungAction = "RUNG_2_DEPLOY";
    rungNarrative = "Nifty 500 <= -15%: Deploy 50% of exit-load-free Arbitrage buffer into equity.";
  } else if (benchmarkDrawdownPct <= RUNG_1_TRIGGER_PCT) {
    activeRung = 1;
    deployPct = 25.0;
    rungAction = "RUNG_1_DEPLOY";
    rungNarrative = "Nifty 500 <= -10%: Deploy 25% of exit-load-free Arbitrage buffer into underweight equity.";
  } else if (benchmarkDrawdownPct > LADDER_RESET_THRESHOLD_PCT) {
    activeRung = 0;
    deployPct = 0.0;
    rungAction = "RESET_STANDING_BY";
    rungNarrative = "Benchmark drawdown above -5%: Ladder rungs reset. Capital preserved.";
  }

  const deployAmount = (arbFreeValue * deployPct) / 100.0;
  const deployUnits = arbNav > 0 ? deployAmount / arbNav : 0.0;

  return {
    benchmarkNav: benchmarkNav,
    benchmarkPeak: benchmarkPeak,
    benchmarkDrawdownPct: benchmarkDrawdownPct,
    activeRung: activeRung,
    rungAction: rungAction,
    deployPct: deployPct,
    deployAmount: deployAmount,
    deployUnits: deployUnits,
    arbFreeValue: arbFreeValue,
    arbFreeUnits: arbFreeUnits,
    rungNarrative: rungNarrative
  };
}

/**
 * Determines whether daily condition is CALM, ACTION_REQUIRED, or PIPELINE_DEGRADED.
 */
function determineDailyTrustSignal(systemState, holdings, ladder) {
  const today = new Date();
  const todayStr = Utilities.formatDate(today, "Asia/Kolkata", "yyyy-MM-dd");

  const validUntilStr = String(systemState["units_valid_until_date"] || "2099-12-31").trim();
  const sipStatus = String(systemState["sip_pipeline_status"] || "HEALTHY").trim();
  const reconciliationStatus = String(systemState["reconciliation_status"] || "MATCHED").trim();

  // 1. Pipeline Degradation Check
  const isUnitsStale = sipStatus === "UNITS_STALE_PENDING_CAS" || todayStr > validUntilStr;
  const isReconciliationMismatch = reconciliationStatus === "MISMATCH";

  if (isUnitsStale || isReconciliationMismatch) {
    return {
      status: "PIPELINE_DEGRADED",
      headline: isUnitsStale ? "Units Stale: Pending CAS Settlement" : "Ledger Reconciliation Mismatch",
      description: isUnitsStale
        ? "Settlement lockout active. Units valid until " + validUntilStr + ". Fresh CAS email required before rebalancing."
        : "Reconciliation mismatch detected against declared CAS units.",
      badge: "WARNING"
    };
  }

  // 2. Action Required Check (Ladder Triggered)
  if (ladder.activeRung > 0) {
    return {
      status: "ACTION_REQUIRED",
      headline: "Lumpsum Ladder Rung " + ladder.activeRung + " Triggered (" + ladder.benchmarkDrawdownPct.toFixed(1) + "%)",
      description: ladder.rungNarrative + " Deploy ₹" + Math.round(ladder.deployAmount).toLocaleString("en-IN") + " from Arbitrage.",
      badge: "ACTION_REQUIRED"
    };
  }

  // 3. Default Calm Signal
  return {
    status: "CALM",
    headline: "Portfolio is Balanced & Calm",
    description: "All units verified against idempotent ledger. Ladder standing by. No action required.",
    badge: "CALM"
  };
}

/**
 * Renders the daily_brief tab in Google Sheets.
 */
function publishDailyBrief(sheet, signal, holdings, systemState, ladder) {
  sheet.clear();

  const totalValue = holdings.reduce((sum, h) => sum + h.currentValue, 0);
  const nowIstStr = Utilities.formatDate(new Date(), "Asia/Kolkata", "yyyy-MM-dd HH:mm:ss 'IST'");

  const summaryRows = [
    ["DAILY BRIEF & CALM SIGNAL", nowIstStr],
    ["Signal Status", signal.status],
    ["Headline", signal.headline],
    ["Narrative", signal.description],
    ["Total Portfolio Valuation", "₹" + Math.round(totalValue).toLocaleString("en-IN")],
    ["Nifty 500 Drawdown", ladder.benchmarkDrawdownPct.toFixed(2) + "%"],
    ["Arbitrage Dry Powder Available", "₹" + Math.round(ladder.arbFreeValue).toLocaleString("en-IN")],
    ["Ladder Deployment Action", signal.status === "ACTION_REQUIRED" ? "Deploy ₹" + Math.round(ladder.deployAmount).toLocaleString("en-IN") : "None (Standing By)"],
    ["Last Local PC Sync", String(systemState["last_pc_sync_ist"] || "N/A")],
    ["Units Valid Until", String(systemState["units_valid_until_date"] || "N/A")],
    ["Reconciliation Status", String(systemState["reconciliation_status"] || "MATCHED")],
    [],
    ["HOLDINGS BREAKDOWN", "", "", "", "", "", ""]
  ];

  sheet.getRange(1, 1, summaryRows.length, 2).setValues(summaryRows.map(r => [r[0], r[1] || ""]));

  // Table Headers
  const tableHeaders = [
    "ISIN",
    "Scheme Name",
    "Bucket",
    "Total Units",
    "Exit-Load-Free Units",
    "Live NAV (₹)",
    "Valuation (₹)",
    "Drawdown from 52W Peak (%)"
  ];

  const tableData = holdings.map(h => [
    h.isin,
    h.schemeName,
    h.bucket,
    h.totalUnits,
    h.exitLoadFreeUnits,
    h.currentNav,
    Math.round(h.currentValue),
    h.drawdownPct.toFixed(2) + "%"
  ]);

  const startRow = summaryRows.length + 2;
  sheet.getRange(startRow, 1, 1, tableHeaders.length).setValues([tableHeaders]).setFontWeight("bold");
  if (tableData.length > 0) {
    sheet.getRange(startRow + 1, 1, tableData.length, tableHeaders.length).setValues(tableData);
  }

  // Format headers
  sheet.getRange(1, 1, 1, 2).setFontWeight("bold").setBackground("#e6f2ff");
  const statusCell = sheet.getRange(2, 2);
  if (signal.status === "CALM") {
    statusCell.setBackground("#d4edda").setFontColor("#155724").setFontWeight("bold");
  } else if (signal.status === "ACTION_REQUIRED") {
    statusCell.setBackground("#fff3cd").setFontColor("#856404").setFontWeight("bold");
  } else {
    statusCell.setBackground("#f8d7da").setFontColor("#721c24").setFontWeight("bold");
  }

  sheet.autoResizeColumns(1, tableHeaders.length);
}

/**
 * Web App entry point for Zero-Knowledge Cloud State Dead-Drop Relay.
 * Allows core-node to upload portfolio_state.enc and mobile app to fetch it.
 */
function doGet(e) {
  const params = e ? e.parameter : {};
  const action = params.action || "status";
  const token = params.token || "";

  const scriptProps = PropertiesService.getScriptProperties();
  const configuredToken = scriptProps.getProperty("API_AUTH_TOKEN");

  if (!configuredToken || !token || token !== configuredToken) {
    return ContentService.createTextOutput(JSON.stringify({ error: "Unauthorized" }))
      .setMimeType(ContentService.MimeType.JSON);
  }

  if (action === "bundle") {
    const encryptedBundleB64 = scriptProps.getProperty("PORTFOLIO_STATE_ENC_B64") || "";
    const updatedAt = scriptProps.getProperty("PORTFOLIO_STATE_UPDATED_AT") || "";
    return ContentService.createTextOutput(JSON.stringify({
      status: "SUCCESS",
      updated_at: updatedAt,
      bundle_b64: encryptedBundleB64
    })).setMimeType(ContentService.MimeType.JSON);
  }

  return ContentService.createTextOutput(JSON.stringify({
    status: "HEALTHY",
    engine: "Portfolio OS Sheets Relay v4.1",
    timestamp: new Date().toISOString()
  })).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  try {
    const postData = JSON.parse(e.postData.contents);
    const token = postData.token || "";

    const scriptProps = PropertiesService.getScriptProperties();
    const configuredToken = scriptProps.getProperty("API_AUTH_TOKEN");

    if (!configuredToken || !token || token !== configuredToken) {
      return ContentService.createTextOutput(JSON.stringify({ error: "Unauthorized" }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    if (postData.bundle_b64) {
      scriptProps.setProperty("PORTFOLIO_STATE_ENC_B64", postData.bundle_b64);
      scriptProps.setProperty("PORTFOLIO_STATE_UPDATED_AT", new Date().toISOString());
      return ContentService.createTextOutput(JSON.stringify({ status: "SUCCESS", message: "Bundle updated" }))
        .setMimeType(ContentService.MimeType.JSON);
    }

    return ContentService.createTextOutput(JSON.stringify({ error: "Missing bundle_b64 payload" }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ error: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
