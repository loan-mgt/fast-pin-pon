package server

import (
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
	// 1. Check DB
	dbStatus := "up"
	if err := s.pool.Ping(r.Context()); err != nil {
		dbStatus = "down"
	}

	// 2. Check Simulation & Mode
	simStatus := "down"
	simMode := "unknown"

	client := http.Client{Timeout: 2 * time.Second}
	// Try the simulation service name (docker)
	resp, err := client.Get("http://simulation:8090/status")
	if err != nil {
		resp, err = client.Get("http://localhost:8090/status")
	}

	if err == nil && resp.StatusCode == 200 {
		simStatus = "up"
		var simData struct {
			UpdatingEnabled bool `json:"updating_enabled"`
		}
		if json.NewDecoder(resp.Body).Decode(&simData) == nil {
			if simData.UpdatingEnabled {
				simMode = "demo"
			} else {
				simMode = "hybrid"
			}
		}
		resp.Body.Close()
	}

	// 3. Engine Check
	// Engine runs on port 8081 (overridden in docker-compose) and exposes /health
	engineStatus := "down"
	if resp, err := client.Get("http://engine:8081/health"); err == nil && resp.StatusCode == 200 {
		engineStatus = "up"
		resp.Body.Close()
	}

	// 4. Microbit Network Stats
	mbStatus := "inactive"
	lastMsgVal := s.lastMicrobitMessage.Load()
	var lastMsgTime time.Time
	secondsSince := -1

	if lastMsgVal != nil {
		lastMsgTime = lastMsgVal.(time.Time)
		since := time.Since(lastMsgTime)
		secondsSince = int(since.Seconds())
		// Considered active if we received a message in the last 60 seconds
		if since < 60*time.Second {
			mbStatus = "active"
		}
	}

	// If simulation is in demo mode (updating enabled), Microbit network is considered inactive/down contextually
	if simMode == "demo" {
		mbStatus = "inactive"
	}

	// 5. System Stats (DB Counts)
	activeUnits := 0
	activeIncidents := 0

	// Count active units (not 'unavailable' or 'offline')
	err = s.pool.QueryRow(r.Context(), "SELECT COUNT(*) FROM units WHERE status NOT IN ('unavailable', 'offline')").Scan(&activeUnits)
	if err != nil {
		// log error but don't fail the health check
	}

	// Count active incidents (not 'completed' or 'cancelled') - use 'interventions' table (incidents concept map to interventions)
	err = s.pool.QueryRow(r.Context(), "SELECT COUNT(*) FROM interventions WHERE status NOT IN ('completed', 'cancelled')").Scan(&activeIncidents)
	if err != nil {
		// log error
	}

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
