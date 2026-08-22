# Project Rule: Zero Tolerance for Hardcoded Fallbacks & Data Fabrication

## 1. Absolute Prohibition of Hardcoded Fallback Values
- **NO Mock Strings or Fake Metrics**: Under no circumstances shall production or client code (e.g. Composable screen views, DTOs, view models) fall back to hardcoded statistical numbers (e.g., `"+4.20%"`, `"0.88"`, `"1.45"`, `"72.8%"`, `19910714.95`, `1821603.88`) when network requests fail or are pending.
- **Null State Rendering**: When state or network data is null/pending:
  - Text fields must render explicit non-misleading placeholders such as `"--"`, `"0.00"`, `"0.0%"` or `"Syncing..."`.
  - Complex views must display explicit loading indicators (`CircularProgressIndicator`) or empty state error cards.
- **No Deceptive Fallback Functions**: Functions like `createDefaultFallbackSnapshot()` that populate fake data structures are strictly prohibited.

## 2. Hard Evidence & Verification Standards
- Every claim made in walkthroughs, forensic reports, or commit messages MUST be directly traceable to raw hardware outputs, logcat traces, or byte-for-byte diffs.
- Narrative claims unsupported by hardware screenshots or raw HTTP traces are treated as compliance failures.
