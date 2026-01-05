package org.fastpinpon.simulation.model;

import java.time.Instant;
import java.util.UUID;

public final class Incident {

    public final UUID id = UUID.randomUUID();
    public final int number;
    public final Instant createdAt = Instant.now();

    public IncidentType type;
    public double lat;
    public double lon;
    public int gravite; // 1..5
    public IncidentState etat = IncidentState.NOUVEAU;
    public Instant lastUpdate = createdAt;
    public String eventId;
    public String interventionId;

    public Incident(int number, IncidentType type, double lat, double lon, int gravite) {
        this.number = number;
        this.type = type;
        this.lat = lat;
        this.lon = lon;
        this.gravite = Math.max(1, Math.min(5, gravite));
    }
}
