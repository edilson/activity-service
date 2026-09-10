package com.example.activity.ingestion;

import com.example.activity.domain.InvalidActivityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.activity.TestSupport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExtractionMapperTest {
    private final ObjectMapper json = new ObjectMapper(); private final ExtractionMapper mapper = new ExtractionMapper();
    @Test void mapsExplicitCoordinates() throws Exception {
        var data = mapper.map(json.readTree(TestSupport.EXTRACTED.replace("[]", "[{\"latitude\":-3.7,\"longitude\":-38.5}]")));
        assertThat(data.distanceMeters()).isEqualTo(125000.0); assertThat(data.route()).hasSize(1);
    }
    @Test void doesNotCoerceStringsOrFabricateMissingValues() throws Exception {
        for (String invalid : new String[]{TestSupport.EXTRACTED.replace(":500", ":null"), TestSupport.EXTRACTED.replace("125000", "\"125 km\""),
            TestSupport.EXTRACTED.replace("cycling", "running"), TestSupport.EXTRACTED.replace("[]", "[{}]"), TestSupport.EXTRACTED.replace("[]", "{}")}) {
            var input = json.readTree(invalid);
            assertThatThrownBy(() -> mapper.map(input)).isInstanceOf(InvalidActivityException.class);
        }
        assertThat(mapper.schema()).containsKeys("required", "properties");
    }
}
