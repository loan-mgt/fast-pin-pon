package config

import (
	"time"

	"github.com/caarlos0/env/v10"
)

// Config centralises every runtime setting so the rest of the codebase can remain deterministic
// and easy to test. All fields can be overridden using environment variables.
type Config struct {
	AppName  string         `env:"APP_NAME" envDefault:"fast-pin-pon-api"`
	Env      string         `env:"APP_ENV" envDefault:"development"`
	LogLevel string         `env:"LOG_LEVEL" envDefault:"info"`
	LogFile  string         `env:"LOG_FILE" envDefault:"logs/api.log"`
	HTTP     HTTPConfig     `envPrefix:"HTTP_"`
	Database DatabaseConfig `envPrefix:"DB_"`
}

// HTTPConfig controls the HTTP server behaviour.
type HTTPConfig struct {
	Address      string        `env:"ADDRESS" envDefault:":8080"`
	ReadTimeout  time.Duration `env:"READ_TIMEOUT" envDefault:"15s"`
	WriteTimeout time.Duration `env:"WRITE_TIMEOUT" envDefault:"15s"`
	IdleTimeout  time.Duration `env:"IDLE_TIMEOUT" envDefault:"60s"`
}

// DatabaseConfig groups the Postgres/PostGIS settings.
type DatabaseConfig struct {
	URL             string        `env:"URL" envDefault:"postgres://postgres:postgres@localhost:5432/fastpinpon?sslmode=disable"`
	RunMigrations   bool          `env:"RUN_MIGRATIONS" envDefault:"true"`
	MigrationsDir   string        `env:"MIGRATIONS_DIR" envDefault:"migrations"`
	MaxConns        int32         `env:"MAX_CONNS" envDefault:"20"`
	MaxConnIdleTime time.Duration `env:"MAX_CONN_IDLE_TIME" envDefault:"5m"`
	MaxConnLifetime time.Duration `env:"MAX_CONN_LIFETIME" envDefault:"30m"`
}

// Load reads configuration from the environment, applying defaults defined above.
func Load() (Config, error) {
	var cfg Config
	if err := env.Parse(&cfg); err != nil {
		return Config{}, err
	}
	return cfg, nil
}
