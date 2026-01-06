package server

import (
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
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
)

func init() {
	prometheus.MustRegister(httpRequestsTotal, httpRequestDurationSeconds)
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