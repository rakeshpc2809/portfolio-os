package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type SnapshotResponse struct {
	SyncInfo struct {
		CurrentValue   float64 `json:"current_value"`
		TotalInvested  float64 `json:"total_invested"`
		FormattedGain  string  `json:"formatted_unrealized_gain"`
		XirrPercentage string  `json:"xirr_percentage"`
	} `json:"sync_info"`
	RebalancePlan struct {
		BuySide struct {
			Buckets []struct {
				Bucket               string  `json:"bucket"`
				CurrentAllocationPct float64 `json:"current_allocation_pct"`
				TargetAllocationPct  float64 `json:"target_allocation_pct"`
			} `json:"buckets"`
		} `json:"buy_side"`
	} `json:"rebalance_plan"`
}

type tickMsg time.Time
type snapshotMsg struct {
	snapshot *SnapshotResponse
	latency  time.Duration
	err      error
}

type model struct {
	snapshot *SnapshotResponse
	latency  time.Duration
	err      error
	quitting bool
}

func fetchSnapshot() tea.Cmd {
	return func() tea.Msg {
		t0 := time.Now()
		req, err := http.NewRequest("GET", "http://127.0.0.1:8080/api/v1/sync/snapshot?fy=2026-27", nil)
		if err != nil {
			return snapshotMsg{err: err}
		}
		token := os.Getenv("API_AUTH_TOKEN")
		if token == "" {
			token = "dev_secret_key_123"
		}
		req.Header.Set("X-Api-Auth-Token", token)

		client := http.Client{Timeout: 3 * time.Second}
		resp, err := client.Do(req)
		if err != nil {
			return snapshotMsg{err: err}
		}
		defer resp.Body.Close()

		var snap SnapshotResponse
		if err := json.NewDecoder(resp.Body).Decode(&snap); err != nil {
			return snapshotMsg{err: err}
		}
		return snapshotMsg{snapshot: &snap, latency: time.Since(t0)}
	}
}

func tick() tea.Cmd {
	return tea.Tick(5*time.Second, func(t time.Time) tea.Msg {
		return tickMsg(t)
	})
}

func (m model) Init() tea.Cmd {
	return tea.Batch(fetchSnapshot(), tick())
}

func (m model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "q", "ctrl+c":
			m.quitting = true
			return m, tea.Quit
		case "r":
			return m, fetchSnapshot()
		}
	case snapshotMsg:
		if msg.err != nil {
			m.err = msg.err
		} else {
			m.snapshot = msg.snapshot
			m.latency = msg.latency
			m.err = nil
		}
	case tickMsg:
		return m, tea.Batch(fetchSnapshot(), tick())
	}
	return m, nil
}

func (m model) View() string {
	if m.quitting {
		return "Exiting...\n"
	}
	boxStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(lipgloss.Color("#27273a")).
		Padding(0, 1).
		Width(70)

	titleStyle := lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("#ecc093"))
	subStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("#6e738d"))
	valStyle := lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("#ffffff"))
	greenStyle := lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("#a6e3a1"))
	accentStyle := lipgloss.NewStyle().Bold(true).Foreground(lipgloss.Color("#cba6f7"))

	if m.snapshot == nil {
		if m.err != nil {
			return boxStyle.Render(fmt.Sprintf("Error fetching snapshot: %v", m.err))
		}
		return boxStyle.Render("Connecting to core-node:8080...")
	}

	si := m.snapshot.SyncInfo
	header := fmt.Sprintf("%s %s\n%s   %s   %s",
		titleStyle.Render("PORTFOLIO OS"),
		subStyle.Render("· REAL-TIME CORPUS TELEMETRY"),
		valStyle.Render(fmt.Sprintf("₹%.2f", si.CurrentValue)),
		greenStyle.Render("▲ "+si.FormattedGain),
		accentStyle.Render("XIRR "+si.XirrPercentage),
	)

	body := "\n" + titleStyle.Render("ASSET ALLOCATION") + "\n"
	for _, b := range m.snapshot.RebalancePlan.BuySide.Buckets {
		body += fmt.Sprintf("  %-15s %5.1f%% (tgt %4.1f%%)\n", b.Bucket, b.CurrentAllocationPct, b.TargetAllocationPct)
	}

	footer := fmt.Sprintf("\n%s  %s",
		greenStyle.Render(fmt.Sprintf("● core:8080 (%dms)", m.latency.Milliseconds())),
		subStyle.Render("[r] refresh  [q] quit"),
	)

	return boxStyle.Render(header + body + footer) + "\n"
}

func main() {
	p := tea.NewProgram(model{})
	if _, err := p.Run(); err != nil {
		fmt.Printf("Error: %v\n", err)
		os.Exit(1)
	}
}
