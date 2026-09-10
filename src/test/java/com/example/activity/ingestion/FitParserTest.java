package com.example.activity.ingestion;

import com.example.activity.domain.InvalidActivityException;
import com.garmin.fit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class FitParserTest {
    @TempDir Path temp;
    private byte[] fixture(Sport sport, boolean ascent) throws Exception {
        var file = temp.resolve("ride.fit").toFile();
        var encoder = new FileEncoder(file, Fit.ProtocolVersion.V2_0);
        var id = new FileIdMesg(); id.setType(com.garmin.fit.File.ACTIVITY); id.setManufacturer(Manufacturer.GARMIN);
        encoder.write(id);
        var session = new SessionMesg(); session.setSport(sport); session.setTotalDistance(125000f); session.setTotalTimerTime(14400f);
        if (ascent) session.setTotalAscent(600);
        encoder.write(session);
        for (int i = 0; i < 2; i++) { var record = new RecordMesg(); record.setPositionLat(100000 + i * 1000); record.setPositionLong(-200000); encoder.write(record); }
        encoder.close(); return Files.readAllBytes(file.toPath());
    }
    @Test void parsesOfficialSdkBinaryFixture() throws Exception {
        var data = new FitParser().parse(fixture(Sport.CYCLING, true));
        assertThat(data.distanceMeters()).isEqualTo(125000.0); assertThat(data.durationSeconds()).isEqualTo(14400.0);
        assertThat(data.elevationGainMeters()).isEqualTo(600.0); assertThat(data.route()).hasSize(2);
        assertThat(data.route().getFirst().latitude()).isCloseTo(0.008381903, within(0.000000001));
    }
    @Test void rejectsNonCyclingAndMissingAscent() throws Exception {
        byte[] running = fixture(Sport.RUNNING, true), missing = fixture(Sport.CYCLING, false);
        assertThatThrownBy(() -> new FitParser().parse(running)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> new FitParser().parse(missing)).isInstanceOf(InvalidActivityException.class);
    }
    @Test void rejectsCorruptFiles() throws Exception {
        byte[] bytes = fixture(Sport.CYCLING, true); bytes[20] ^= 1;
        assertThatThrownBy(() -> new FitParser().parse(bytes)).isInstanceOf(InvalidActivityException.class);
        assertThatThrownBy(() -> new FitParser().parse(new byte[]{1, 2, 3})).isInstanceOf(InvalidActivityException.class);
    }
}
