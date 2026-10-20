use crate::api::{HrpAudit, OverlapSummary, Snapshot};
use ratatui::widgets::TableState;

/// Central application state.
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
    /// Quiet mode privacy masking
    pub quiet_mode: bool,
    /// Whether data is served from offline cache
    pub is_offline_cache: bool,
    /// Date of offline cache if applicable
    pub cache_date: Option<String>,
    /// Table selection state for scrolling lists
    pub table_state: TableState,
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
    OfflineSnapshotLoaded(Box<Snapshot>, String),
    OverlapUpdated(Box<OverlapSummary>),
    #[allow(dead_code)]
    HrpUpdated(Box<HrpAudit>),
    FetchError(String),
    Tick,
}
