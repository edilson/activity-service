package com.example.activity.service;

import com.example.activity.domain.*;
import com.example.activity.repository.ActivityPhotoRepository;
import com.example.activity.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ActivityPhotoServiceTest {
    ActivityService activities = mock(ActivityService.class);
    ActivityPhotoRepository repository = mock(ActivityPhotoRepository.class);
    ObjectStorageService storage = mock(ObjectStorageService.class);
    ActivityPhotoService service = new ActivityPhotoService(activities, repository, storage, new PhotoValidator());
    @Test void savesPhotoReferenceAfterValidationAndOwnershipCheck() throws Exception {
        var id = UUID.randomUUID(); byte[] bytes = PhotoValidatorTest.png();
        var file = new StoredFile("bucket", "key", "s3://bucket/key", (long) bytes.length);
        when(storage.upload(id, "photos", bytes, "image/png")).thenReturn(file);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var photo = service.add("owner", id, "photo.png", bytes);
        assertThat(photo.getFile()).isEqualTo(file); assertThat(photo.getActivityId()).isEqualTo(id);
        var order = inOrder(activities, storage, repository); order.verify(activities).get("owner", id);
        order.verify(storage).upload(id, "photos", bytes, "image/png"); order.verify(repository).save(any());
    }
    @Test void rejectsOtherOwnersBeforeReadingOrUploading() {
        var id = UUID.randomUUID(); when(activities.get("other", id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.add("other", id, "x.png", new byte[]{1})).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.list("other", id, 0)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.download("other", id, UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage, repository);
    }
    @Test void cannotDownloadPhotoFromDifferentActivity() {
        var id = UUID.randomUUID(); var photoId = UUID.randomUUID(); when(repository.findByIdAndActivityId(photoId, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.download("owner", id, photoId)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage);
    }
}
