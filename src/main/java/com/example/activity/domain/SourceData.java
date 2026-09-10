package com.example.activity.domain;
import com.fasterxml.jackson.databind.JsonNode;
/** Lossless source content retained alongside normalized metrics. */
public record SourceData(String filename, String contentType, byte[] originalFile, String sourceUrl, JsonNode providerResponse) {}
