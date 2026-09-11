package com.example.activity.service;

import com.example.activity.domain.ActivityPhoto;
import com.example.activity.repository.ActivityPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Service
public class ActivityPhotoService {
    private final ActivityService activities; private final ActivityPhotoRepository photos;
    private final ObjectStorageService storage; private final PhotoValidator validator;
    public ActivityPhotoService(ActivityService activities, ActivityPhotoRepository photos, ObjectStorageService storage, PhotoValidator validator) {
        this.activities = activities; this.photos = photos; this.storage = storage; this.validator = validator;
    }
    @Transactional
    public ActivityPhoto add(String owner, UUID activityId, String filename, byte[] bytes) {
        activities.get(owner, activityId);
        String mime = validator.validate(filename, bytes);
        var file = storage.upload(activityId, "photos", bytes, mime);
        return photos.save(new ActivityPhoto(activityId, filename, mime, file));
    }
    @Transactional(readOnly = true)
    public Page<ActivityPhoto> list(String owner, UUID activityId, int page) {
        activities.get(owner, activityId);
        return photos.findByActivityId(activityId, PageRequest.of(Math.max(0, page), 20, Sort.by("createdAt").descending()));
    }
    @Transactional(readOnly = true)
    public byte[] download(String owner, UUID activityId, UUID photoId) {
        activities.get(owner, activityId);
        var photo = photos.findByIdAndActivityId(photoId, activityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
        return storage.download(photo.getFile());
    }
}
