package server

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

func (s *Server) routes() http.Handler {
	r := chi.NewRouter()

	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(s.metricsMiddleware)
	r.Use(s.loggingMiddleware)
	r.Use(middleware.Recoverer)
	r.Use(middleware.Timeout(60 * time.Second))
	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   []string{"http://localhost:8080", "http://fast-pin-pon.4loop.org", "https://fast-pin-pon.4loop.org", "https://loan-mgt.github.io"},
		AllowedMethods:   []string{"GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-Request-ID"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	r.Get("/healthz", s.handleHealth)
	r.Route("/v1", func(v1 chi.Router) {
		// Apply JWT authentication to all v1 routes
		v1.Use(s.authMw.Middleware)

		v1.Get("/event-types", s.handleListEventTypes)
		v1.Get("/unit-types", s.handleListUnitTypes)
		v1.Get("/buildings", s.handleListBuildings)
		v1.Get("/sync", s.handleSync)

		v1.Get("/events", s.handleListEvents)
		v1.Post("/events", s.handleCreateEvent)
		v1.Get("/events/{eventID}", s.handleGetEvent)
		v1.Get("/events/{eventID}/logs", s.handleListEventLogs)
		v1.Post("/events/{eventID}/logs", s.handleCreateEventLog)
		v1.Get("/event-logs/recent", s.handleListRecentEventLogs)
		v1.Get("/events/{eventID}/interventions", s.handleListInterventionsForEvent)

		v1.Post("/interventions", s.handleCreateIntervention)
		v1.Get("/interventions/{interventionID}", s.handleGetIntervention)
		v1.Patch("/interventions/{interventionID}/status", s.handleUpdateInterventionStatus)
		v1.Post("/interventions/{interventionID}/assignments", s.handleCreateAssignment)
		v1.Delete("/interventions/{interventionID}/assignments/{unitID}", s.handleReleaseAssignment)
		v1.Get("/interventions/{interventionID}/assignments", s.handleListAssignmentsForIntervention)
		v1.Patch("/assignments/{assignmentID}/status", s.handleUpdateAssignmentStatus)

		v1.Get("/units", s.handleListUnits)
		v1.Get("/units/nearby", s.handleListUnitsNearby)
		v1.Post("/units", s.handleCreateUnit)
		v1.Delete("/units/{unitID}", s.handleDeleteUnit)
		v1.Patch("/units/{unitID}/status", s.handleUpdateUnitStatus)
		v1.Patch("/units/{unitID}/location", s.handleUpdateUnitLocation)
		v1.Patch("/units/{unitID}/station", s.handleUpdateUnitStation)
		v1.Post("/units/{unitID}/telemetry", s.handleInsertTelemetry)
		v1.Put("/units/{unitID}/microbit", s.handleAssignMicrobit)
		v1.Delete("/units/{unitID}/microbit", s.handleUnassignMicrobit)
		v1.Get("/units/by-microbit/{microbitID}", s.handleGetUnitByMicrobit)

		// Dispatch endpoints
		v1.Get("/dispatch/config", s.handleGetDispatchConfig)
		v1.Put("/dispatch/config", s.handleUpdateDispatchConfig)
		v1.Get("/dispatch/static", s.handleGetDispatchStatic)
		v1.Get("/dispatch/pending", s.handleListPendingInterventions)
		v1.Get("/interventions/{interventionID}/candidates", s.handleGetDispatchCandidates)
		v1.Get("/interventions/{interventionID}/dispatch-info", s.handleGetInterventionDispatchInfo)

		// Routing endpoints (pgRouting)
		v1.Post("/routing/calculate", s.handleCalculateRoute)
		v1.Get("/units/{unitID}/route", s.handleGetUnitRoute)
		v1.Post("/units/{unitID}/route", s.handleSaveUnitRoute)
		v1.Delete("/units/{unitID}/route", s.handleDeleteUnitRoute)
		v1.Post("/units/{unitID}/route/repair", s.handleRepairUnitRoute)
		v1.Patch("/units/{unitID}/route/progress", s.handleUpdateRouteProgress)
		v1.Get("/units/{unitID}/route/position", s.handleGetRoutePosition)

	})

	r.Handle("/metrics", promhttp.Handler())

	return r
}

func (s *Server) loggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)
		duration := time.Since(start)
		s.log.Info().
			Str("method", r.Method).
			Str("path", r.URL.Path).
			Int("status", ww.Status()).
			Int("bytes", ww.BytesWritten()).
			Dur("duration", duration).
			Msg("http request")
	})
}
