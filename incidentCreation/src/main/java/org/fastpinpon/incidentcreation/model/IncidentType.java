package org.fastpinpon.incidentcreation.model;

/**
 * Types of incidents that can be generated.
 * Aligned with event_types in the database.
 */
public enum IncidentType {
    // 70% probability tier - common incidents
    CRASH,
    RESCUE_MEDICAL,
    OTHER,
    
    // 20% probability tier - fire incidents  
    FIRE_URBAN,
    FIRE_INDUSTRIAL,
    
    // 10% probability tier - rare/specialized incidents
    HAZMAT,
    AQUATIC_RESCUE
}
