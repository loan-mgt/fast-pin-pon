package server

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"fast/pin/internal/config"
	"fast/pin/internal/database"
	db "fast/pin/internal/db/sqlc"

	"github.com/go-playground/validator/v10"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog"
)

// Server wires configuration, dependencies and HTTP routing together.
type Server struct {
	cfg       config.Config
	log       zerolog.Logger
	pool      *pgxpool.Pool
	queries   *db.Queries
	validate  *validator.Validate
	authMw    *AuthMiddleware
	startedAt time.Time
	// repairLocks prevents concurrent repair attempts for the same unit
	repairLocks sync.Map

	// lastMicrobitMessage tracks the timestamp of the last update received from the bridge
	lastMicrobitMessage atomic.Value
}

// New instantiates the HTTP server, runs DB migrations and prepares shared dependencies.
func New(ctx context.Context, cfg config.Config, log zerolog.Logger) (*Server, error) {
	pool, err := database.Connect(ctx, cfg, log)
	if err != nil {
		return nil, err
	}

	validate := newValidator()

	authMw, err := NewAuthMiddleware(ctx, cfg.Keycloak, log)
	if err != nil {
		pool.Close()
		return nil, fmt.Errorf("init auth middleware: %w", err)
	}

	srv := &Server{
		cfg:       cfg,
		log:       log,
		pool:      pool,
		queries:   db.New(pool),
		validate:  validate,
		authMw:    authMw,
		startedAt: time.Now().UTC(),
	}

	return srv, nil
}

// Close releases database resources.
func (s *Server) Close() {
	if s.authMw != nil {
		s.authMw.Close()
	}
	if s.pool != nil {
		s.pool.Close()
	}
}

// Run starts the HTTP server and blocks until the context is cancelled or an unrecoverable error occurs.
func (s *Server) Run(ctx context.Context) error {
	// Start background incident metrics sync (syncs every 30 seconds)
	StartIncidentMetricsSync(ctx, s.queries, s.log, 30*time.Second)

	httpServer := &http.Server{
		Addr:         s.cfg.HTTP.Address,
		Handler:      s.routes(),
		ReadTimeout:  s.cfg.HTTP.ReadTimeout,
		WriteTimeout: s.cfg.HTTP.WriteTimeout,
		IdleTimeout:  s.cfg.HTTP.IdleTimeout,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(shutdownCtx); err != nil {
			s.log.Error().Err(err).Msg("graceful shutdown failed")
		}
	}()

	s.log.Info().Str("addr", s.cfg.HTTP.Address).Msg("http server listening")
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		return err
	}

	return nil
}

func newValidator() *validator.Validate {
	v := validator.New(validator.WithRequiredStructEnabled())
	_ = v.RegisterValidation("latitude", func(fl validator.FieldLevel) bool {
		val, ok := fl.Field().Interface().(float64)
		if !ok {
			return false
		}
		return val >= -90 && val <= 90
	})
	_ = v.RegisterValidation("longitude", func(fl validator.FieldLevel) bool {
		val, ok := fl.Field().Interface().(float64)
		if !ok {
			return false
		}
		return val >= -180 && val <= 180
	})
	return v
}
