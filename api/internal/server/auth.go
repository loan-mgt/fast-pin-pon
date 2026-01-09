package server

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"time"

	"fast/pin/internal/config"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
	"github.com/rs/zerolog"
)

// contextKey is a private type for context keys to avoid collisions.
type contextKey string

const (
	// UserContextKey is the context key for storing user claims.
	UserContextKey contextKey = "user"
)

// UserClaims represents the JWT claims from Keycloak.
type UserClaims struct {
	jwt.RegisteredClaims
	PreferredUsername string   `json:"preferred_username"`
	Email             string   `json:"email"`
	RealmAccess       struct {
		Roles []string `json:"roles"`
	} `json:"realm_access"`
}

// AuthMiddleware handles JWT validation using Keycloak's JWKS.
type AuthMiddleware struct {
	jwks         keyfunc.Keyfunc
	cancelFn     context.CancelFunc
	validIssuers []string
	log          zerolog.Logger
}

// NewAuthMiddleware creates a new authentication middleware with JWKS from Keycloak.
func NewAuthMiddleware(ctx context.Context, cfg config.KeycloakConfig, log zerolog.Logger) (*AuthMiddleware, error) {
	jwksURL := fmt.Sprintf("%s/realms/%s/protocol/openid-connect/certs", cfg.URL, cfg.Realm)

	// Create a cancellable context for JWKS refresh goroutine
	jwksCtx, cancelFn := context.WithCancel(ctx)

	// Create JWKS with automatic refresh
	jwks, err := keyfunc.NewDefaultCtx(jwksCtx, []string{jwksURL})
	if err != nil {
		cancelFn()
		return nil, fmt.Errorf("failed to create JWKS from %s: %w", jwksURL, err)
	}

	// Accept tokens from both internal and public Keycloak URLs
	internalIssuer := fmt.Sprintf("%s/realms/%s", cfg.URL, cfg.Realm)
	publicIssuer := fmt.Sprintf("%s/realms/%s", cfg.PublicURL, cfg.Realm)
	// Also allow localhost:8080 for dev environment quirks
	devIssuer := fmt.Sprintf("http://localhost:8080/realms/%s", cfg.Realm)
	validIssuers := []string{internalIssuer, publicIssuer, devIssuer}

	log.Info().
		Str("jwks_url", jwksURL).
		Strs("valid_issuers", validIssuers).
		Msg("JWT authentication middleware initialized")

	return &AuthMiddleware{
		jwks:         jwks,
		cancelFn:     cancelFn,
		validIssuers: validIssuers,
		log:          log,
	}, nil
}

// Close releases resources used by the auth middleware.
func (a *AuthMiddleware) Close() {
	if a.cancelFn != nil {
		a.cancelFn()
	}
}

// Middleware returns an HTTP middleware that validates JWT tokens.
func (a *AuthMiddleware) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token, err := a.extractAndValidateToken(r)
		if err != nil {
			a.log.Debug().Err(err).Str("path", r.URL.Path).Msg("authentication failed")
			http.Error(w, "Unauthorized", http.StatusUnauthorized)
			return
		}

		claims, ok := token.Claims.(*UserClaims)
		if !ok {
			a.log.Debug().Msg("failed to extract claims from token")
			http.Error(w, "Unauthorized", http.StatusUnauthorized)
			return
		}

		// Check for api-access role
		if !a.hasRole(claims, "api-access") {
			a.log.Debug().
				Str("username", claims.PreferredUsername).
				Strs("roles", claims.RealmAccess.Roles).
				Msg("user lacks api-access role")
			http.Error(w, "Forbidden: missing api-access role", http.StatusForbidden)
			return
		}

		// Add user claims to context
		ctx := context.WithValue(r.Context(), UserContextKey, claims)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// extractAndValidateToken extracts and validates the JWT from the Authorization header.
func (a *AuthMiddleware) extractAndValidateToken(r *http.Request) (*jwt.Token, error) {
	authHeader := r.Header.Get("Authorization")
	if authHeader == "" {
		return nil, fmt.Errorf("missing Authorization header")
	}

	parts := strings.Split(authHeader, " ")
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
		return nil, fmt.Errorf("invalid Authorization header format")
	}

	tokenString := parts[1]

	token, err := jwt.ParseWithClaims(tokenString, &UserClaims{}, a.jwks.Keyfunc,
		jwt.WithExpirationRequired(),
		jwt.WithLeeway(5*time.Second),
	)
	if err != nil {
		return nil, fmt.Errorf("invalid token: %w", err)
	}

	if !token.Valid {
		return nil, fmt.Errorf("token is not valid")
	}

	// Validate issuer against allowed list
	claims, ok := token.Claims.(*UserClaims)
	if !ok {
		return nil, fmt.Errorf("failed to extract claims")
	}

	issuerValid := false
	for _, validIssuer := range a.validIssuers {
		if claims.Issuer == validIssuer {
			issuerValid = true
			break
		}
	}
	if !issuerValid {
		return nil, fmt.Errorf("invalid issuer: %s", claims.Issuer)
	}

	return token, nil
}

// hasRole checks if the user has a specific realm role.
func (a *AuthMiddleware) hasRole(claims *UserClaims, role string) bool {
	for _, r := range claims.RealmAccess.Roles {
		if r == role {
			return true
		}
	}
	return false
}

// GetUserFromContext retrieves the user claims from the request context.
func GetUserFromContext(ctx context.Context) (*UserClaims, bool) {
	claims, ok := ctx.Value(UserContextKey).(*UserClaims)
	return claims, ok
}
