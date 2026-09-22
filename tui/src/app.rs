use crate::api::{HrpAudit, OverlapSummary, Snapshot};

/// Central, read-only application state.
/// Updated atomically from the polling thread via `mpsc`.
#[derive(Debug, Default)]
pub struct AppState {
    /// Latest deserialized snapshot from `/api/v1/sync/snapshot`.
    pub snapshot: Option<Snapshot>,
    /// Latest `overlap_summary` from `/api/v1/analytics/overlap`.
    pub overlap_summary: Option<OverlapSummary>,
    /// Latest HRP audit from `/api/v1/analytics/hrp-audit`.
    pub hrp_audit: Option<HrpAudit>,
    /// Last error message from any HTTP fetch, for display.
    pub last_error: Option<String>,
    /// Which modal is currently open, if any.
    pub modal: Option<ModalView>,
    /// Whether the app is shutting down.
    pub quit: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub enum ModalView {
    TaxLots,
    RebalancePlan,
    DailySummary,
}

/// Messages sent from the polling task to the render loop.
#[derive(Debug)]
pub enum AppMsg {
    SnapshotUpdated(Box<Snapshot>),
    OverlapUpdated(Box<OverlapSummary>),
    HrpUpdated(Box<HrpAudit>),
    FetchError(String),
    Tick,
}
