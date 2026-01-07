package org.fastpinpon.simulation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Incident {

    private UUID id = UUID.randomUUID();
    private final int number;
    private final Instant createdAt = Instant.now();

    private IncidentType type;
    private double lat;
    private double lon;
    private int gravite; // 1..5
    private IncidentState etat = IncidentState.NOUVEAU;
    private Instant lastUpdate = createdAt;
    private String eventId;
    private String interventionId;
    
    // Required unit types for this incident (e.g., ["VSAV", "FPT"])
    // If empty, any available unit type can be dispatched
    private List<String> requiredUnitTypes = new ArrayList<>();

    public Incident(int number, IncidentType type, double lat, double lon, int gravite) {
        this(UUID.randomUUID(), number, type, lat, lon, gravite);
    }

    public Incident(UUID id, int number, IncidentType type, double lat, double lon, int gravite) {
        this.id = id;
        this.number = number;
        this.type = type;
        this.lat = lat;
        this.lon = lon;
        this.gravite = Math.max(1, Math.min(5, gravite));
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getNumber() {
        return number;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public IncidentType getType() {
        return type;
    }

    public void setType(IncidentType type) {
        this.type = type;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public int getGravite() {
        return gravite;
    }

    public void setGravite(int gravite) {
        this.gravite = Math.max(1, Math.min(5, gravite));
    }

    public IncidentState getEtat() {
        return etat;
    }

    public void setEtat(IncidentState etat) {
        this.etat = etat;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getInterventionId() {
        return interventionId;
    }

    public void setInterventionId(String interventionId) {
        this.interventionId = interventionId;
    }

    public List<String> getRequiredUnitTypes() {
        return requiredUnitTypes;
    }

    public void setRequiredUnitTypes(List<String> requiredUnitTypes) {
        this.requiredUnitTypes = requiredUnitTypes != null ? requiredUnitTypes : new ArrayList<>();
    }

    public void addRequiredUnitType(String unitType) {
        if (unitType != null && !unitType.isEmpty() && !requiredUnitTypes.contains(unitType)) {
            requiredUnitTypes.add(unitType);
        }
    }

    public boolean requiresUnitType(String unitType) {
        // If no specific types required, any type is acceptable
        if (requiredUnitTypes.isEmpty()) {
            return true;
        }
        return requiredUnitTypes.contains(unitType);
    }
}
