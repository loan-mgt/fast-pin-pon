package server

import (
    "context"
    "net/http"
    "time"
)

// handleListBuildings godoc
// @Summary      List buildings (fire stations)
// @Description  Returns the list of buildings of type 'station'
// @Tags         buildings
// @Produce      json
// @Success      200 {array} LocationResponse
// @Router       /v1/buildings [get]
func (s *Server) handleListBuildings(w http.ResponseWriter, r *http.Request) {
    ctx := context.Background()
    rows, err := s.pool.Query(ctx, `
        SELECT id::text,
               name,
               type,
               COALESCE(ST_Y(location::geometry)::double precision, 0)::double precision AS latitude,
               COALESCE(ST_X(location::geometry)::double precision, 0)::double precision AS longitude,
               created_at,
               updated_at
        FROM locations
        WHERE type = 'station'
        ORDER BY name ASC;
    `)
    if err != nil {
        s.writeError(w, http.StatusInternalServerError, "failed to query buildings", err.Error())
        return
    }
    defer rows.Close()

    var out []LocationResponse
    for rows.Next() {
        var (
            id string
            name string
            typ string
            lat float64
            lon float64
            createdAt, updatedAt time.Time
        )
        if err := rows.Scan(&id, &name, &typ, &lat, &lon, &createdAt, &updatedAt); err != nil {
            s.writeError(w, http.StatusInternalServerError, "failed to scan row", err.Error())
            return
        }
        out = append(out, LocationResponse{
            ID:   id,
            Name: name,
            Type: typ,
            Location: GeoPoint{Latitude: lat, Longitude: lon},
            CreatedAt: createdAt,
            UpdatedAt: updatedAt,
        })
    }
    if rows.Err() != nil {
        s.writeError(w, http.StatusInternalServerError, "query error", rows.Err().Error())
        return
    }

    s.writeJSON(w, http.StatusOK, out)
}
