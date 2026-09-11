package com.example.activity.api;

import com.example.activity.config.SecurityConfig;
import com.example.activity.domain.*;
import com.example.activity.service.ActivityPhotoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageImpl;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(ActivityPhotoController.class)
@Import(SecurityConfig.class)
class ActivityPhotoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ActivityPhotoService service;
    @Test void uploadsListsAndDownloadsOwnedPhotos() throws Exception {
        var id = UUID.randomUUID(); var photo = new ActivityPhoto(id, "photo.png", "image/png", new StoredFile("bucket", "key", "s3://bucket/key", 1L));
        when(service.add(eq("rider"), eq(id), eq("photo.png"), any())).thenReturn(photo);
        mvc.perform(multipart("/api/activities/" + id + "/photos").file(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1}))
            .with(user("rider")).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.file.url").value("s3://bucket/key"));
        when(service.list("rider", id, 0)).thenReturn(new PageImpl<>(List.of(photo)));
        mvc.perform(get("/api/activities/" + id + "/photos").with(user("rider"))).andExpect(status().isOk());
        when(service.download("rider", id, photo.getId())).thenReturn(new byte[]{1});
        mvc.perform(get("/api/activities/" + id + "/photos/" + photo.getId() + "/file").with(user("rider")))
            .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1}));
    }
    @Test void requiresAuthenticationAndCsrf() throws Exception {
        String url = "/api/activities/" + UUID.randomUUID() + "/photos";
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(multipart(url).file("file", new byte[]{1}).with(user("rider"))).andExpect(status().isForbidden());
    }
}
