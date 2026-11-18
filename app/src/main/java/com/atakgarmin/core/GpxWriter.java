package com.atakgarmin.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds GPX 1.1 documents for a Garmin Foretrex 401.
 *
 * The Foretrex 401 mounts as USB mass storage and reads/writes GPX
 * (Garmin/GPX/ on the device volume), so GPX is the file format produced here.
 */
public final class GpxWriter {
    private GpxWriter() {}

    /** Result of a build, including anything that had to be altered to fit the device. */
    public static final class Result {
        public final String gpx;
        public final List<String> warnings;

        Result(String gpx, List<String> warnings) {
            this.gpx = gpx;
            this.warnings = warnings;
        }
    }

    public static Result build(List<GpxModel.Point> waypoints, List<GpxModel.Route> routes) {
        List<String> warnings = new ArrayList<String>();
        StringBuilder sb = new StringBuilder(4096);

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        sb.append("<gpx version=\"1.1\" creator=\"ATAK-Garmin\" ")
          .append("xmlns=\"http://www.topografix.com/GPX/1/1\" ")
          .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
          .append("xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 ")
          .append("http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");

        int wptCount = 0;
        if (waypoints != null) {
            for (GpxModel.Point p : waypoints) {
                if (wptCount >= ForetrexLimits.MAX_WAYPOINTS) {
                    warnings.add("Waypoint limit of " + ForetrexLimits.MAX_WAYPOINTS
                            + " reached; dropped " + (waypoints.size() - wptCount) + " waypoint(s).");
                    break;
                }
                if (!validCoord(p)) {
                    warnings.add("Skipped waypoint with invalid coordinates: " + p.name);
                    continue;
                }
                appendPoint(sb, "wpt", p, warnings);
                wptCount++;
            }
        }

        int rteCount = 0;
        if (routes != null) {
            for (GpxModel.Route r : routes) {
                if (rteCount >= ForetrexLimits.MAX_ROUTES) {
                    warnings.add("Route limit of " + ForetrexLimits.MAX_ROUTES
                            + " reached; dropped " + (routes.size() - rteCount) + " route(s).");
                    break;
                }
                List<GpxModel.Point> pts = new ArrayList<GpxModel.Point>();
                for (GpxModel.Point p : r.points) {
                    if (validCoord(p)) pts.add(p);
                }
                if (pts.size() < 2) {
                    warnings.add("Skipped route \"" + r.name + "\": needs at least 2 valid points.");
                    continue;
                }
                if (pts.size() > ForetrexLimits.ROUTE_POINTS_MAX) {
                    warnings.add("Route \"" + r.name + "\" has " + pts.size() + " points; thinned to "
                            + ForetrexLimits.ROUTE_POINTS_MAX + ".");
                    pts = thin(pts, ForetrexLimits.ROUTE_POINTS_MAX);
                }
                sb.append("  <rte>\n");
                sb.append("    <name>").append(esc(fitName(r.name, warnings))).append("</name>\n");
                for (GpxModel.Point p : pts) {
                    appendPoint(sb, "rtept", p, warnings);
                }
                sb.append("  </rte>\n");
                rteCount++;
            }
        }

        sb.append("</gpx>\n");
        return new Result(sb.toString(), warnings);
    }

    private static void appendPoint(StringBuilder sb, String tag, GpxModel.Point p, List<String> warnings) {
        sb.append("  <").append(tag)
          .append(String.format(Locale.US, " lat=\"%.7f\" lon=\"%.7f\">\n", p.lat, p.lon));
        if (!Double.isNaN(p.elevation)) {
            sb.append(String.format(Locale.US, "    <ele>%.1f</ele>\n", p.elevation));
        }
        sb.append("    <name>").append(esc(fitName(p.name, warnings))).append("</name>\n");
        if (p.description != null && p.description.length() > 0) {
            sb.append("    <desc>").append(esc(p.description)).append("</desc>\n");
        }
        sb.append("    <sym>Flag, Blue</sym>\n");
        sb.append("  </").append(tag).append(">\n");
    }

    /** Upper-cases, strips non-printable chars, and truncates to the device name limit. */
    static String fitName(String name, List<String> warnings) {
        String n = name == null ? "" : name.trim();
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c >= 32 && c < 127) clean.append(c);
        }
        String s = clean.toString().toUpperCase(Locale.US);
        if (s.length() == 0) s = "WPT";
        if (s.length() > ForetrexLimits.NAME_MAX_CHARS) {
            String cut = s.substring(0, ForetrexLimits.NAME_MAX_CHARS);
            warnings.add("Name \"" + s + "\" shortened to \"" + cut + "\".");
            s = cut;
        }
        return s;
    }

    /** Evenly samples down to {@code max} points, always keeping first and last. */
    static List<GpxModel.Point> thin(List<GpxModel.Point> pts, int max) {
        List<GpxModel.Point> out = new ArrayList<GpxModel.Point>(max);
        int n = pts.size();
        for (int i = 0; i < max; i++) {
            int idx = (int) Math.round(i * (n - 1) / (double) (max - 1));
            out.add(pts.get(idx));
        }
        return out;
    }

    private static boolean validCoord(GpxModel.Point p) {
        return p != null && !Double.isNaN(p.lat) && !Double.isNaN(p.lon)
                && Math.abs(p.lat) <= 90 && Math.abs(p.lon) <= 180;
    }

    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
