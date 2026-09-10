package com.example.activity.service;

import com.example.activity.domain.RoutePoint;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class PolylineServiceTest {
    private final PolylineService service = new PolylineService();
    @Test void matchesGoogleReferenceVector() {
        assertThat(service.encode(List.of(new RoutePoint(38.5, -120.2), new RoutePoint(40.7, -120.95), new RoutePoint(43.252, -126.453))))
            .isEqualTo("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
    }
    @Test void noPolylineForInsufficientCoordinates() {
        assertThat(service.encode(List.of())).isNull();
        assertThat(service.encode(List.of(new RoutePoint(0, 0)))).isNull();
    }
}
