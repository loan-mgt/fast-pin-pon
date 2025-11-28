package server

import (
	"net/http"
	"time"
)

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	payload := HealthResponse{
		Status: "ok",
		Env:    s.cfg.Env,
		Uptime: time.Since(s.startedAt).String(),
	}
	s.writeJSON(w, http.StatusOK, payload)
}
