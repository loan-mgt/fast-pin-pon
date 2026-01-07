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
 */
public final class DecisionEngine {
    private static final Logger LOG = Logger.getLogger(DecisionEngine.class.getName());

    private final ApiClient api;
}
