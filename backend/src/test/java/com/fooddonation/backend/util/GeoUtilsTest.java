package com.fooddonation.backend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    @DisplayName("Haversine formula calculates correct distance between known coordinates")
    void testHaversineKnownDistance() {
        // Mumbai CST: 18.9401, 72.8347
        // Bandra Station: 19.0544, 72.8406
        // Approximate distance: ~12.7 km
        double distance = GeoUtils.haversineKm(18.9401, 72.8347, 19.0544, 72.8406);
        assertTrue(distance > 12.0 && distance < 13.5, "Distance should be approx 12.7 km, got: " + distance);
    }

    @Test
    @DisplayName("Identical coordinates return 0 distance")
    void testHaversineIdenticalCoordinates() {
        double distance = GeoUtils.haversineKm(19.0760, 72.8777, 19.0760, 72.8777);
        assertEquals(0.0, distance, 0.001);
    }

    @Test
    @DisplayName("Null coordinate inputs safely return Double.MAX_VALUE without throwing NullPointerException")
    void testHaversineNullCoordinates() {
        assertEquals(Double.MAX_VALUE, GeoUtils.haversineKm(null, 72.8777, 19.0760, 72.8777));
        assertEquals(Double.MAX_VALUE, GeoUtils.haversineKm(19.0760, null, 19.0760, 72.8777));
        assertEquals(Double.MAX_VALUE, GeoUtils.haversineKm(19.0760, 72.8777, null, 72.8777));
        assertEquals(Double.MAX_VALUE, GeoUtils.haversineKm(19.0760, 72.8777, 19.0760, null));
    }
}
