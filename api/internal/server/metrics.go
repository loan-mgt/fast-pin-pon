package server

import (
	"context"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/prometheus/client_golang/prometheus"
)

var (
	httpRequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "api_http_requests_total",
			Help: "Total number of HTTP requests received by the API.",
		},
		[]string{"route", "method", "status"},
	)

	httpRequestDurationSeconds = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "api_http_request_duration_seconds",
			Help:    "Duration of HTTP requests handled by the API.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"route", "method", "status"},
	)

	assignmentTravelDurationSeconds = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name: "api_intervention_assignment_travel_duration_seconds",
			Help: "Time from dispatch to arrival for a unit assignment.",
			Buckets: []float64{60, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600},
		},
		[]string{"event_id", "intervention_id", "event_type", "unit_type", "severity"},
	)

	assignmentOnSiteDurationSeconds = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name: "api_intervention_assignment_on_site_duration_seconds",
			Help: "Time from arrival to release for a unit assignment.",
			Buckets: []float64{120, 300, 600, 900, 1200, 1800, 2700, 3600, 5400, 7200, 10800},
		},
		[]string{"event_id", "intervention_id", "event_type", "unit_type", "severity"},
	)

	eventResolutionDurationSeconds = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name: "api_event_resolution_duration_seconds",
			Help: "Time from event creation to resolution (intervention completed) per event type.",
			Buckets: []float64{300, 600, 1200, 1800, 2700, 3600, 5400, 7200, 10800, 14400, 28800, 43200},
		},
		[]string{"event_type", "severity"},
	)
)

func init() {
	prometheus.MustRegister(
		httpRequestsTotal,
		httpRequestDurationSeconds,
		assignmentTravelDurationSeconds,
		assignmentOnSiteDurationSeconds,
		eventResolutionDurationSeconds,
	)
}

// metricsMiddleware records basic request metrics for Prometheus (RPS and latency).
func (s *Server) metricsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)

		durationSeconds := time.Since(start).Seconds()
		status := strconv.Itoa(ww.Status())
		route := r.URL.Path
		if rctx := chi.RouteContext(r.Context()); rctx != nil {
			if pattern := rctx.RoutePattern(); pattern != "" {
				route = pattern
			}
		}

		httpRequestsTotal.WithLabelValues(route, r.Method, status).Inc()
		httpRequestDurationSeconds.WithLabelValues(route, r.Method, status).Observe(durationSeconds)
	})
}

func (s *Server) observeAssignmentTravel(ctx context.Context, assignmentID pgtype.UUID) {
	row, err := s.queries.GetAssignmentContext(ctx, assignmentID)
	if err != nil {
		s.log.Warn().Err(err).Str("assignment_id", uuidString(assignmentID)).Msg("failed to load assignment for travel metric")
		return
	}

	if !row.DispatchedAt.Valid || !row.ArrivedAt.Valid {
		return
	}

	duration := row.ArrivedAt.Time.Sub(row.DispatchedAt.Time)
	if duration <= 0 {
		return
	}

	severityLabel := strconv.Itoa(int(row.Severity))
	assignmentTravelDurationSeconds.WithLabelValues(
		uuidString(row.EventID),
		uuidString(row.InterventionID),
		row.EventTypeCode,
		row.UnitTypeCode,
		severityLabel,
	).Observe(duration.Seconds())
}

func (s *Server) observeAssignmentOnSite(ctx context.Context, assignmentID pgtype.UUID) {
	row, err := s.queries.GetAssignmentContext(ctx, assignmentID)
	if err != nil {
		s.log.Warn().Err(err).Str("assignment_id", uuidString(assignmentID)).Msg("failed to load assignment for on-site metric")
		return
	}

	if !row.ArrivedAt.Valid || !row.ReleasedAt.Valid {
		return
	}

	duration := row.ReleasedAt.Time.Sub(row.ArrivedAt.Time)
	if duration <= 0 {
		return
	}

	severityLabel := strconv.Itoa(int(row.Severity))
	assignmentOnSiteDurationSeconds.WithLabelValues(
		uuidString(row.EventID),
		uuidString(row.InterventionID),
		row.EventTypeCode,
		row.UnitTypeCode,
		severityLabel,
	).Observe(duration.Seconds())
}

func (s *Server) observeEventResolution(ctx context.Context, interventionID pgtype.UUID, completedAt pgtype.Timestamptz) {
	if !completedAt.Valid {
		return
	}

	row, err := s.queries.GetInterventionEventContext(ctx, interventionID)
	if err != nil {
		s.log.Warn().Err(err).Str("intervention_id", uuidString(interventionID)).Msg("failed to load intervention for resolution metric")
		return
	}

	if !row.ReportedAt.Valid || !row.CompletedAt.Valid {
		return
	}

	duration := row.CompletedAt.Time.Sub(row.ReportedAt.Time)
	if duration <= 0 {
		return
	}

	severityLabel := strconv.Itoa(int(row.Severity))
	eventResolutionDurationSeconds.WithLabelValues(
		row.EventTypeCode,
		severityLabel,
	).Observe(duration.Seconds())
}