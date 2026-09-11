package com.example.activity.service;

import com.example.activity.domain.*;
import com.example.activity.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ActivityService {
    private final ActivityRepository activities; private final OutboxRepository outbox; private final PolylineService polylines;
    private final ActivitySourceRepository sources;
    private final ObjectMapper json;
    private final ObjectStorageService storage;
    public ActivityService(ActivityRepository activities, OutboxRepository outbox, PolylineService polylines, ActivitySourceRepository sources, ObjectMapper json, ObjectStorageService storage) {
        this.activities = activities; this.outbox = outbox; this.polylines = polylines;
        this.sources = sources; this.json = json;
        this.storage = storage;
    }
    @Transactional
    public Activity save(String owner, String source, ActivityData data) {
        return save(owner, source, data, new SourceData(null, null, null, null, null));
    }
    @Transactional
    public Activity save(String owner, String source, ActivityData data, SourceData sourceData) {
        return save(owner, source, data, sourceData, null);
    }
    @Transactional
    public Activity save(String owner, String source, ActivityData data, SourceData sourceData, String name) {
        Activity activity = new Activity(owner, source, data, polylines.encode(data.route()));
        activity.setName(name);
        var payload = new ActivitySource(activity.getId(), sourceData, json.valueToTree(data.route()));
        if (sourceData.originalFile() != null)
            payload.storeFile(storage.upload(activity.getId(), "source", sourceData.originalFile(), sourceData.contentType()));
        activity = activities.save(activity);
        sources.save(payload);
        outbox.save(new OutboxEvent(activity)); return activity;
    }
    @Transactional
    public Activity rename(String owner, UUID id, String name) {
        var activity = get(owner, id); activity.setName(name); return activities.save(activity);
    }
    @Transactional(readOnly = true)
    public byte[] download(String owner, UUID id) {
        var source = source(owner, id);
        if (source.getFile() != null) return storage.download(source.getFile());
        // Keep old imports readable until their background S3 migration succeeds.
        if (source.getOriginalFile() != null) return source.getOriginalFile();
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This activity has no uploaded file");
    }
    @Transactional(readOnly = true)
    public ActivitySource source(String owner, UUID id) {
        get(owner, id);
        return sources.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source data is unavailable for this legacy import"));
    }
    @Transactional(readOnly = true)
    public Activity get(String owner, UUID id) {
        return activities.findByIdAndOwner(id, owner).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Activity not found"));
    }
    @Transactional(readOnly = true)
    public Page<Activity> list(String owner, int page) { return activities.findByOwner(owner, PageRequest.of(Math.max(0, page), 20, Sort.by("createdAt").descending())); }
}
