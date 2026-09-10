package com.example.activity.ingestion;

import com.example.activity.domain.*;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Component
public class GpxParser {
    public ActivityData parse(byte[] bytes) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            if (!"gpx".equals(doc.getDocumentElement().getLocalName())) throw new InvalidActivityException("Expected GPX document");
            NodeList segments = doc.getElementsByTagNameNS("*", "trkseg");
            List<RoutePoint> route = new ArrayList<>();
            double distance = 0, ascent = 0, seconds = 0;
            int nonemptySegments = 0;
            for (int s = 0; s < segments.getLength(); s++) {
                var points = ((Element) segments.item(s)).getElementsByTagNameNS("*", "trkpt");
                if (points.getLength() == 0) continue;
                nonemptySegments++;
                RoutePoint previous = null; Double previousElevation = null; Instant previousTime = null;
                for (int i = 0; i < points.getLength(); i++) {
                    var node = (Element) points.item(i);
                    var point = new RoutePoint(Double.parseDouble(node.getAttribute("lat")), Double.parseDouble(node.getAttribute("lon")));
                    double elevation = Double.parseDouble(required(node, "ele"));
                    if (!Double.isFinite(elevation)) throw new InvalidActivityException("Invalid GPX elevation");
                    Instant time = Instant.parse(required(node, "time"));
                    if (previous != null) {
                        double dt = Duration.between(previousTime, time).toMillis() / 1000.0;
                        if (dt < 0) throw new InvalidActivityException("GPX timestamps must be ordered within each segment");
                        distance += distance(previous, point); ascent += Math.max(0, elevation - previousElevation); seconds += dt;
                    }
                    route.add(point);
                    if (route.size() > 100000) throw new InvalidActivityException("Route exceeds 100000 points");
                    previous = point; previousElevation = elevation; previousTime = time;
                }
            }
            // A single polyline cannot represent disconnected segments without inventing connecting legs.
            return new ActivityData(distance, seconds, ascent, nonemptySegments == 1 ? route : List.of());
        } catch (InvalidActivityException e) { throw e; }
        catch (Exception e) { throw new InvalidActivityException("Invalid GPX: track points require coordinates, elevation and ISO timestamps"); }
    }
    private String required(Element element, String tag) {
        var nodes = element.getElementsByTagNameNS("*", tag);
        if (nodes.getLength() != 1) throw new InvalidActivityException("GPX requires one " + tag + " per track point");
        return nodes.item(0).getTextContent().trim();
    }
    private double distance(RoutePoint a, RoutePoint b) {
        double lat = Math.toRadians(b.latitude() - a.latitude()), lon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(lat / 2), 2) + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude())) * Math.pow(Math.sin(lon / 2), 2);
        return 6371008.8 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
    }
}
