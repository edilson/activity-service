package com.example.activity.ingestion;
import com.example.activity.domain.ActivityData;
import com.fasterxml.jackson.databind.JsonNode;
public record ExtractionResult(ActivityData data, JsonNode response) {}
