package org.fastpinpon.simulation.engine;

import org.fastpinpon.simulation.api.ApiClient;
import org.fastpinpon.simulation.model.BaseLocation;
import org.fastpinpon.simulation.model.Incident;
import org.fastpinpon.simulation.model.IncidentState;
import org.fastpinpon.simulation.model.Vehicle;
import org.fastpinpon.simulation.model.VehicleState;
import org.fastpinpon.simulation.routing.RoutingService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decision engine for incident response.
 * 
 * This engine is responsible for:
 * - Registering incidents (from any source) into the API
 * - Deciding how many and which units to dispatch
 * - Managing incident lifecycle (in progress, resolved)
 * - Tracking vehicle states and movements
 * 
 * The engine is decoupled from incident generation - it can handle incidents
 * from random simulation, real API events, or any other source implementing
 * the IncidentSource interface.
 */
public final class DecisionEngine {
    private static final Logger LOG = Logger.getLogger(DecisionEngine.class.getName());

    private final ApiClient api;
    private final List<Incident> activeIncidents = new ArrayList<>();
    private final List<Vehicle> vehicles;
    private final RoutingService routingService = new RoutingService();

    // Distance thresholds in METERS
    private static final double ARRIVAL_THRESHOLD_METERS = 30.0;  // 30 meters to trigger arrival
    private static final double WAYPOINT_THRESHOLD_METERS = 25.0; // 25 meters to advance waypoint
    
    // Movement speed configuration for emergency vehicles
    // Fast simulation speed: ~0.001 degrees per tick = ~111 meters per tick
    // At 1 tick per second = 111 m/s = ~400 km/h (accelerated for visible simulation)
    // For real-time 90 km/h, use 0.00025 instead
    private static final double MOVEMENT_STEP = 0.001;  // ~111 meters per tick (fast simulation)
    
    // Timing constants for incident lifecycle
    // Time after all units arrive before incident auto-resolves (in seconds)
    private static final long INCIDENT_RESOLVE_AFTER_ARRIVAL_SECONDS = 45;

    private static final String SIM_INCIDENT_PREFIX = "[SIM] Incident ";

    // Base names
    private static final String BASE_VILLEURBANNE = "Villeurbanne";
    private static final String BASE_CONFLUENCE = "Lyon Confluence";
    private static final String BASE_PART_DIEU = "Lyon Part-Dieu";
    private static final String BASE_CUSSET = "Cusset";

    // Backend status constants
    private static final String ASSIGNMENT_STATUS_ARRIVED = "arrived";
    private static final String ASSIGNMENT_STATUS_RELEASED = "released";
    private static final String INTERVENTION_STATUS_EN_ROUTE = "created";
    private static final String INTERVENTION_STATUS_ON_SITE = "on_site";
    private static final String INTERVENTION_STATUS_COMPLETED = "completed";

    // Unit statuses
    private static final String UNIT_STATUS_AVAILABLE = "available";
    private static final String UNIT_STATUS_EN_ROUTE = "under_way";
    private static final String UNIT_STATUS_ON_SITE = "on_site";

    private static final boolean PATCH_ASSIGNMENT_STATUS = true;
    private static final boolean PATCH_INTERVENTION_STATUS = true;

    private static final DateTimeFormatter INTERVENTION_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final double CITY_CENTER_LAT = 45.75;
    private static final double CITY_CENTER_LON = 4.85;

    private static final BaseLocation[] BASES = new BaseLocation[]{
            new BaseLocation(BASE_VILLEURBANNE, 45.766180, 4.878770),
            new BaseLocation(BASE_CONFLUENCE, 45.741054, 4.823733),
            new BaseLocation(BASE_PART_DIEU, 45.760540, 4.861700),
            new BaseLocation(BASE_CUSSET, 45.76623, 4.89534),
    };

    // Base priority is now computed dynamically based on distance to incident

    /**
     * Create a decision engine with the given API client and vehicle fleet.
     * 
     * @param api the API client for backend communication
     * @param vehicles the fleet of vehicles to manage
     */
    public DecisionEngine(ApiClient api, List<Vehicle> vehicles) {
        this.api = api;
        this.vehicles = vehicles;
    }

