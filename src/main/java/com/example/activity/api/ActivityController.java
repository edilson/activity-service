package com.example.activity.api;

import com.example.activity.domain.Activity;
import com.example.activity.domain.ActivitySource;
import org.springframework.web.server.ResponseStatusException;
import com.example.activity.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.data.domain.Page;
import java.io.IOException;
import java.security.Principal;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final IngestionService ingestion; private final ActivityService activities;
    public ActivityController(IngestionService ingestion, ActivityService activities) { this.ingestion = ingestion; this.activities = activities; }
    public record StravaRequest(@NotBlank @Size(max = 2048) String url) {}
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Activity> upload(Principal principal, @RequestPart("file") MultipartFile file) throws IOException {
        return created(ingestion.file(principal.getName(), file.getOriginalFilename(), file.getBytes()));
    }
    @PostMapping("/strava")
    public ResponseEntity<Activity> strava(Principal principal, @Valid @RequestBody StravaRequest body) { return created(ingestion.strava(principal.getName(), body.url())); }
    @GetMapping("/{id}")
    public Activity get(Principal principal, @PathVariable UUID id) { return activities.get(principal.getName(), id); }
    @GetMapping("/{id}/source")
    public ActivitySource source(Principal principal, @PathVariable UUID id) { return activities.source(principal.getName(), id); }
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> originalFile(Principal principal, @PathVariable UUID id) {
        var source = activities.source(principal.getName(), id);
        byte[] bytes = source.getOriginalFile();
        if (bytes == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This activity has no uploaded file");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("activity-" + id).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store").body(bytes);
    }
    @GetMapping
    public Page<Activity> list(Principal principal, @RequestParam(defaultValue = "0") int page) { return activities.list(principal.getName(), page); }
    private ResponseEntity<Activity> created(Activity activity) { return ResponseEntity.created(URI.create("/api/activities/" + activity.getId())).body(activity); }
}
