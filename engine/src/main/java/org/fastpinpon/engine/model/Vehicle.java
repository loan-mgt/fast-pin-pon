package org.fastpinpon.simulation.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Vehicle {

    private final String unitId;     // API unit id
    private final String callSign;   // API call_sign

    private String unitTypeCode;
    private String homeBase;
    private double lat;
    private double lon;
    private VehicleState etat = VehicleState.DISPONIBLE;
    private Incident currentIncident;
    private String assignmentId;
    private Instant lastUpdate = Instant.now();
    private Instant enRouteSince;
    private Instant returnSince;
    
    // Route waypoints for road-based navigation
    private List<double[]> currentRoute = new ArrayList<>();
    private int currentWaypointIndex = 0;
    
    // Convoy position: 0 = leader, 1 = second, etc.
    // Used to maintain spacing in convoy formation
    private int convoyPosition = 0;

    public Vehicle(String unitId, String callSign, String unitTypeCode, String homeBase, double lat, double lon) {
        this.unitId = unitId;
        this.callSign = callSign;
        this.unitTypeCode = unitTypeCode;
        this.homeBase = homeBase;
        this.lat = lat;
        this.lon = lon;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getCallSign() {
        return callSign;
    }

    public String getUnitTypeCode() {
        return unitTypeCode;
    }

    public void setUnitTypeCode(String unitTypeCode) {
        this.unitTypeCode = unitTypeCode;
    }

    public String getHomeBase() {
        return homeBase;
    }

    public void setHomeBase(String homeBase) {
        this.homeBase = homeBase;
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

    public VehicleState getEtat() {
        return etat;
    }

    public void setEtat(VehicleState etat) {
        this.etat = etat;
    }

    public Incident getCurrentIncident() {
        return currentIncident;
    }

    public void setCurrentIncident(Incident currentIncident) {
        this.currentIncident = currentIncident;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Instant getEnRouteSince() {
        return enRouteSince;
    }

    public void setEnRouteSince(Instant enRouteSince) {
        this.enRouteSince = enRouteSince;
    }

    public Instant getReturnSince() {
        return returnSince;
    }

    public void setReturnSince(Instant returnSince) {
        this.returnSince = returnSince;
    }

    // Route navigation methods
    
    public List<double[]> getCurrentRoute() {
        return currentRoute;
    }

    public void setCurrentRoute(List<double[]> route) {
        this.currentRoute = route != null ? route : new ArrayList<>();
        this.currentWaypointIndex = 0;
    }

    public void clearRoute() {
        this.currentRoute.clear();
        this.currentWaypointIndex = 0;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    public void setCurrentWaypointIndex(int index) {
        this.currentWaypointIndex = index;
    }

    public boolean hasRoute() {
        return currentRoute != null && !currentRoute.isEmpty();
    }

    public boolean hasNextWaypoint() {
        return hasRoute() && currentWaypointIndex < currentRoute.size();
    }

    public double[] getNextWaypoint() {
        if (!hasNextWaypoint()) {
            return new double[0];
        }
        return currentRoute.get(currentWaypointIndex);
    }

    public void advanceToNextWaypoint() {
        if (hasNextWaypoint()) {
            currentWaypointIndex++;
        }
    }

    public double[] getFinalDestination() {
        if (!hasRoute()) {
            return new double[0];
        }
        return currentRoute.get(currentRoute.size() - 1);
    }
    
    // Convoy position methods
    
    public int getConvoyPosition() {
        return convoyPosition;
    }
    
    public void setConvoyPosition(int convoyPosition) {
        this.convoyPosition = convoyPosition;
    }
}
