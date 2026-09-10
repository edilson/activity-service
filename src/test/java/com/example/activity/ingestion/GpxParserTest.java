package com.example.activity.ingestion;

import com.example.activity.domain.InvalidActivityException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class GpxParserTest {
    private final GpxParser parser = new GpxParser();
    private static final String SEGMENT = """
        <trkseg><trkpt lat="0" lon="0"><ele>10</ele><time>2026-01-01T00:00:00Z</time></trkpt>
        <trkpt lat="0" lon="0.01"><ele>25</ele><time>2026-01-01T00:01:00Z</time></trkpt>
        <trkpt lat="0" lon="0.02"><ele>20</ele><time>2026-01-01T00:02:00Z</time></trkpt></trkseg>
        """;
    private byte[] gpx(String segments) { return ("<gpx xmlns='http://www.topografix.com/GPX/1/1'><trk>" + segments + "</trk></gpx>").getBytes(StandardCharsets.UTF_8); }
    @Test void computesDistanceDurationAndPositiveAscent() {
        var data = parser.parse(gpx(SEGMENT));
        assertThat(data.distanceMeters()).isCloseTo(2223.90, within(0.1));
        assertThat(data.durationSeconds()).isEqualTo(120.0);
        assertThat(data.elevationGainMeters()).isEqualTo(15.0);
        assertThat(data.route()).hasSize(3);
    }
    @Test void segmentGapsDoNotAddDistanceTimeOrFakeRoute() {
        var data = parser.parse(gpx(SEGMENT + SEGMENT));
        assertThat(data.distanceMeters()).isCloseTo(4447.8, within(0.1));
        assertThat(data.durationSeconds()).isEqualTo(240.0);
        assertThat(data.elevationGainMeters()).isEqualTo(30.0);
        assertThat(data.route()).isEmpty();
    }
    @Test void rejectsMissingMetricsBackwardsTimestampsAndEmptyTracks() {
        for (String invalid : new String[]{"", SEGMENT.replace("<ele>10</ele>", ""), SEGMENT.replace("00:01:00", "00:03:00"), SEGMENT.replace("<ele>10</ele>", "<ele>NaN</ele>")})
            assertThatThrownBy(() -> parser.parse(gpx(invalid))).isInstanceOf(InvalidActivityException.class);
    }
    @Test void rejectsExternalEntities() {
        String xml = "<!DOCTYPE gpx [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><gpx>&xxe;</gpx>";
        assertThatThrownBy(() -> parser.parse(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(InvalidActivityException.class);
    }
}
