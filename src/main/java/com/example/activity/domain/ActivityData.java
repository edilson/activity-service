package com.example.activity.domain;

import java.util.List;

public record ActivityData(Double distanceMeters, Double durationSeconds, Double elevationGainMeters,
                           List<RoutePoint> route) {
    public ActivityData {
        positive(distanceMeters, "distanceMeters", false);
        positive(durationSeconds, "durationSeconds", false);
        positive(elevationGainMeters, "elevationGainMeters", true);
        route = route == null ? List.of() : List.copyOf(route);
        if (route.size() > 100_000) throw new InvalidActivityException("Route exceeds 100000 points");
    }
    private static void positive(Double value, String field, boolean allowZero) {
        if (value == null || !Double.isFinite(value) || (allowZero ? value < 0 : value <= 0))
            throw new InvalidActivityException(field + " is mandatory and must be " + (allowZero ? "nonnegative" : "positive"));
    }
}
