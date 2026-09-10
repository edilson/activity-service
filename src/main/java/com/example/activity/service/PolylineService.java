package com.example.activity.service;

import com.example.activity.domain.RoutePoint;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PolylineService {
    public String encode(List<RoutePoint> points) {
        if (points.size() < 2) return null;
        StringBuilder result = new StringBuilder(); long lat = 0, lon = 0;
        for (RoutePoint point : points) {
            long nextLat = Math.round(point.latitude() * 100000), nextLon = Math.round(point.longitude() * 100000);
            append(result, nextLat - lat); append(result, nextLon - lon); lat = nextLat; lon = nextLon;
        }
        return result.toString();
    }
    private void append(StringBuilder out, long delta) {
        long value = delta < 0 ? ~(delta << 1) : delta << 1;
        while (value >= 32) { out.append((char) ((32 | (value & 31)) + 63)); value >>= 5; }
        out.append((char) (value + 63));
    }
}
