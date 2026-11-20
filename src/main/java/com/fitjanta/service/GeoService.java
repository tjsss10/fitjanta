package com.fitjanta.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Distance calculations and approximate city locations for the map.
 */
@Service
public class GeoService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** City centres used when a user hasn't shared an exact location yet. */
    private static final Map<String, double[]> CITIES = Map.ofEntries(
            Map.entry("pune", new double[]{18.5204, 73.8567}),
            Map.entry("mumbai", new double[]{19.0760, 72.8777}),
            Map.entry("bombay", new double[]{19.0760, 72.8777}),
            Map.entry("navi mumbai", new double[]{19.0330, 73.0297}),
            Map.entry("thane", new double[]{19.2183, 72.9781}),
            Map.entry("nashik", new double[]{19.9975, 73.7898}),
            Map.entry("nagpur", new double[]{21.1458, 79.0882}),
            Map.entry("aurangabad", new double[]{19.8762, 75.3433}),
            Map.entry("kolhapur", new double[]{16.7050, 74.2433}),
            Map.entry("solapur", new double[]{17.6599, 75.9064}),
            Map.entry("delhi", new double[]{28.6139, 77.2090}),
            Map.entry("new delhi", new double[]{28.6139, 77.2090}),
            Map.entry("noida", new double[]{28.5355, 77.3910}),
            Map.entry("gurgaon", new double[]{28.4595, 77.0266}),
            Map.entry("gurugram", new double[]{28.4595, 77.0266}),
            Map.entry("bangalore", new double[]{12.9716, 77.5946}),
            Map.entry("bengaluru", new double[]{12.9716, 77.5946}),
            Map.entry("hyderabad", new double[]{17.3850, 78.4867}),
            Map.entry("chennai", new double[]{13.0827, 80.2707}),
            Map.entry("kolkata", new double[]{22.5726, 88.3639}),
            Map.entry("ahmedabad", new double[]{23.0225, 72.5714}),
            Map.entry("surat", new double[]{21.1702, 72.8311}),
            Map.entry("jaipur", new double[]{26.9124, 75.7873}),
            Map.entry("lucknow", new double[]{26.8467, 80.9462}),
            Map.entry("kanpur", new double[]{26.4499, 80.3319}),
            Map.entry("indore", new double[]{22.7196, 75.8577}),
            Map.entry("bhopal", new double[]{23.2599, 77.4126}),
            Map.entry("patna", new double[]{25.5941, 85.1376}),
            Map.entry("chandigarh", new double[]{30.7333, 76.7794}),
            Map.entry("goa", new double[]{15.2993, 74.1240}),
            Map.entry("kochi", new double[]{9.9312, 76.2673}),
            Map.entry("coimbatore", new double[]{11.0168, 76.9558}),
            Map.entry("visakhapatnam", new double[]{17.6868, 83.2185}),
            Map.entry("vadodara", new double[]{22.3072, 73.1812}),
            Map.entry("satara", new double[]{17.6805, 74.0183}),
            Map.entry("ahmednagar", new double[]{19.0948, 74.7480}),
            Map.entry("pimpri", new double[]{18.6298, 73.7997}),
            Map.entry("pimpri chinchwad", new double[]{18.6298, 73.7997}),
            Map.entry("loni kalbhor", new double[]{18.4875, 74.0228}));

    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /**
     * Approximate position for a user's city, spread slightly per user so
     * markers in the same city don't sit on top of each other.
     */
    public double[] approximateLocation(String city, long userId) {
        if (city == null) return null;
        double[] c = CITIES.get(city.trim().toLowerCase(Locale.ROOT));
        if (c == null) return null;
        double angle = (userId * 137.508) % 360;          // golden-angle spread
        double radiusKm = 0.6 + (userId % 5) * 0.35;
        double dLat = (radiusKm / 111.0) * Math.cos(Math.toRadians(angle));
        double dLon = (radiusKm / (111.0 * Math.cos(Math.toRadians(c[0])))) * Math.sin(Math.toRadians(angle));
        return new double[]{c[0] + dLat, c[1] + dLon};
    }

    public static boolean validCoordinates(Double lat, Double lon) {
        return lat != null && lon != null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180
                && !(lat == 0 && lon == 0);
    }
}
