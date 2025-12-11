// Package main wires configuration, dependencies, and HTTP server startup.
//
// @Title Fast Pin Pon API
// @Version 0.1.0
// @Description Real-time coordination API for intervention dispatch.
// @Server http://localhost:8081 Local development
// @Server https://api.fast-pin-pon.4loop.org Production (HTTPS)
package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"
	"time"

	"fast/pin/internal/config"
	"fast/pin/internal/server"

	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatal().Err(err).Msg("load config")
	}

	logger := newLogger(cfg)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	srv, err := server.New(ctx, cfg, logger)
	if err != nil {
		logger.Fatal().Err(err).Msg("init server")
	}
	defer srv.Close()

	if err := srv.Run(ctx); err != nil {
		logger.Fatal().Err(err).Msg("server stopped")
	}
}

func newLogger(cfg config.Config) zerolog.Logger {
	zerolog.TimeFieldFormat = time.RFC3339Nano
	level, err := zerolog.ParseLevel(cfg.LogLevel)
	if err != nil {
		level = zerolog.InfoLevel
	}
	logger := log.Level(level).With().Str("env", cfg.Env).Str("app", cfg.AppName).Logger()
	if cfg.Env == "development" {
		logger = logger.Output(zerolog.ConsoleWriter{Out: os.Stdout, TimeFormat: time.RFC822})
	}
	return logger
}
