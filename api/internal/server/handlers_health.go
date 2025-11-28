package server

import (
	"net/http"
	"time"
)

// handleHealth godoc
// @Summary Health check
// @Description Returns service health and uptime information.
// @Tags System
// @Produce json
// @Success 200 {object} HealthResponse
// @Router /healthz [get]
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	payload := HealthResponse{
		Status: "ok",
		Env:    s.cfg.Env,
		Uptime: time.Since(s.startedAt).String(),
	}
	s.writeJSON(w, http.StatusOK, payload)
}
