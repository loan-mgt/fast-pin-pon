package server

import (
	"context"
	"fmt"
	"net/http"
	"strconv"
	"sync"
	"time"

	db "fast/pin/internal/db/sqlc"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/rs/zerolog"
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

	// Incident heatmap gauge - persisted from database, survives restarts
	incidentHeatmapGauge = prometheus.NewGaugeVec(
		prometheus.GaugeOpts{
			Name: "api_incident_heatmap",
			Help: "All incident locations from database for persistent heatmap visualization.",
		},
		[]string{"event_type", "severity", "lat_bucket", "lon_bucket", "timestamp"},
	)

	// Total incidents counter per location bucket
	incidentCountGauge = prometheus.NewGaugeVec(
		prometheus.GaugeOpts{
			Name: "api_incident_count",
			Help: "Count of incidents per location bucket, synced from database.",
		},
		[]string{"lat_bucket", "lon_bucket"},
	)

	metricsSyncMu sync.Mutex
)

func init() {
	prometheus.MustRegister(
		httpRequestsTotal,
		httpRequestDurationSeconds,
		incidentHeatmapGauge,
		incidentCountGauge,
	)
}

// bucketCoordinate rounds a coordinate to a grid bucket for aggregation
// Uses 0.001 degree buckets (~100m resolution) for finer heatmap visualization
func bucketCoordinate(coord float64) string {
	bucket := float64(int(coord*1000)) / 1000
	return fmt.Sprintf("%.3f", bucket)
}

// SyncIncidentMetrics loads all incidents from the database and updates metrics
func SyncIncidentMetrics(ctx context.Context, queries *db.Queries, log zerolog.Logger) error {
	metricsSyncMu.Lock()
	defer metricsSyncMu.Unlock()

	events, err := queries.GetAllEventLocations(ctx)
	if err != nil {
		return fmt.Errorf("failed to get event locations: %w", err)
	}

	// Reset gauges before repopulating
	incidentHeatmapGauge.Reset()
	incidentCountGauge.Reset()

	// Track counts per bucket for aggregation
	bucketCounts := make(map[string]float64)

	for _, e := range events {
		severityStr := strconv.Itoa(int(e.Severity))
		latBucket := bucketCoordinate(e.Latitude)
		lonBucket := bucketCoordinate(e.Longitude)
		bucketKey := latBucket + "," + lonBucket
		
		// Convert timestamp to Unix seconds string for Prometheus label
		timestampStr := strconv.FormatInt(e.ReportedAt.Time.Unix(), 10)

		// Set individual incident marker with timestamp
		incidentHeatmapGauge.WithLabelValues(e.EventTypeCode, severityStr, latBucket, lonBucket, timestampStr).Add(1)

		// Track count per bucket
		bucketCounts[bucketKey]++
	}

	// Set aggregated counts per location
	for bucketKey, count := range bucketCounts {
		parts := splitBucketKey(bucketKey)
		if len(parts) == 2 {
			incidentCountGauge.WithLabelValues(parts[0], parts[1]).Set(count)
		}
	}

	log.Info().Int("incident_count", len(events)).Msg("synced incident metrics from database")
	return nil
}

func splitBucketKey(key string) []string {
	for i, c := range key {
		if c == ',' {
			return []string{key[:i], key[i+1:]}
		}
	}
	return nil
}

// StartIncidentMetricsSync starts a background goroutine that periodically syncs incident metrics
func StartIncidentMetricsSync(ctx context.Context, queries *db.Queries, log zerolog.Logger, interval time.Duration) {
	// Initial sync
	if err := SyncIncidentMetrics(ctx, queries, log); err != nil {
		log.Error().Err(err).Msg("initial incident metrics sync failed")
	}

	// Periodic sync
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()

		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				if err := SyncIncidentMetrics(ctx, queries, log); err != nil {
					log.Error().Err(err).Msg("periodic incident metrics sync failed")
				}
			}
		}
	}()
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