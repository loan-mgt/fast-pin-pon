package org.fastpinpon.simulation.model;

import java.time.Instant;

public final class Vehicle {

    public final String unitId;     // API unit id
    public final String callSign;   // API call_sign

    public String unitTypeCode;
    public String homeBase;
    public double lat;
    public double lon;
    public VehicleState etat = VehicleState.DISPONIBLE;
    public Incident currentIncident;
    public String assignmentId;
    public Instant lastUpdate = Instant.now();
    public Instant enRouteSince;
    public Instant returnSince;

    public Vehicle(String unitId, String callSign, String unitTypeCode, String homeBase, double lat, double lon) {
        this.unitId = unitId;
        this.callSign = callSign;
        this.unitTypeCode = unitTypeCode;
        this.homeBase = homeBase;
        this.lat = lat;
        this.lon = lon;
    }
}
