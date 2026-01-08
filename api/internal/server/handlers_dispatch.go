package server

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"

	db "fast/pin/internal/db/sqlc"
)

// =============================================================================
// Dispatch Configuration Handlers
// =============================================================================

// handleGetDispatchConfig returns all dispatch configuration parameters.
// @Summary Get dispatch configuration
// @Description Returns all tunable weights and thresholds for the decision engine
// @Tags dispatch
// @Produce json
// @Success 200 {object} DispatchConfigResponse
// @Failure 500 {object} APIError
// @Router /v1/dispatch/config [get]
func (s *Server) handleGetDispatchConfig(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	configs, err := s.queries.ListDispatchConfig(ctx)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch dispatch config", err.Error())
		return
	}

	items := make([]DispatchConfigItem, 0, len(configs))
	for _, c := range configs {
		items = append(items, mapDispatchConfigToDTO(c))
	}

	s.writeJSON(w, http.StatusOK, DispatchConfigResponse{Items: items})
}

// handleUpdateDispatchConfig updates a single dispatch configuration parameter.
// @Summary Update dispatch configuration
// @Description Updates a single weight or threshold value. Triggers engine refresh.
// @Tags dispatch
// @Accept json
// @Produce json
// @Param body body UpdateDispatchConfigRequest true "Config update"
// @Success 200 {object} DispatchConfigItem
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/dispatch/config [put]
func (s *Server) handleUpdateDispatchConfig(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	var req UpdateDispatchConfigRequest
	if err := s.decodeAndValidate(r, &req); err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidPayload, err.Error())
		return
	}

	// Convert float64 to pgtype.Numeric
	numericValue := pgtype.Numeric{}
	if err := numericValue.Scan(fmt.Sprintf("%f", req.Value)); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid numeric value", err.Error())
		return
	}

	updated, err := s.queries.UpdateDispatchConfigValue(ctx, db.UpdateDispatchConfigValueParams{
		Key:   req.Key,
		Value: numericValue,
	})
	if err != nil {
		if err == pgx.ErrNoRows {
			s.writeError(w, http.StatusNotFound, "config key not found", req.Key)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to update config", err.Error())
		return
	}

	// Trigger engine refresh asynchronously
	go s.notifyEngineRefresh(context.Background())

	s.writeJSON(w, http.StatusOK, mapDispatchConfigToDTO(updated))
}

// =============================================================================
// Static Data Handler (Engine Startup)
// =============================================================================

// handleGetDispatchStatic returns all static data for engine initialization.
// @Summary Get static dispatch data
// @Description Returns all configuration, unit types, event types, and bases for engine startup caching
// @Tags dispatch
// @Produce json
// @Success 200 {object} StaticDataResponse
// @Failure 500 {object} APIError
// @Router /v1/dispatch/static [get]
func (s *Server) handleGetDispatchStatic(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	// Fetch all static data in parallel
	type result struct {
		configs    []db.DispatchConfig
		unitTypes  []db.UnitType
		eventTypes []db.EventType
		bases      []db.ListBasesRow
		err        error
	}

	ch := make(chan result, 1)
	go func() {
		var res result
		res.configs, res.err = s.queries.ListDispatchConfig(ctx)
		if res.err != nil {
			ch <- res
			return
		}
		res.unitTypes, res.err = s.queries.ListUnitTypes(ctx)
		if res.err != nil {
			ch <- res
			return
		}
		res.eventTypes, res.err = s.queries.ListEventTypes(ctx)
		if res.err != nil {
			ch <- res
			return
		}
		res.bases, res.err = s.queries.ListBases(ctx)
		ch <- res
	}()

	res := <-ch
	if res.err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch static data", res.err.Error())
		return
	}

	// Map to DTOs
	configItems := make([]DispatchConfigItem, 0, len(res.configs))
	for _, c := range res.configs {
		configItems = append(configItems, mapDispatchConfigToDTO(c))
	}

	unitTypes := make([]UnitTypeResponse, 0, len(res.unitTypes))
	for _, ut := range res.unitTypes {
		unitTypes = append(unitTypes, UnitTypeResponse{
			Code:         ut.Code,
			Name:         ut.Name,
			Capabilities: ut.Capabilities,
			SpeedKMH:     ut.SpeedKmh,
			MaxCrew:      ut.MaxCrew,
			Illustration: optionalString(ut.Illustration),
		})
	}

	eventTypes := make([]EventTypeResponse, 0, len(res.eventTypes))
	for _, et := range res.eventTypes {
		eventTypes = append(eventTypes, EventTypeResponse{
			Code:                 et.Code,
			Name:                 et.Name,
			Description:          et.Description,
			DefaultSeverity:      et.DefaultSeverity,
			RecommendedUnitTypes: et.RecommendedUnitTypes,
		})
	}

	bases := make([]BaseInfo, 0, len(res.bases))
	for _, b := range res.bases {
		name := ""
		if b.Name != nil {
			name = *b.Name
		}
		bases = append(bases, BaseInfo{
			Name:           name,
			AvailableUnits: b.AvailableUnits,
			TotalUnits:     b.TotalUnits,
		})
	}

	s.writeJSON(w, http.StatusOK, StaticDataResponse{
		Config:     configItems,
		UnitTypes:  unitTypes,
		EventTypes: eventTypes,
		Bases:      bases,
	})
}

