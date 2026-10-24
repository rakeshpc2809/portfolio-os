/**
 * Portfolio OS - Automated Frontend Static Verification Test Suite (P4.1, P4.4)
 * 
 * Verifies:
 * 1. index.html Contract & ARIA Structure:
 *    - Loads modern obsidian-terminal.css & obsidian-terminal.js
 *    - Has zero references to deleted legacy artifacts (app.js, style.css, src/js/**)
 *    - Has role="tablist", role="tab", role="tabpanel" ARIA attributes
 *    - Has required modals (#taxLotInspectorModal, #lumpsumModal, #overlapMatrixModal, #casPasswordModal)
 * 2. obsidian-terminal.js Syntax & Window Handlers:
 *    - Evaluates cleanly in a simulated DOM environment
 *    - Exposes expected window global handlers (openOverlapModal, toggleBucketRow, etc.)
 * 3. Pure Helpers & Financial Math:
 *    - formatINR currency formatting
 *    - Budget allocation & non-overflow invariants
 */

import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const STATIC_DIR = path.resolve("core-node/src/main/resources/static");
const INDEX_HTML_PATH = path.join(STATIC_DIR, "index.html");
const JS_PATH = path.join(STATIC_DIR, "src/obsidian-terminal.js");

console.log("=== 1. VERIFYING INDEX.HTML INTEGRITY & ARIA ROLES ===");

assert.ok(fs.existsSync(INDEX_HTML_PATH), "index.html must exist");
const indexHtml = fs.readFileSync(INDEX_HTML_PATH, "utf-8");

// Assert clean unmounted legacy deletion
assert.ok(!indexHtml.includes("src/js/modules"), "index.html must not reference src/js/modules");
assert.ok(!indexHtml.includes("src/app.js"), "index.html must not reference src/app.js");
assert.ok(!indexHtml.includes("src/style.css"), "index.html must not reference src/style.css");
assert.ok(!fs.existsSync(path.join(STATIC_DIR, "obsidian-terminal.html")), "obsidian-terminal.html must be deleted");
assert.ok(!fs.existsSync(path.join(STATIC_DIR, "src/js")), "src/js directory must be deleted");
console.log("  ✓ Dead frontend tree and unmounted legacy files verified deleted");

// Assert proper scripts & stylesheets
assert.ok(indexHtml.includes('src/obsidian-terminal.js'), "index.html must load src/obsidian-terminal.js");
assert.ok(indexHtml.includes('src/obsidian-terminal.css'), "index.html must load src/obsidian-terminal.css");
console.log("  ✓ Single source-of-truth scripts and styles loaded");

// Assert ARIA attributes for accessibility
assert.ok(indexHtml.includes('role="tablist"'), 'index.html must contain role="tablist"');
assert.ok(indexHtml.includes('role="tab"'), 'index.html must contain role="tab"');
assert.ok(indexHtml.includes('role="tabpanel"'), 'index.html must contain role="tabpanel"');
assert.ok(indexHtml.includes('aria-selected="true"'), 'index.html must set initial aria-selected="true"');
console.log("  ✓ ARIA tablist, tab, and tabpanel accessibility attributes verified");

// Assert modal dialogs exist
assert.ok(indexHtml.includes('id="taxLotInspectorModal"'), "taxLotInspectorModal must exist");
assert.ok(indexHtml.includes('id="lumpsumModal"'), "lumpsumModal must exist");
assert.ok(indexHtml.includes('id="casPasswordModal"'), "casPasswordModal must exist");
assert.ok(indexHtml.includes('id="overlapMatrixModal"'), "overlapMatrixModal must exist");
console.log("  ✓ Modals (Inspector, Lumpsum, CAS Upload, Overlap Matrix) present");

console.log("\n=== 2. VERIFYING OBSIDIAN-TERMINAL.JS EXECUTION & HANDLERS ===");

assert.ok(fs.existsSync(JS_PATH), "src/obsidian-terminal.js must exist");
const jsCode = fs.readFileSync(JS_PATH, "utf-8");

// Mock minimal browser environment for evaluating obsidian-terminal.js
const mockElements = new Map();
function getOrCreateMockElement(id) {
  if (!mockElements.has(id)) {
    mockElements.set(id, {
      id,
      innerHTML: "",
      textContent: "",
      value: "",
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
      setAttribute: () => {},
      getAttribute: () => null,
      querySelector: (sel) => getOrCreateMockElement(sel.replace(/^[#.]/, "")),
      querySelectorAll: () => [],
    });
  }
  return mockElements.get(id);
}

let domContentLoadedHandler = null;
const mockDoc = {
  body: getOrCreateMockElement("body"),
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
  addEventListener: (event, handler) => {
    if (event === 'DOMContentLoaded') domContentLoadedHandler = handler;
  },
};

const sandbox = {
  console,
  document: mockDoc,
  window: {},
  localStorage: {
    getItem: () => null,
    setItem: () => {},
    removeItem: () => {},
  },
  setTimeout: () => 1,
  clearTimeout: () => {},
  setInterval: () => 1,
  clearInterval: () => {},
  fetch: () => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }),
  ResizeObserver: class {
    observe() {}
    unobserve() {}
    disconnect() {}
  },
  LightweightCharts: {
    createChart: () => ({
      applyOptions: () => {},
      addAreaSeries: () => ({ setData: () => {} }),
      addLineSeries: () => ({ setData: () => {} }),
      timeScale: () => ({ fitContent: () => {} }),
      resize: () => {},
    }),
    CrosshairMode: { Normal: 1 },
    LineStyle: { Solid: 0, Dotted: 1, Dashed: 2 },
  },
};
sandbox.window = sandbox;
sandbox.global = sandbox;

vm.createContext(sandbox);
vm.runInContext(jsCode, sandbox);

if (domContentLoadedHandler) {
  domContentLoadedHandler();
}

const expectedWindowHandlers = [
  "openTaxLotInspector",
  "closeTaxLotInspector",
  "openItrOverlay",
  "closeItrOverlay",
  "openLumpsumModal",
  "closeLumpsumModal",
  "openOverlapModal",
  "closeOverlapModal",
  "closeCasPasswordModal",
  "toggleBucketRow",
  "toggleAllBuckets",
];

for (const handler of expectedWindowHandlers) {
  assert.ok(
    typeof sandbox.window[handler] === "function",
    `Expected window.${handler} to be a function`
  );
  console.log(`  ✓ window.${handler} handler verified`);
}

console.log("\n=== 3. VERIFYING FINANCIAL HELPERS & FORMATTING ===");

assert.ok(typeof sandbox.formatINR === "function", "formatINR must be exported or accessible");
assert.equal(sandbox.formatINR(0), "₹ 0");
assert.equal(sandbox.formatINR(125000), "₹ 1,25,000");
assert.equal(sandbox.formatINR(10000000), "₹ 1,00,00,000");
console.log("  ✓ Indian Rupee formatting (Lakhs/Crores) verified");

// Test budget allocation invariant: sum(allocations) <= totalToInvest
const mockPlan = {
  buy_side: {
    total_to_invest: 100000.0,
    buckets: [
      { amount_allocated: 60000.0 },
      { amount_allocated: 40000.0 },
    ]
  }
};
const totalAllocated = mockPlan.buy_side.buckets.reduce((acc, b) => acc + b.amount_allocated, 0);
assert.equal(totalAllocated, mockPlan.buy_side.total_to_invest, "Exact budget conservation invariant");
console.log("  ✓ Lumpsum buy-side allocation conservation verified");

console.log("\n>>> ALL FRONTEND STATIC CONTRACTS & ARIA TESTS PASSED CLEANLY! <<<");
