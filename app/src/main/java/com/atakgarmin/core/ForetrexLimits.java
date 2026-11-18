package com.atakgarmin.core;

/**
 * Device limits used when packaging data for a Garmin Foretrex 401.
 *
 * Documented by retailers/Garmin: 500 waypoints, 20 routes, 10,000 track points.
 * NAME_MAX_CHARS and ROUTE_POINTS_MAX are conservative ASSUMPTIONS, not verified
 * against the owner's manual. Verify on the real device and adjust here.
 */
public final class ForetrexLimits {
    private ForetrexLimits() {}

    public static final int MAX_WAYPOINTS = 500;
    public static final int MAX_ROUTES = 20;

    /** Assumption: Foretrex display/storage handles short names best. Verify. */
    public static final int NAME_MAX_CHARS = 10;

    /** Assumption: per-route point cap. Verify. */
    public static final int ROUTE_POINTS_MAX = 250;

    /** Folder on the device's USB mass-storage volume that holds GPX files. */
    public static final String[] DEVICE_GPX_PATH = {"Garmin", "GPX"};
}
