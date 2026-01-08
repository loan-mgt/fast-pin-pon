package server

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
)

type APIError struct {
	Error   string      `json:"error"`
	Details interface{} `json:"details,omitempty"`
}

const (
	errInvalidPayload        = "invalid payload"
	errInvalidEventID        = "invalid event id"
	errInvalidInterventionID = "invalid intervention id"
	errInvalidUnitID         = "invalid unit id"
)

func (s *Server) writeJSON(w http.ResponseWriter, status int, payload interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if payload != nil {
		_ = json.NewEncoder(w).Encode(payload)
	}
}

func (s *Server) writeError(w http.ResponseWriter, status int, message string, details interface{}) {
	s.writeJSON(w, status, APIError{Error: message, Details: details})
}

func (s *Server) decodeAndValidate(r *http.Request, dst interface{}) error {
	defer r.Body.Close()
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(dst); err != nil {
		return err
	}
	if err := s.validate.Struct(dst); err != nil {
		return err
	}
	return nil
}

func (s *Server) parseUUIDParam(r *http.Request, key string) (pgtype.UUID, error) {
	raw := chi.URLParam(r, key)
	if strings.TrimSpace(raw) == "" {
		return pgtype.UUID{}, errors.New("missing id")
	}
	parsed, err := uuid.Parse(raw)
	if err != nil {
		return pgtype.UUID{}, err
	}
	var arr [16]byte
	copy(arr[:], parsed[:])
	return pgtype.UUID{Bytes: arr, Valid: true}, nil
}

func mustUUID(id uuid.UUID) pgtype.UUID {
	var arr [16]byte
	copy(arr[:], id[:])
	return pgtype.UUID{Bytes: arr, Valid: true}
}

func timestamptzPtr(ts pgtype.Timestamptz) *time.Time {
	if !ts.Valid {
		return nil
	}
	t := ts.Time
	return &t
}

func optionalString(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}

func (s *Server) paginate(r *http.Request, defaultLimit int32) (limit int32, offset int32) {
	query := r.URL.Query()
	limit = defaultLimit
	offset = 0
	if l := query.Get("limit"); l != "" {
		if parsed, err := parseInt32(l); err == nil && parsed > 0 {
			limit = parsed
		}
	}
	if o := query.Get("offset"); o != "" {
		if parsed, err := parseInt32(o); err == nil && parsed >= 0 {
			offset = parsed
		}
	}
	return
}

func parseInt32(value string) (int32, error) {
	if strings.TrimSpace(value) == "" {
		return 0, errors.New("empty value")
	}
	n64, err := strconv.ParseInt(value, 10, 32)
	if err != nil {
		return 0, err
	}
	return int32(n64), nil
}

func uuidString(u pgtype.UUID) string {
	if !u.Valid {
		return ""
	}
	raw := u.Bytes
	id, err := uuid.FromBytes(raw[:])
	if err != nil {
		return ""
	}
	return id.String()
}

func uuidStringOptional(u pgtype.UUID) string {
	if !u.Valid {
		return ""
	}
	raw := u.Bytes
	id, err := uuid.FromBytes(raw[:])
	if err != nil {
		return ""
	}
	return id.String()
}

func pgUUIDFromStringOptional(value *string) pgtype.UUID {
	if value == nil || *value == "" {
		return pgtype.UUID{}
	}
	parsed, err := uuid.Parse(strings.TrimSpace(*value))
	if err != nil {
		return pgtype.UUID{}
	}
	var arr [16]byte
	copy(arr[:], parsed[:])
	return pgtype.UUID{Bytes: arr, Valid: true}
}

func timestamptzFromPtr(t *time.Time) pgtype.Timestamptz {
	if t == nil {
		return pgtype.Timestamptz{}
	}
	return pgtype.Timestamptz{Time: t.UTC(), Valid: true}
}

func rawJSONOrEmpty(data RawJSON) []byte {
	if len(data) == 0 {
		return []byte("{}")
	}
	return []byte(data)
}

func pgUUIDFromString(value string) (pgtype.UUID, error) {
	parsed, err := uuid.Parse(strings.TrimSpace(value))
	if err != nil {
		return pgtype.UUID{}, err
	}
	var arr [16]byte
	copy(arr[:], parsed[:])
	return pgtype.UUID{Bytes: arr, Valid: true}, nil
}

func isNotFound(err error) bool {
	return errors.Is(err, pgx.ErrNoRows)
}

func isUniqueViolation(err error) bool {
	if err == nil {
		return false
	}
	// PostgreSQL unique violation error code is 23505
	return strings.Contains(err.Error(), "23505") ||
		strings.Contains(err.Error(), "unique constraint") ||
		strings.Contains(err.Error(), "duplicate key")
}
