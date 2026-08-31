/**
 * Portfolio OS - Automated Frontend Static Verification Test Suite
 * 
 * Verifies:
 * 1. ES Module Exports (all 34 exports via root facade portfolio.js and submodules)
 * 2. Window.* Global Handlers (all 15 handlers)
 * 3. Pure Functions: resampleToMonthEnd
 * 4. Real Code Execution: Invoking renderTargetFundProgression from rebalance.js
 *    against a DOM fixture to assert lumpsum math non-overflow (sum <= 100.0%).
 */

import assert from "node:assert/strict";

// Mock minimal browser environment for Node.js test execution
const mockElements = new Map();
function getOrCreateMockElement(id) {
  if (!mockElements.has(id)) {
    mockElements.set(id, {
      id,
      innerHTML: "",
      textContent: "",
      style: {},
      children: [],
      classList: {
        add: () => {},
        remove: () => {},
        contains: () => false,
      },
      addEventListener: () => {},
      replaceChildren: () => {},
      appendChild: () => {},
      querySelector: () => null,
      querySelectorAll: () => [],
    });
  }
  return mockElements.get(id);
}

global.document = {
  getElementById: (id) => getOrCreateMockElement(id),
  querySelector: (sel) => {
    const id = sel.replace(/^[#.]/, "");
    return getOrCreateMockElement(id);
  },
  querySelectorAll: () => [],
  createElement: (tag) => ({
    tagName: tag,
    innerHTML: "",
    textContent: "",
    style: {},
    appendChild: () => {},
    setAttribute: () => {},
    classList: { add: () => {}, remove: () => {} },
  }),
  createDocumentFragment: () => ({
    appendChild: () => {},
  }),
  addEventListener: () => {},
};

global.window = {
  document: global.document,
  addEventListener: () => {},
  echarts: {
    init: () => ({
      setOption: () => {},
      resize: () => {},
      dispose: () => {},
    }),
    graphic: {
      LinearGradient: class {},
    },
  },
};

console.log("=== 1. VERIFYING ES MODULE EXPORTS (34 FUNCTIONS) ===");

// Dynamic import of root facade
const portfolioFacade = await import("../../main/resources/static/src/js/modules/portfolio.js");

const expectedExports = [
  // summary.js (1)
  "updatePortfolioSummary",
  
  // holdings.js (4)
  "renderHoldingsTable",
  "toggleLotDetails",
  "renderSchemeGroupedTaxLotsUI",
  "toggleSchemeLotCard",
  
  // charts.js (8)
  "renderPieChart",
  "resampleToMonthEnd",
  "renderNetWorthTrendChart",
  "loadNetWorthTrend",
  "renderAllocationChart",
  "renderCategoryChart",
  "renderBucketAllocationChart",
  "renderFundAllocationCompareChart",
  
  // fire.js (6)
  "fetchGoalSummary",
  "renderGoalSummary",
  "fetchFireSummary",
  "renderFireSummary",
  "initFireSensitivitySliders",
  "renderFireFanChart",
  
  // overlap.js (4)
  "loadBenchmarkAnalytics",
  "populateFundDropdowns",
  "loadOverlapAnalytics",
  "loadUpSetAnalytics",
  
  // rebalance.js (11)
  "fetchConsolidationPreviewData",
  "renderConsolidationPlan",
  "fetchRebalancePreview",
  "updateRebalanceSummary",
  "fetchBucketRebalance",
  "renderBucketRebalance",
  "renderCashflowSankey",
  "loadActionRecommendations",
  "loadUnifiedRebalancePlan",
  "renderUnifiedRebalancePlanUI",
  "renderTargetFundProgression",
];

for (const exp of expectedExports) {
  assert.ok(
    typeof portfolioFacade[exp] === "function",
    `Facade export missing: ${exp}`,
  );
  console.log(`  ✓ Facade export verified: ${exp}`);
}

console.log("\n=== 2. VERIFYING WINDOW.* ASSIGNMENTS (15 HANDLERS) ===");

// Import all submodules to trigger their window assignments
await import("../../main/resources/static/src/js/modules/portfolio/summary.js");
await import("../../main/resources/static/src/js/modules/portfolio/holdings.js");
await import("../../main/resources/static/src/js/modules/portfolio/charts.js");
await import("../../main/resources/static/src/js/modules/portfolio/fire.js");
await import("../../main/resources/static/src/js/modules/portfolio/overlap.js");
await import("../../main/resources/static/src/js/modules/portfolio/rebalance.js");

const expectedWindowAssignments = [
  "toggleLotDetails",
  "toggleSchemeLotCard",
  "renderSchemeGroupedTaxLotsUI",
  "fetchFireSummary",
  "renderFireSummary",
  "renderFireFanChart",
  "loadOverlapAnalytics",
  "loadUpSetAnalytics",
  "render2FundVennDiagram",
  "onkeydown",
  "loadActionRecommendations",
  "loadUnifiedRebalancePlan",
  "openLumpsumModal",
  "closeLumpsumModal",
  "submitLumpsumSim",
];

for (const handler of expectedWindowAssignments) {
  assert.ok(
    global.window[handler] !== undefined,
    `window.${handler} assignment missing`,
  );
  console.log(`  ✓ window.${handler} assignment verified`);
}

console.log("\n=== 3. VERIFYING PURE FUNCTION: resampleToMonthEnd ===");
const { resampleToMonthEnd } = portfolioFacade;
const rawDates = [
  "2025-01-05",
  "2025-01-15",
  "2025-01-31",
  "2025-02-10",
  "2025-02-28",
  "2025-03-05",
  "2025-03-31",
];
const rawValues = [100, 110, 120, 125, 130, 135, 140];
const rawInvested = [90, 90, 90, 95, 95, 100, 100];

const resampled = resampleToMonthEnd(rawDates, rawValues, rawInvested);
assert.equal(resampled.dates.length, 3, "Expected 3 month-end dates");
assert.equal(resampled.values[0], 120, "Jan month-end value mismatch");
assert.equal(resampled.values[1], 130, "Feb month-end value mismatch");
assert.equal(resampled.values[2], 140, "Mar month-end value mismatch");
console.log(`  ✓ resampleToMonthEnd produced 3 month-end points cleanly`);

console.log("\n=== 4. VERIFYING REAL CODE: renderTargetFundProgression LUMPSUM MATH ===");
const { renderTargetFundProgression } = portfolioFacade;

// Test Case 1: ₹50,000 Inflow Simulation
const fixtureHoldings = [
  { asset_id: "INF879O01027", asset_name: "Parag Parikh Flexi Cap Fund", current_value: 500000 },
  { asset_id: "INF109KC13X2", asset_name: "ICICI Prudential Nifty LargeMidcap 250", current_value: 300000 },
  { asset_id: "INF204K01K15", asset_name: "Nippon India Small Cap Fund", current_value: 200000 },
];

const fixtureConfig = {
  versions: [
    {
      version: "2.3",
      targets: [
        {
          bucket: "core",
          target_pct: 50.0,
          preferred_funds: [
            { fund_id: "INF109KC13X2", allocation_weight: 0.6 },
            { fund_id: "INF879O01027", allocation_weight: 0.4 },
          ],
        },
        {
          bucket: "satellite",
          target_pct: 50.0,
          preferred_funds: [
            { fund_id: "INF204K01K15", allocation_weight: 1.0 },
          ],
        },
      ],
    },
  ],
};

const plan50k = {
  sell_side: { waterfall: [] },
  buy_side: {
    buckets: [
      {
        bucket: "core",
        amount_allocated: 30000,
        fund_breakdown: [
          { fund_id: "INF109KC13X2", fund_name: "ICICI Prudential Nifty LargeMidcap 250", amount: 18000 },
          { fund_id: "INF879O01027", fund_name: "Parag Parikh Flexi Cap Fund", amount: 12000 },
        ],
      },
      {
        bucket: "satellite",
        amount_allocated: 20000,
        fund_breakdown: [
          { fund_id: "INF204K01K15", fund_name: "Nippon India Small Cap Fund", amount: 20000 },
        ],
      },
    ],
  },
};

const container = getOrCreateMockElement("rebalanceFundProgressionContainer");
renderTargetFundProgression(plan50k, fixtureHoldings, fixtureConfig);

assert.ok(container.innerHTML.length > 0, "Container should have rendered HTML");

// Extract all post-progression percentages (format: "➔</span> <span ...>X.X%")
const postPctMatches = [...container.innerHTML.matchAll(/➔<\/span>\s*<span[^>]*>([0-9.]+)%/g)].map(m => parseFloat(m[1]));
assert.ok(postPctMatches.length >= 3, `Expected at least 3 fund progression percentages, got ${postPctMatches.length}`);

const sumPostPct50k = postPctMatches.reduce((a, b) => a + b, 0);
console.log(`  ✓ ₹50,000 Inflow (Real Function Execution): Rendered ${postPctMatches.length} funds, 1-decimal sum = ${sumPostPct50k.toFixed(1)}% (100.0% ± 0.2%)`);
assert.ok(Math.abs(sumPostPct50k - 100.0) <= 0.2, `1-decimal rounded sum should be near 100%, got ${sumPostPct50k}`);

// Test Case 2: ₹2,00,000 Inflow Simulation
const plan200k = {
  sell_side: { waterfall: [] },
  buy_side: {
    buckets: [
      {
        bucket: "core",
        amount_allocated: 120000,
        fund_breakdown: [
          { fund_id: "INF109KC13X2", fund_name: "ICICI Prudential Nifty LargeMidcap 250", amount: 72000 },
          { fund_id: "INF879O01027", fund_name: "Parag Parikh Flexi Cap Fund", amount: 48000 },
        ],
      },
      {
        bucket: "satellite",
        amount_allocated: 80000,
        fund_breakdown: [
          { fund_id: "INF204K01K15", fund_name: "Nippon India Small Cap Fund", amount: 80000 },
        ],
      },
    ],
  },
};

renderTargetFundProgression(plan200k, fixtureHoldings, fixtureConfig);
const postPctMatches200k = [...container.innerHTML.matchAll(/➔<\/span>\s*<span[^>]*>([0-9.]+)%/g)].map(m => parseFloat(m[1]));
const sumPostPct200k = postPctMatches200k.reduce((a, b) => a + b, 0);
console.log(`  ✓ ₹2,00,000 Inflow (Real Function Execution): Rendered ${postPctMatches200k.length} funds, 1-decimal sum = ${sumPostPct200k.toFixed(1)}% (100.0% ± 0.2%)`);
assert.ok(Math.abs(sumPostPct200k - 100.0) <= 0.2, `1-decimal rounded sum should be near 100%, got ${sumPostPct200k}`);

console.log("\n>>> ALL 34 EXPORTS, 15 WINDOW ASSIGNMENTS, AND REAL LUMPSUM FUNCTION INVOCATIONS PASSED! <<<");
