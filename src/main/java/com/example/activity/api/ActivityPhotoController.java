package com.example.activity.api;

import com.example.activity.domain.ActivityPhoto;
import com.example.activity.service.ActivityPhotoService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.data.domain.Page;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities/{activityId}/photos")
public class ActivityPhotoController {
    private final ActivityPhotoService photos;
    public ActivityPhotoController(ActivityPhotoService photos) { this.photos = photos; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ActivityPhoto> add(Principal user, @PathVariable UUID activityId, @RequestPart("file") MultipartFile file) throws IOException {
        var photo = photos.add(user.getName(), activityId, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.created(URI.create("/api/activities/" + activityId + "/photos/" + photo.getId() + "/file")).body(photo);
    }
    @GetMapping
    public Page<ActivityPhoto> list(Principal user, @PathVariable UUID activityId, @RequestParam(defaultValue = "0") int page) {
        return photos.list(user.getName(), activityId, page);
    }
    @GetMapping("/{photoId}/file")
    public ResponseEntity<byte[]> download(Principal user, @PathVariable UUID activityId, @PathVariable UUID photoId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("photo-" + photoId).build().toString())
            .body(photos.download(user.getName(), activityId, photoId));
    }
}
