package com.example.activity.ingestion;

import com.example.activity.domain.*;
import com.garmin.fit.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.util.*;

@Component
public class FitParser {
    public ActivityData parse(byte[] bytes) {
        try {
            Decode decode = new Decode();
            if (!decode.checkFileIntegrity(new ByteArrayInputStream(bytes))) throw new InvalidActivityException("FIT integrity check failed");
            List<SessionMesg> sessions = new ArrayList<>(); List<RoutePoint> route = new ArrayList<>();
            MesgBroadcaster broadcaster = new MesgBroadcaster(decode);
            broadcaster.addListener((SessionMesgListener) sessions::add);
            broadcaster.addListener((RecordMesgListener) record -> {
                if (record.getPositionLat() != null && record.getPositionLong() != null) {
                    route.add(new RoutePoint(record.getPositionLat() * (180.0 / 2147483648.0), record.getPositionLong() * (180.0 / 2147483648.0)));
                    if (route.size() > 100000) throw new InvalidActivityException("Route exceeds 100000 points");
                }
            });
            decode.read(new ByteArrayInputStream(bytes), broadcaster, broadcaster);
            if (sessions.size() != 1 || sessions.getFirst().getSport() != Sport.CYCLING)
                throw new InvalidActivityException("FIT must contain exactly one cycling session");
            SessionMesg session = sessions.getFirst();
            return new ActivityData(number(session.getTotalDistance()), number(session.getTotalTimerTime()), number(session.getTotalAscent()), route);
        } catch (InvalidActivityException e) { throw e; }
        catch (RuntimeException e) { throw new InvalidActivityException("Invalid FIT file"); }
    }
    private Double number(Number n) { return n == null ? null : n.doubleValue(); }
}
