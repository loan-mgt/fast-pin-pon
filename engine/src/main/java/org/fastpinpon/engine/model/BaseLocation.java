package org.fastpinpon.simulation.model;

public final class BaseLocation {
    public final String name;
    public final double lat;
    public final double lon;

    public BaseLocation(String name, double lat, double lon) {
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }
}
