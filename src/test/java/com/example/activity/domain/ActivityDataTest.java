package com.example.activity.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ActivityDataTest {
    @Test void mandatoryMetricsRejectMissingNegativeAndNonfinite() {
        for (Double invalid : Arrays.asList(null, -1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThatThrownBy(() -> new ActivityData(invalid, 1.0, 0.0, null)).isInstanceOf(InvalidActivityException.class);
            assertThatThrownBy(() -> new ActivityData(1.0, invalid, 0.0, null)).isInstanceOf(InvalidActivityException.class);
        }
        for (Double invalid : Arrays.asList(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertThatThrownBy(() -> new ActivityData(1.0, 1.0, invalid, null)).isInstanceOf(InvalidActivityException.class);
    }
    @Test void zeroAscentIsValidAndRouteIsImmutable() {
        var points = new ArrayList<>(List.of(new RoutePoint(0, 0)));
        var data = new ActivityData(1.0, 1.0, 0.0, points); points.clear();
        assertThat(data.route()).hasSize(1);
        assertThat(new ActivityData(1.0, 1.0, 0.0, null).route()).isEmpty();
    }
    @Test void rejectsInvalidCoordinates() {
        assertThatThrownBy(() -> new RoutePoint(91, 0)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> new RoutePoint(0, -181)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> new RoutePoint(Double.NaN, 0)).isInstanceOf(InvalidActivityException.class);
    }
}
