package com.example.activity.domain;

public record RoutePoint(double latitude, double longitude) {
    public RoutePoint {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
            throw new InvalidActivityException("Route coordinates are outside valid geographic bounds");
    }
}
