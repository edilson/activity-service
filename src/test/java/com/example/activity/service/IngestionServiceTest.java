package com.example.activity.service;

import com.example.activity.TestSupport;
import com.example.activity.domain.*;
import com.example.activity.ingestion.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class IngestionServiceTest {
    GpxParser gpx = mock(GpxParser.class); FitParser fit = mock(FitParser.class); GroqClient groq = mock(GroqClient.class);
    FirecrawlClient firecrawl = mock(FirecrawlClient.class); ActivityService activities = mock(ActivityService.class);
    IngestionService service = new IngestionService(gpx, fit, groq, firecrawl, activities);
    @Test void dispatchesAllFormats() {
        byte[] bytes = {1}; var data = TestSupport.data(1);
        var response = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("extra", "retained");
        when(gpx.parse(bytes)).thenReturn(data); when(fit.parse(bytes)).thenReturn(data); when(groq.extractDetailed(bytes)).thenReturn(new ExtractionResult(data, response));
        when(firecrawl.extractDetailed("url")).thenReturn(new ExtractionResult(data, response));
        service.file("rider", "RIDE.GPX", bytes); verify(activities).save(eq("rider"), eq("GPX"), eq(data), argThat(s -> java.util.Arrays.equals(s.originalFile(), bytes)), isNull());
        service.file("rider", "ride.fit", bytes); verify(activities).save(eq("rider"), eq("FIT"), eq(data), argThat(s -> java.util.Arrays.equals(s.originalFile(), bytes)), isNull());
        service.file("rider", "ride.png", bytes); verify(activities).save(eq("rider"), eq("SCREENSHOT"), eq(data), argThat(s -> s.providerResponse().equals(response)), isNull());
        service.strava("rider", "url"); verify(activities).save(eq("rider"), eq("STRAVA"), eq(data), argThat(s -> s.sourceUrl().equals("url") && s.providerResponse().equals(response)), isNull());
    }
    @Test void rejectsUnsupportedOrEmptyUploadsWithoutSaving() {
        assertThatThrownBy(() -> service.file("rider", "file.txt", new byte[]{1})).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> service.file("rider", "file.gpx", new byte[0])).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> service.file("rider", null, new byte[]{1})).isInstanceOf(InvalidActivityException.class);
        verifyNoInteractions(activities);
    }
    @Test void extractionFailureCannotSaveIncompleteActivity() {
        when(gpx.parse(any())).thenThrow(new InvalidActivityException("missing altitude"));
        assertThatThrownBy(() -> service.file("rider", "ride.gpx", new byte[]{1})).isInstanceOf(InvalidActivityException.class);
        verifyNoInteractions(activities);
    }
}