// =============================================================================
// Dispatch Candidates Handler
// =============================================================================

// handleGetDispatchCandidates returns candidate units for an intervention.
// @Summary Get dispatch candidates
// @Description Returns candidate units ranked by estimated travel time with scoring info
// @Tags dispatch
// @Produce json
// @Param interventionID path string true "Intervention ID"
// @Success 200 {object} DispatchCandidatesResponse
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/interventions/{interventionID}/candidates [get]
func (s *Server) handleGetDispatchCandidates(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidInterventionID, err.Error())
		return
	}

	// Get intervention details
	intervention, err := s.queries.GetInterventionForDispatch(ctx, interventionID)
	if err != nil {
		if err == pgx.ErrNoRows {
			s.writeError(w, http.StatusNotFound, "intervention not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to fetch intervention", err.Error())
		return
	}

	// Get max candidates from config (default 10)
	maxCandidates := int32(10)
	if cfg, err := s.queries.GetDispatchConfigValue(ctx, "max_candidates_per_dispatch"); err == nil {
		if v, err := numericToFloat64(cfg.Value); err == nil && v > 0 {
			maxCandidates = int32(v)
		}
	}

	// Fetch candidates
	candidates, err := s.queries.ListDispatchCandidates(ctx, db.ListDispatchCandidatesParams{
		InterventionID: interventionID,
		UnitTypes:      intervention.RecommendedUnitTypes,
		MaxCandidates:  maxCandidates,
	})
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch candidates", err.Error())
		return
	}

	// Map to DTOs
	candidateDTOs := make([]DispatchCandidate, 0, len(candidates))
	for _, c := range candidates {
		candidateDTOs = append(candidateDTOs, mapCandidateToDTO(c))
	}

	s.writeJSON(w, http.StatusOK, DispatchCandidatesResponse{
		InterventionID:       uuidToString(intervention.InterventionID),
		EventSeverity:        intervention.EventSeverity,
		RecommendedUnitTypes: intervention.RecommendedUnitTypes,
		Candidates:           candidateDTOs,
	})
}

// =============================================================================
// Pending Interventions Handler
// =============================================================================

// handleListPendingInterventions returns interventions awaiting dispatch.
// @Summary List pending interventions
// @Description Returns interventions in planned/created/en_route status for periodic dispatch
// @Tags dispatch
// @Produce json
// @Success 200 {object} PendingInterventionsResponse
// @Failure 500 {object} APIError
// @Router /v1/dispatch/pending [get]
func (s *Server) handleListPendingInterventions(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	rows, err := s.queries.ListPendingInterventions(ctx)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "failed to fetch pending interventions", err.Error())
		return
	}

	interventions := make([]PendingIntervention, 0, len(rows))
	for _, row := range rows {
		interventions = append(interventions, PendingIntervention{
			InterventionID:     uuidToString(row.InterventionID),
			EventID:            uuidToString(row.EventID),
			Status:             string(row.InterventionStatus),
			Priority:           row.Priority,
			EventSeverity:      row.EventSeverity,
			EventTypeCode:      row.EventTypeCode,
			RecommendedTypes:   row.RecommendedUnitTypes,
			Location:           GeoPoint{Latitude: row.Latitude, Longitude: row.Longitude},
			AssignedUnitsCount: row.AssignedUnitsCount,
			CreatedAt:          row.CreatedAt.Time,
		})
	}

	s.writeJSON(w, http.StatusOK, PendingInterventionsResponse{Interventions: interventions})
}

// =============================================================================
// Intervention Dispatch Info Handler
// =============================================================================

// handleGetInterventionDispatchInfo returns dispatch details for an intervention.
// @Summary Get intervention dispatch info
// @Description Returns intervention details needed for dispatch decision
// @Tags dispatch
// @Produce json
// @Param interventionID path string true "Intervention ID"
// @Success 200 {object} InterventionDispatchInfo
// @Failure 400 {object} APIError
// @Failure 404 {object} APIError
// @Failure 500 {object} APIError
// @Router /v1/interventions/{interventionID}/dispatch-info [get]
func (s *Server) handleGetInterventionDispatchInfo(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()

	interventionID, err := s.parseUUIDParam(r, "interventionID")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, errInvalidInterventionID, err.Error())
		return
	}

	row, err := s.queries.GetInterventionForDispatch(ctx, interventionID)
	if err != nil {
		if err == pgx.ErrNoRows {
			s.writeError(w, http.StatusNotFound, "intervention not found", nil)
			return
		}
		s.writeError(w, http.StatusInternalServerError, "failed to fetch intervention", err.Error())
		return
	}

	s.writeJSON(w, http.StatusOK, InterventionDispatchInfo{
		InterventionID:       uuidToString(row.InterventionID),
		EventID:              uuidToString(row.EventID),
		Status:               string(row.InterventionStatus),
		Priority:             row.Priority,
		DecisionMode:         string(row.DecisionMode),
		EventTitle:           row.EventTitle,
		EventSeverity:        row.EventSeverity,
		EventTypeCode:        row.EventTypeCode,
		RecommendedUnitTypes: row.RecommendedUnitTypes,
		Location:             GeoPoint{Latitude: row.Latitude, Longitude: row.Longitude},
	})
}

