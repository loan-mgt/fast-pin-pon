package server

import (
	"context"
	"encoding/json"
	"net/http"
	"time"
)

type DetailedHealthResponse struct {
	Services        ServicesHealth  `json:"services"`
	Mode            string          `json:"mode"`
	MicrobitNetwork MicrobitNetwork `json:"microbit_network"`
	SystemStats     SystemStats     `json:"system_stats"`
	Uptime          string          `json:"uptime"`
}

type ServicesHealth struct {
	Database   string `json:"database"`
	Simulation string `json:"simulation"`
	Engine     string `json:"engine"`
}

type MicrobitNetwork struct {
	Status           string `json:"status"`
	LastMessageAt    string `json:"last_message_at,omitempty"`
	SecondsSinceLast int    `json:"seconds_since_last"`
}

type SystemStats struct {
	ActiveUnits     int `json:"active_units"`
	ActiveIncidents int `json:"active_incidents"`
}

// handleHealth godoc
// @Title Health check
// @Description Returns service health and uptime information.
// @Resource System
// @Produce json
// @Success 200 {object} HealthResponse
// @Route /healthz [get]
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	payload := HealthResponse{
		Status: "ok",
		Env:    s.cfg.Env,
		Uptime: time.Since(s.startedAt).String(),
	}
	s.writeJSON(w, http.StatusOK, payload)
}

// handleAdminHealth godoc
// @Title Admin Health Dashboard
// @Description Returns detailed health stats for IT dashboard
// @Resource System
// @Produce json
// @Route /v1/admin/health [get]
func (s *Server) handleAdminHealth(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	client := http.Client{Timeout: 2 * time.Second}

	dbStatus := s.checkDatabase(ctx)
	simStatus, simMode := s.checkSimulation(client)
	engineStatus := s.checkEngine(client)
	mbStatus, lastMsgTime, secondsSince := s.checkMicrobitNetwork(simMode)
	activeUnits, activeIncidents := s.getSystemStats(ctx)

	response := DetailedHealthResponse{
		Services: ServicesHealth{
			Database:   dbStatus,
			Simulation: simStatus,
			Engine:     engineStatus,
		},
		Mode: simMode,
		MicrobitNetwork: MicrobitNetwork{
			Status:           mbStatus,
			LastMessageAt:    lastMsgTime.Format(time.RFC3339),
			SecondsSinceLast: secondsSince,
		},
		SystemStats: SystemStats{
			ActiveUnits:     activeUnits,
			ActiveIncidents: activeIncidents,
		},
		Uptime: time.Since(s.startedAt).String(),
	}

	s.writeJSON(w, http.StatusOK, response)
}

func (s *Server) checkDatabase(ctx context.Context) string {
	if err := s.pool.Ping(ctx); err != nil {
		return "down"
	}
	return "up"
}

func (s *Server) checkSimulation(client http.Client) (status string, mode string) {
	status = "down"
	mode = "unknown"

	resp, err := client.Get("http://simulation:8090/status")
	if err != nil {
		resp, err = client.Get("http://localhost:8090/status")
	}

	if err != nil || resp.StatusCode != 200 {
		return
	}
	defer resp.Body.Close()

	status = "up"
	var simData struct {
		UpdatingEnabled bool `json:"updating_enabled"`
	}
	if json.NewDecoder(resp.Body).Decode(&simData) == nil {
		if simData.UpdatingEnabled {
			mode = "demo"
		} else {
			mode = "hybrid"
		}
	}
	return
}

func (s *Server) checkEngine(client http.Client) string {
	resp, err := client.Get("http://engine:8081/health")
	if err == nil && resp.StatusCode == 200 {
		resp.Body.Close()
		return "up"
	}
	return "down"
}

func (s *Server) checkMicrobitNetwork(simMode string) (status string, lastMsgTime time.Time, secondsSince int) {
	status = "inactive"
	secondsSince = -1

	lastMsgVal := s.lastMicrobitMessage.Load()
	if lastMsgVal != nil {
		lastMsgTime = lastMsgVal.(time.Time)
		since := time.Since(lastMsgTime)
		secondsSince = int(since.Seconds())
		if since < 60*time.Second {
			status = "active"
		}
	}

	if simMode == "demo" {
		status = "inactive"
	}
	return
}

func (s *Server) getSystemStats(ctx context.Context) (activeUnits int, activeIncidents int) {
	_ = s.pool.QueryRow(ctx, "SELECT COUNT(*) FROM units WHERE status NOT IN ('unavailable', 'offline')").Scan(&activeUnits)
	_ = s.pool.QueryRow(ctx, "SELECT COUNT(*) FROM interventions WHERE status NOT IN ('completed', 'cancelled')").Scan(&activeIncidents)
	return
}
