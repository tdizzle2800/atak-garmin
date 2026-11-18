package com.atakgarmin.core;

import java.util.ArrayList;
import java.util.List;

/** Plain data classes, independent of ATAK, so the GPX logic is testable on a desktop JVM. */
public final class GpxModel {
    private GpxModel() {}

    public static final class Point {
        public final String name;
        public final double lat;
        public final double lon;
        /** Metres above sea level (MSL). NaN when unknown. */
        public final double elevation;
        public final String description;

        public Point(String name, double lat, double lon, double elevation, String description) {
            this.name = name == null ? "" : name;
            this.lat = lat;
            this.lon = lon;
            this.elevation = elevation;
            this.description = description;
        }

        public Point(String name, double lat, double lon) {
            this(name, lat, lon, Double.NaN, null);
        }
    }

    public static final class Route {
        public final String name;
        public final List<Point> points = new ArrayList<Point>();

        public Route(String name) {
            this.name = name == null ? "" : name;
        }

        public Route add(Point p) {
            points.add(p);
            return this;
        }
    }
}
