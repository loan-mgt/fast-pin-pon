package database

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"fast/pin/internal/config"

	"github.com/jackc/pgx/v5/pgxpool"
	_ "github.com/jackc/pgx/v5/stdlib"
	"github.com/rs/zerolog"
	migrate "github.com/rubenv/sql-migrate"
)

// Connect sets up the database pool and optionally runs migrations.
func Connect(ctx context.Context, cfg config.Config, log zerolog.Logger) (*pgxpool.Pool, error) {
	if cfg.Database.URL == "" {
		return nil, errors.New("database URL is empty")
	}

	if cfg.Database.RunMigrations {
		if err := runMigrations(ctx, cfg, log); err != nil {
			return nil, fmt.Errorf("running migrations: %w", err)
		}
	}

	poolCfg, err := pgxpool.ParseConfig(cfg.Database.URL)
	if err != nil {
		return nil, fmt.Errorf("parsing database config: %w", err)
	}
	poolCfg.MaxConns = cfg.Database.MaxConns
	poolCfg.MaxConnIdleTime = cfg.Database.MaxConnIdleTime
	poolCfg.MaxConnLifetime = cfg.Database.MaxConnLifetime
	if poolCfg.ConnConfig.RuntimeParams == nil {
		poolCfg.ConnConfig.RuntimeParams = map[string]string{}
	}
	poolCfg.ConnConfig.RuntimeParams["application_name"] = cfg.AppName
	poolCfg.ConnConfig.RuntimeParams["timezone"] = "UTC"

	pool, err := pgxpool.NewWithConfig(ctx, poolCfg)
	if err != nil {
		return nil, fmt.Errorf("connecting to database: %w", err)
	}

	return pool, nil
}

func runMigrations(ctx context.Context, cfg config.Config, log zerolog.Logger) error {
	dbConn, err := sql.Open("pgx", cfg.Database.URL)
	if err != nil {
		return fmt.Errorf("opening sql connection: %w", err)
	}
	defer dbConn.Close()

	if err := dbConn.PingContext(ctx); err != nil {
		return fmt.Errorf("ping database: %w", err)
	}

	source := &migrate.FileMigrationSource{Dir: cfg.Database.MigrationsDir}
	n, err := migrate.ExecContext(ctx, dbConn, "postgres", source, migrate.Up)
	if err != nil {
		return err
	}
	if n > 0 {
		log.Info().Int("applied", n).Msg("migrations executed")
	}
	return nil
}
