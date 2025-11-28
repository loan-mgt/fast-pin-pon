package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

type statusResponse struct {
	Service   string `json:"service"`
	Status    string `json:"status"`
	Timestamp string `json:"timestamp"`
}

var requestCounter = promauto.NewCounterVec(
	prometheus.CounterOpts{
		Name: "fast_pin_pon_requests_total",
		Help: "Count of HTTP requests by handler.",
	},
	[]string{"handler"},
)

func main() {
	addr := getAddr()
	mux := http.NewServeMux()
	mux.HandleFunc("/", rootHandler)
	mux.HandleFunc("/healthz", healthHandler)
	mux.Handle("/metrics", promhttp.Handler())

	server := &http.Server{
		Addr:         addr,
		Handler:      logRequests(mux),
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 10 * time.Second,
	}

	log.Printf("API listening on %s", addr)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("server failed: %v", err)
	}
}

func rootHandler(w http.ResponseWriter, _ *http.Request) {
	requestCounter.WithLabelValues("root").Inc()
	w.Header().Set("Content-Type", "application/json")
	response := map[string]string{
		"message": "Fast Pin Pon API",
	}
	if err := json.NewEncoder(w).Encode(response); err != nil {
		http.Error(w, "failed to encode response", http.StatusInternalServerError)
	}
}

func healthHandler(w http.ResponseWriter, _ *http.Request) {
	requestCounter.WithLabelValues("health").Inc()
	w.Header().Set("Content-Type", "application/json")
	response := statusResponse{
		Service:   "fast-pin-pon",
		Status:    "ok",
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	}
	if err := json.NewEncoder(w).Encode(response); err != nil {
		http.Error(w, "failed to encode response", http.StatusInternalServerError)
	}
}

func logRequests(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		next.ServeHTTP(w, r)
		log.Printf("%s %s %s", r.Method, r.URL.Path, time.Since(start))
	})
}

// Prometheus metrics collected automatically via promhttp handler

func getAddr() string {
	if port := os.Getenv("PORT"); port != "" {
		return ":" + port
	}
	return ":8080"
}
