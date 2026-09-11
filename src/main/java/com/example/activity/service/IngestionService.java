package com.example.activity.service;

import com.example.activity.domain.*;
import com.example.activity.ingestion.*;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
public class IngestionService {
    private final GpxParser gpx; private final FitParser fit; private final GroqClient groq; private final FirecrawlClient firecrawl; private final ActivityService activities;
    public IngestionService(GpxParser gpx, FitParser fit, GroqClient groq, FirecrawlClient firecrawl, ActivityService activities) {
        this.gpx = gpx; this.fit = fit; this.groq = groq; this.firecrawl = firecrawl; this.activities = activities;
    }
    public Activity file(String owner, String filename, byte[] bytes) {
        return file(owner, filename, bytes, null);
    }
    public Activity file(String owner, String filename, byte[] bytes, String activityName) {
        validateName(activityName);
        if (bytes.length == 0 || bytes.length > 20 * 1024 * 1024) throw new InvalidActivityException("File must contain between 1 byte and 20 MiB");
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.length() > 255) throw new InvalidActivityException("Filename exceeds 255 characters");
        if (name.endsWith(".gpx")) return activities.save(owner, "GPX", gpx.parse(bytes), new SourceData(filename, "application/gpx+xml", bytes, null, null), activityName);
        if (name.endsWith(".fit")) return activities.save(owner, "FIT", fit.parse(bytes), new SourceData(filename, "application/octet-stream", bytes, null, null), activityName);
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            var result = groq.extractDetailed(bytes);
            String mime = bytes[0] == (byte)137 ? "image/png" : "image/jpeg";
            return activities.save(owner, "SCREENSHOT", result.data(), new SourceData(filename, mime, bytes, null, result.response()), activityName);
        }
        throw new InvalidActivityException("Supported files: .gpx, .fit, .png, .jpg, .jpeg");
    }
    public Activity strava(String owner, String url) {
        return strava(owner, url, null);
    }
    public Activity strava(String owner, String url, String name) {
        validateName(name);
        var result = firecrawl.extractDetailed(url);
        return activities.save(owner, "STRAVA", result.data(), new SourceData(null, null, null, url, result.response()), name);
    }
    private void validateName(String name) {
        if (name != null && name.length() > 255) throw new InvalidActivityException("Activity name must be at most 255 characters");
    }
}