    /**
     * Process a new incident: register it in the API and dispatch units.
     * This is the main entry point for handling incidents from any source.
     * 
     * @param incident the incident to process
     * @return true if the incident was successfully registered and dispatched
     */
    public boolean processNewIncident(Incident incident) {
        // Register the incident as an event in the API
        String eventId = api.createEvent(
                incident.getType(), 
                incident.getNumber(), 
                incident.getLat(), 
                incident.getLon(), 
                incident.getGravite()
        );
        
        if (eventId == null) {
            LOG.warning("[Decision] Failed to create event for incident " + incident.getNumber());
            return false;
        }

        // Update the incident with the event ID
        try {
            UUID uuid = UUID.fromString(eventId);
            incident.setId(uuid);
        } catch (Exception e) {
            LOG.warning("[Decision] Failed to parse eventId as UUID: " + eventId);
            return false;
        }
        incident.setEventId(eventId);
        activeIncidents.add(incident);

        logNewIncident(incident);

        // Create an intervention and dispatch units
        String interventionId = api.createIntervention(eventId, incident.getGravite());
        if (interventionId != null) {
            incident.setInterventionId(interventionId);
            String ts = INTERVENTION_TS.format(Instant.now());
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, "[Decision] Intervention created at {0} for incident {1} in {2}",
                        new Object[]{ts, incident.getNumber(), nearestBaseName(incident.getLat(), incident.getLon())});
            }
            dispatchUnits(interventionId, incident);
        }

        return true;
    }

    /**
     * Process an incident that already exists in the API (e.g., real event).
     * Use this when the event is already registered and you only need to dispatch units.
     * 
     * @param incident the incident with eventId already set
     * @return true if dispatch was successful
     */
    public boolean processExistingIncident(Incident incident) {
        if (incident.getEventId() == null) {
            LOG.warning("[Decision] Cannot process incident without eventId");
            return false;
        }

        activeIncidents.add(incident);
        logNewIncident(incident);

        // Create intervention if not already present
        if (incident.getInterventionId() == null) {
            String interventionId = api.createIntervention(incident.getEventId(), incident.getGravite());
            if (interventionId != null) {
                incident.setInterventionId(interventionId);
            }
        }

        if (incident.getInterventionId() != null) {
            dispatchUnits(incident.getInterventionId(), incident);
        }

        return true;
    }

    /**
     * Advance the simulation state: update incident and vehicle states.
     * Should be called periodically (e.g., every tick).
     */
    public void tick() {
        advanceIncidents();
        advanceVehicles();
        cleanupResolvedIncidents();
    }

    /**
     * Get all active (non-resolved) incidents.
     * 
     * @return list of active incidents
     */
    public List<Incident> getActiveIncidents() {
        List<Incident> result = new ArrayList<>();
        for (Incident inc : activeIncidents) {
            if (inc.getEtat() != IncidentState.RESOLU) {
                result.add(inc);
            }
        }
        return result;
    }

    /**
     * Get the count of active (non-available) vehicles.
     * 
     * @return number of vehicles currently assigned to incidents
     */
    public int getActiveVehicleCount() {
        int count = 0;
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() != null && v.getCurrentIncident().getEtat() != IncidentState.RESOLU) {
                count++;
            }
        }
        return count;
    }

    /**
     * Push telemetry for all vehicles and incidents to the API.
     */
    public void pushTelemetry() {
        for (Vehicle v : vehicles) {
            api.updateUnitStatus(v.getUnitId(), mapVehicleState(v.getEtat()));
            api.updateUnitLocation(v.getUnitId(), v.getLat(), v.getLon(), v.getLastUpdate());
        }
        for (Incident inc : activeIncidents) {
            if (inc.getEventId() != null) {
                api.logHeartbeat(inc.getEventId());
            }
        }
    }

    // =========================================================================
    // Decision Logic
    // =========================================================================

    private void dispatchUnits(String interventionId, Incident incident) {
        List<String> basePriority = getBasesByDistance(incident.getLat(), incident.getLon());
        List<Vehicle> availableVehicles = findAvailableVehicles(incident);
        
        if (availableVehicles.isEmpty()) {
            LOG.log(Level.INFO, "[Decision] No available units for incident {0}", incident.getId());
            return;
        }

        sortVehiclesByPriority(availableVehicles, basePriority, incident);
        dispatchVehiclesToIncident(availableVehicles, incident, interventionId, basePriority);
    }
    
    private List<Vehicle> findAvailableVehicles(Incident incident) {
        List<Vehicle> matching = new ArrayList<>();
        List<Vehicle> fallback = new ArrayList<>();
        
        for (Vehicle v : vehicles) {
            if (v.getEtat() == VehicleState.DISPONIBLE) {
                if (incident.requiresUnitType(v.getUnitTypeCode())) {
                    matching.add(v);
                }
                fallback.add(v);
            }
        }
        
        if (!matching.isEmpty()) {
            return matching;
        }
        if (!fallback.isEmpty()) {
            LOG.log(Level.WARNING, "[Decision] No units matching required types {0}, using any available", 
                    incident.getRequiredUnitTypes());
        }
        return fallback;
    }
    
    private void sortVehiclesByPriority(List<Vehicle> vehicles, List<String> basePriority, Incident incident) {
        vehicles.sort((a, b) -> {
            int pa = getBasePriorityIndex(a.getHomeBase(), basePriority);
            int pb = getBasePriorityIndex(b.getHomeBase(), basePriority);
            if (pa != pb) {
                return Integer.compare(pa, pb);
            }
            return Double.compare(
                    distance(a.getLat(), a.getLon(), incident.getLat(), incident.getLon()),
                    distance(b.getLat(), b.getLon(), incident.getLat(), incident.getLon())
            );
        });
    }
    
    private int getBasePriorityIndex(String homeBase, List<String> basePriority) {
        int index = basePriority.indexOf(homeBase);
        return index == -1 ? basePriority.size() : index;
    }
    
    private void dispatchVehiclesToIncident(List<Vehicle> availableVehicles, Incident incident, 
                                            String interventionId, List<String> basePriority) {
        int needed = calculateUnitsNeeded(incident);
        int toDispatch = Math.min(needed, availableVehicles.size());

        logDispatchDecision(toDispatch, incident, basePriority);

        for (int i = 0; i < toDispatch; i++) {
            Vehicle v = availableVehicles.get(i);
            assignVehicleToIncident(v, incident, interventionId);
            sleepBetweenDispatches();
        }

        if (incident.getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
            api.updateInterventionStatus(incident.getInterventionId(), INTERVENTION_STATUS_EN_ROUTE);
        }
    }
    
    private void logDispatchDecision(int toDispatch, Incident incident, List<String> basePriority) {
        if (LOG.isLoggable(Level.INFO)) {
            String nearestBase = basePriority.isEmpty() ? "unknown" : basePriority.get(0);
            String typeReq = incident.getRequiredUnitTypes().isEmpty() ? "any" : incident.getRequiredUnitTypes().toString();
            LOG.log(Level.INFO, "[Decision] Dispatching {0} units for incident {1} (nearest base: {2}, types: {3})",
                    new Object[]{toDispatch, incident.getNumber(), nearestBase, typeReq});
        }
    }
    
    private void sleepBetweenDispatches() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Calculate how many units are needed for an incident.
     * Override this method to implement custom dispatch logic.
     * 
     * @param incident the incident
     * @return number of units to dispatch
     */
    protected int calculateUnitsNeeded(Incident incident) {
        // Default: dispatch units equal to severity level
        return incident.getGravite();
    }

    private void assignVehicleToIncident(Vehicle v, Incident incident, String interventionId) {
        v.setCurrentIncident(incident);
        v.setEtat(VehicleState.EN_ROUTE);
        v.setAssignmentId(api.assignUnit(interventionId, v.getUnitId(), "unit"));
        v.setLastUpdate(Instant.now());
        v.setEnRouteSince(v.getLastUpdate());
        
        // Calculate route following roads
        List<double[]> route = routingService.getRoute(
                v.getLat(), v.getLon(),
                incident.getLat(), incident.getLon()
        );
        
        if (route != null && !route.isEmpty()) {
            v.setCurrentRoute(route);
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, "[Decision] Assign {0} (from {1}) -> incident {2} via {3} waypoints",
                        new Object[]{v.getCallSign(), nvl(v.getHomeBase(), "N/A"), incident.getNumber(), route.size()});
            }
        } else {
            // Fallback to direct route if OSRM fails
            v.clearRoute();
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, "[Decision] Assign {0} (from {1}) -> incident {2} (direct route - routing unavailable)",
                        new Object[]{v.getCallSign(), nvl(v.getHomeBase(), "N/A"), incident.getNumber()});
            }
        }
    }

    // =========================================================================
    // Incident Lifecycle
    // =========================================================================

    private void advanceIncidents() {
        Instant now = Instant.now();
        for (Incident inc : activeIncidents) {
            advanceIncidentState(inc, now);
        }
    }
    
    private void advanceIncidentState(Incident inc, Instant now) {
        if (inc.getEtat() == IncidentState.NOUVEAU && hasArrivedUnit(inc)) {
            transitionToInProgress(inc, now);
        }
        if (inc.getEtat() == IncidentState.EN_COURS) {
            checkAndResolveIfComplete(inc, now);
        }
    }
    
    private void transitionToInProgress(Incident inc, Instant now) {
        inc.setEtat(IncidentState.EN_COURS);
        inc.setLastUpdate(now);
        if (inc.getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
            api.updateInterventionStatus(inc.getInterventionId(), INTERVENTION_STATUS_ON_SITE);
        }
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "[Decision] Incident {0} now IN PROGRESS - units working on site", inc.getNumber());
        }
    }
    
    private void checkAndResolveIfComplete(Incident inc, Instant now) {
        long secondsOnSite = now.getEpochSecond() - inc.getLastUpdate().getEpochSecond();
        boolean timeElapsed = secondsOnSite >= INCIDENT_RESOLVE_AFTER_ARRIVAL_SECONDS;
        if (timeElapsed && allUnitsOnSite(inc)) {
            resolveIncident(inc, "intervention complete after " + secondsOnSite + "s on site");
        }
    }

    private boolean allUnitsOnSite(Incident inc) {
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == inc && v.getEtat() != VehicleState.SUR_PLACE) {
                return false;
            }
        }
        return true;
    }

    private boolean hasArrivedUnit(Incident inc) {
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == inc && v.getEtat() == VehicleState.SUR_PLACE) {
                return true;
            }
        }
        return false;;
    }

    private void resolveIncident(Incident inc, String reason) {
        if (inc.getEtat() == IncidentState.RESOLU) {
            return;
        }
        inc.setEtat(IncidentState.RESOLU);
        inc.setLastUpdate(Instant.now());
        logIncidentResolved(inc, reason);
        updateInterventionStatusOnResolve(inc);
        sendVehiclesHome(inc);
        printIncidentStatusLine(inc);
    }
    
    private void logIncidentResolved(Incident inc, String reason) {
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "{0}{1} RESOLVED ({2})", new Object[]{SIM_INCIDENT_PREFIX, inc.getNumber(), reason});
        }
    }
    
    private void updateInterventionStatusOnResolve(Incident inc) {
        if (inc.getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
            api.updateInterventionStatus(inc.getInterventionId(), INTERVENTION_STATUS_COMPLETED);
        }
    }
    
    private void sendVehiclesHome(Incident inc) {
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == inc) {
                prepareVehicleForReturn(v);
            }
        }
    }
    
    private void prepareVehicleForReturn(Vehicle v) {
        v.setEtat(VehicleState.RETOUR);
        v.setLastUpdate(Instant.now());
        v.setReturnSince(v.getLastUpdate());
        
        api.updateUnitStatus(v.getUnitId(), UNIT_STATUS_EN_ROUTE);
        
        if (PATCH_ASSIGNMENT_STATUS) {
            api.updateAssignmentStatus(v.getAssignmentId(), ASSIGNMENT_STATUS_RELEASED);
        }
        
        calculateReturnRoute(v);
        
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "[Decision] {0} returning to home base {1}", 
                    new Object[]{v.getCallSign(), v.getHomeBase()});
        }
    }
    
    private void calculateReturnRoute(Vehicle v) {
        BaseLocation homeBase = getBaseByName(v.getHomeBase());
        if (homeBase == null) {
            return;
        }
        List<double[]> returnRoute = routingService.getRoute(
                v.getLat(), v.getLon(),
                homeBase.lat, homeBase.lon
        );
        if (!returnRoute.isEmpty()) {
            v.setCurrentRoute(returnRoute);
        }
    }

    // =========================================================================
    // Vehicle Movement
    // =========================================================================

    private void advanceVehicles() {
        for (Vehicle v : orderedVehiclesByIncident()) {
            switch (v.getEtat()) {
                case DISPONIBLE:
                    break;
                case EN_ROUTE:
                    handleEnRoute(v);
                    break;
                case SUR_PLACE:
                    handleSurPlace(v);
                    break;
                case RETOUR:
                    handleReturn(v);
                    break;
            }
        }
    }

    private void handleEnRoute(Vehicle v) {
        if (v.getCurrentIncident() == null) {
            return;
        }
        
        double targetLat = v.getCurrentIncident().getLat();
        double targetLon = v.getCurrentIncident().getLon();
        
        // Check distance to final destination (incident) in METERS
        double distToIncident = distanceInMeters(v.getLat(), v.getLon(), targetLat, targetLon);
        
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "[Decision] {0} EN_ROUTE to incident {1} - distance: {2}m (threshold: {3}m)",
                    new Object[]{v.getCallSign(), v.getCurrentIncident().getNumber(), 
                            String.format("%.1f", distToIncident), ARRIVAL_THRESHOLD_METERS});
        }
        
        // Arrived at incident location (within threshold)
        if (distToIncident < ARRIVAL_THRESHOLD_METERS) {
            onVehicleArrivedAtIncident(v, distToIncident);
        } else {
            // Follow route waypoints if available
            followRouteOrDirect(v, targetLat, targetLon);
        }
    }

    private void onVehicleArrivedAtIncident(Vehicle v, double distMeters) {
        double targetLat = v.getCurrentIncident().getLat();
        double targetLon = v.getCurrentIncident().getLon();
        
        // Snap to exact incident location
        v.setLat(targetLat);
        v.setLon(targetLon);
        v.setEtat(VehicleState.SUR_PLACE);
        v.setLastUpdate(Instant.now());
        v.setEnRouteSince(null);
        v.clearRoute();
        
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "[Decision] {0} arrived ON SITE at incident {1} (distance: {2}m)", 
                    new Object[]{v.getCallSign(), v.getCurrentIncident().getNumber(), String.format("%.1f", distMeters)});
        }
        
        if (PATCH_ASSIGNMENT_STATUS) {
            api.updateAssignmentStatus(v.getAssignmentId(), ASSIGNMENT_STATUS_ARRIVED);
        }
        api.updateUnitStatus(v.getUnitId(), UNIT_STATUS_ON_SITE);
        printIncidentStatusLine(v.getCurrentIncident());
        if (v.getCurrentIncident().getInterventionId() != null && PATCH_INTERVENTION_STATUS) {
            api.updateInterventionStatus(v.getCurrentIncident().getInterventionId(), INTERVENTION_STATUS_ON_SITE);
        }
    }

    private void followRouteOrDirect(Vehicle v, double finalLat, double finalLon) {
        if (v.hasNextWaypoint()) {
            // Follow route waypoints (stay on roads)
            double[] waypoint = v.getNextWaypoint();
            double wpLat = waypoint[0];
            double wpLon = waypoint[1];
            
            double distToWaypoint = distanceInMeters(v.getLat(), v.getLon(), wpLat, wpLon);
            
            if (distToWaypoint < WAYPOINT_THRESHOLD_METERS) {
                // Reached waypoint - SNAP to it (keeps vehicle on road) and move to next
                v.setLat(wpLat);
                v.setLon(wpLon);
                v.advanceToNextWaypoint();
                
                // If there's another waypoint, start moving towards it immediately
                if (v.hasNextWaypoint()) {
                    double[] nextWp = v.getNextWaypoint();
                    moveTowards(v, nextWp[0], nextWp[1], MOVEMENT_STEP);
                }
            } else {
                // Move towards current waypoint (following road)
                moveTowards(v, wpLat, wpLon, MOVEMENT_STEP);
            }
        } else {
            // No route or route exhausted - move directly towards destination
            moveTowards(v, finalLat, finalLon, MOVEMENT_STEP);
        }
        v.setLastUpdate(Instant.now());
    }

    private void handleSurPlace(Vehicle v) {
        Incident inc = v.getCurrentIncident();
        if (inc == null) {
            resetToAvailable(v);
            return;
        }
        
        logOnSiteTime(v, inc);
        
        // Check if incident is resolved - time to return home
        if (inc.getEtat() == IncidentState.RESOLU && v.getEtat() != VehicleState.RETOUR) {
            transitionToReturn(v);
        }
        // Otherwise, vehicle stays on site - no movement
    }
    
    private void resetToAvailable(Vehicle v) {
        v.setEtat(VehicleState.DISPONIBLE);
        v.clearRoute();
        v.setLastUpdate(Instant.now());
    }
    
    private void logOnSiteTime(Vehicle v, Incident inc) {
        if (inc.getLastUpdate() != null && LOG.isLoggable(Level.FINE)) {
            long onSiteSeconds = Instant.now().getEpochSecond() - inc.getLastUpdate().getEpochSecond();
            LOG.log(Level.FINE, "[Decision] {0} ON_SITE at incident {1} for {2}s (resolves at {3}s)",
                    new Object[]{v.getCallSign(), inc.getNumber(), onSiteSeconds, INCIDENT_RESOLVE_AFTER_ARRIVAL_SECONDS});
        }
    }
    
    private void transitionToReturn(Vehicle v) {
        v.setEtat(VehicleState.RETOUR);
        v.setReturnSince(Instant.now());
        v.setLastUpdate(v.getReturnSince());
        
        ensureReturnRouteCalculated(v);
        
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "[Decision] {0} incident resolved, returning to {1}",
                    new Object[]{v.getCallSign(), v.getHomeBase()});
        }
    }
    
    private void ensureReturnRouteCalculated(Vehicle v) {
        if (v.getCurrentRoute() != null && !v.getCurrentRoute().isEmpty()) {
            return;
        }
        BaseLocation homeBase = getBaseByName(v.getHomeBase());
        if (homeBase == null) {
            return;
        }
        List<double[]> returnRoute = routingService.getRoute(
                v.getLat(), v.getLon(),
                homeBase.lat, homeBase.lon
        );
        if (!returnRoute.isEmpty()) {
            v.setCurrentRoute(returnRoute);
        }
    }

    private void handleReturn(Vehicle v) {
        // Get home base coordinates
        BaseLocation homeBase = getBaseByName(v.getHomeBase());
        if (homeBase == null) {
            // Fallback to city center if home base not found
            homeBase = new BaseLocation("default", CITY_CENTER_LAT, CITY_CENTER_LON);
        }
        
        double targetLat = homeBase.lat;
        double targetLon = homeBase.lon;
        
        // Check distance to home base in METERS
        double distToHome = distanceInMeters(v.getLat(), v.getLon(), targetLat, targetLon);
        
        // Arrived at home base (within 25 meters)
        if (distToHome < ARRIVAL_THRESHOLD_METERS) {
            // Snap to home base location
            v.setLat(targetLat);
            v.setLon(targetLon);
            v.setEtat(VehicleState.DISPONIBLE);
            v.setCurrentIncident(null);
            v.setAssignmentId(null);
            v.setEnRouteSince(null);
            v.setReturnSince(null);
            v.clearRoute();
            v.setLastUpdate(Instant.now());
            
            api.updateUnitStatus(v.getUnitId(), UNIT_STATUS_AVAILABLE);
            
            if (LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, "[Decision] {0} returned to {1} - now AVAILABLE (distance: {2}m)", 
                        new Object[]{v.getCallSign(), v.getHomeBase(), String.format("%.1f", distToHome)});
            }
        } else {
            // Follow route waypoints back to home base
            followRouteOrDirect(v, targetLat, targetLon);
        }
    }
    
    private BaseLocation getBaseByName(String name) {
        if (name == null) {
            return null;
        }
        for (BaseLocation base : BASES) {
            if (base.name.equals(name)) {
                return base;
            }
        }
        return null;
    }

    private void cleanupResolvedIncidents() {
        activeIncidents.removeIf(inc -> inc.getEtat() == IncidentState.RESOLU && !hasAnyVehicleOnIncident(inc));
    }

    private boolean hasAnyVehicleOnIncident(Incident incident) {
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == incident) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private void logNewIncident(Incident incident) {
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, "\n{0}{1}: {2} | Type: {3} | G: {4} | Area: {5} | Loc: ({6})",
                    new Object[]{
                            SIM_INCIDENT_PREFIX,
                            incident.getNumber(),
                            incident.getId(),
                            incident.getType(),
                            incident.getGravite(),
                            nearestBaseName(incident.getLat(), incident.getLon()),
                            String.format(Locale.US, "%.5f, %.5f", incident.getLat(), incident.getLon())
                    });
        }
    }

    private void printIncidentStatusLine(Incident incident) {
        if (incident == null) {
            return;
        }
        List<Vehicle> involved = new ArrayList<>();
        for (Vehicle v : vehicles) {
            if (v.getCurrentIncident() == incident) {
                involved.add(v);
            }
        }
        if (involved.isEmpty()) {
            return;
        }
        involved.sort((a, b) -> a.getCallSign().compareToIgnoreCase(b.getCallSign()));
        String area = nearestBaseName(incident.getLat(), incident.getLon());
        StringBuilder sb = new StringBuilder();
        sb.append(SIM_INCIDENT_PREFIX).append(incident.getNumber()).append(" - ").append(area).append(" : ");
        for (int i = 0; i < involved.size(); i++) {
            Vehicle v = involved.get(i);
            sb.append(v.getCallSign()).append(" [").append(readableState(v.getEtat())).append("]");
            if (i < involved.size() - 1) {
                sb.append(", ");
            }
        }
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, sb.toString());
        }
    }

    private List<Vehicle> orderedVehiclesByIncident() {
        List<Vehicle> ordered = new ArrayList<>(vehicles);
        ordered.sort((a, b) -> {
            if (a.getCurrentIncident() == null && b.getCurrentIncident() == null) return 0;
            if (a.getCurrentIncident() == null) return 1;
            if (b.getCurrentIncident() == null) return -1;
            return Integer.compare(a.getCurrentIncident().getNumber(), b.getCurrentIncident().getNumber());
        });
        return ordered;
    }

    private String mapVehicleState(VehicleState state) {
        switch (state) {
            case DISPONIBLE:
                return UNIT_STATUS_AVAILABLE;
            case EN_ROUTE:
                return UNIT_STATUS_EN_ROUTE;
            case SUR_PLACE:
                return UNIT_STATUS_ON_SITE;
            case RETOUR:
                return UNIT_STATUS_EN_ROUTE;
            default:
                return UNIT_STATUS_AVAILABLE;
        }
    }

    private String readableState(VehicleState state) {
        switch (state) {
            case DISPONIBLE:
                return UNIT_STATUS_AVAILABLE;
            case EN_ROUTE:
                return "under way";
            case SUR_PLACE:
                return "on site";
            case RETOUR:
                return "return";
            default:
                return "unknown";
        }
    }

    private void moveTowards(Vehicle v, double targetLat, double targetLon, double step) {
        double dLat = targetLat - v.getLat();
        double dLon = targetLon - v.getLon();
        double len = Math.sqrt(dLat * dLat + dLon * dLon);
        if (len < 1e-6) {
            return;
        }
        // Don't overshoot the target - if step > distance, just snap to target
        double actualStep = Math.min(step, len);
        v.setLat(v.getLat() + (dLat / len) * actualStep);
        v.setLon(v.getLon() + (dLon / len) * actualStep);
    }

    /**
     * Get bases sorted by distance to a given location (nearest first).
     * This enables dynamic dispatch priority based on actual distances.
     * 
     * @param lat incident latitude
     * @param lon incident longitude
     * @return list of base names sorted by distance (nearest first)
     */
    private List<String> getBasesByDistance(double lat, double lon) {
        List<BaseLocation> sorted = new ArrayList<>(Arrays.asList(BASES));
        sorted.sort((a, b) -> Double.compare(
                distance(a.lat, a.lon, lat, lon),
                distance(b.lat, b.lon, lat, lon)
        ));
        List<String> result = new ArrayList<>();
        for (BaseLocation base : sorted) {
            result.add(base.name);
        }
        return result;
    }

    private String nearestBaseName(double lat, double lon) {
        String name = BASE_PART_DIEU;
        double best = Double.MAX_VALUE;
        for (BaseLocation b : BASES) {
            double d = Math.pow(lat - b.lat, 2) + Math.pow(lon - b.lon, 2);
            if (d < best) {
                best = d;
                name = b.name;
            }
        }
        return name;
    }

    /**
     * Calculate distance between two points using Haversine formula.
     * Returns distance in METERS.
     */
    private double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Simple Euclidean distance in degrees (for sorting only).
     */
    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }

    private String nvl(String value, String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }
}