// =============================================================================
// Helper Functions
// =============================================================================

func mapDispatchConfigToDTO(c db.DispatchConfig) DispatchConfigItem {
	value, _ := numericToFloat64(c.Value)
	item := DispatchConfigItem{
		Key:         c.Key,
		Value:       value,
		Description: c.Description,
		UpdatedAt:   c.UpdatedAt.Time,
	}
	if c.MinValue.Valid {
		if v, err := numericToFloat64(c.MinValue); err == nil {
			item.MinValue = &v
		}
	}
	if c.MaxValue.Valid {
		if v, err := numericToFloat64(c.MaxValue); err == nil {
			item.MaxValue = &v
		}
	}
	return item
}

func mapCandidateToDTO(c db.ListDispatchCandidatesRow) DispatchCandidate {
	dto := DispatchCandidate{
		ID:                uuidToString(c.ID),
		CallSign:          c.CallSign,
		UnitTypeCode:      c.UnitTypeCode,
		HomeBase:          optionalString(c.HomeBase),
		Status:            string(c.Status),
		Location:          GeoPoint{Latitude: c.Latitude, Longitude: c.Longitude},
		TravelTimeSeconds: c.TravelTimeSeconds,
		DistanceMeters:    c.DistanceMeters,
		OtherUnitsAtBase:  int(c.OtherUnitsAtBase),
	}

	if c.CurrentAssignmentID.Valid {
		id := uuidToString(c.CurrentAssignmentID)
		dto.CurrentAssignmentID = &id
	}
	if c.CurrentInterventionID.Valid {
		id := uuidToString(c.CurrentInterventionID)
		dto.CurrentInterventionID = &id
	}
	if c.CurrentInterventionSeverity != nil {
		dto.CurrentInterventionSeverity = c.CurrentInterventionSeverity
	}
	if c.CurrentInterventionPriority != nil {
		dto.CurrentInterventionPriority = c.CurrentInterventionPriority
	}

	return dto
}

func uuidToString(u pgtype.UUID) string {
	if !u.Valid {
		return ""
	}
	return uuid.UUID(u.Bytes).String()
}

func numericToFloat64(n pgtype.Numeric) (float64, error) {
	if !n.Valid {
		return 0, fmt.Errorf("numeric is null")
	}
	f, err := n.Float64Value()
	if err != nil {
		return 0, err
	}
	return f.Float64, nil
}

// =============================================================================
// Engine Client
// =============================================================================

// notifyEngineRefresh sends a refresh signal to the decision engine.
func (s *Server) notifyEngineRefresh(ctx context.Context) {
	engineURL := s.cfg.EngineURL
	if engineURL == "" {
		s.log.Debug().Msg("Engine URL not set, skipping engine refresh notification")
		return
	}

	client := &http.Client{Timeout: 5 * time.Second}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, engineURL+"/refresh", nil)
	if err != nil {
		s.log.Warn().Err(err).Msg("failed to create engine refresh request")
		return
	}

	resp, err := client.Do(req)
	if err != nil {
		s.log.Warn().Err(err).Msg("failed to notify engine of config refresh")
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		s.log.Warn().Int("status", resp.StatusCode).Msg("engine refresh returned non-OK status")
		return
	}

	s.log.Info().Msg("engine config refresh triggered successfully")
}

// notifyEngineDispatch sends a dispatch trigger to the decision engine.
func (s *Server) notifyEngineDispatch(ctx context.Context, interventionID string) {
	engineURL := s.cfg.EngineURL
	if engineURL == "" {
		s.log.Debug().Msg("Engine URL not set, skipping engine dispatch notification")
		return
	}

	client := &http.Client{Timeout: 10 * time.Second}
	url := fmt.Sprintf("%s/dispatch/%s", engineURL, interventionID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, nil)
	if err != nil {
		s.log.Warn().Err(err).Msg("failed to create engine dispatch request")
		return
	}

	resp, err := client.Do(req)
	if err != nil {
		s.log.Warn().Err(err).Str("intervention_id", interventionID).Msg("failed to notify engine for dispatch")
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		s.log.Warn().Int("status", resp.StatusCode).Str("intervention_id", interventionID).Msg("engine dispatch returned non-OK status")
		return
	}

	s.log.Info().Str("intervention_id", interventionID).Msg("engine dispatch triggered successfully")
}
