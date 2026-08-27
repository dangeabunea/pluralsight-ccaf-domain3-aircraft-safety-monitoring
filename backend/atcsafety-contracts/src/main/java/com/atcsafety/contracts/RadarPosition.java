package com.atcsafety.contracts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RadarPosition(
        @JsonProperty(required = true) int targetId,
        @JsonProperty(required = true) int radarCycle,
        @JsonProperty(required = true) float x,
        @JsonProperty(required = true) float y,
        @JsonProperty(required = true) float altFeet,
        @JsonProperty(required = true) float speedKn,
        @JsonProperty(required = true) String timestampUTC,
        @JsonProperty(required = true) int headingDeg,
        @JsonProperty(required = false) String flightNb,
        @JsonProperty(required = false) Float lat,
        @JsonProperty(required = false) Float lon
) {}
