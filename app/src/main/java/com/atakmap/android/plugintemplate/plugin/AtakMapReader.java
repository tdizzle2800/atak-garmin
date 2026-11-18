package com.atakmap.android.plugintemplate.plugin;

import com.atakgarmin.core.GpxModel;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.routes.Route;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls user waypoints and routes off the ATAK map into the plain model used by GpxWriter.
 *
 * NOTE: targets the current ATAK-CIV API; compile validation requires the matching official SDK.
 */
public class AtakMapReader {

    public static class Snapshot {
        public final List<GpxModel.Point> waypoints = new ArrayList<GpxModel.Point>();
        public final List<GpxModel.Route> routes = new ArrayList<GpxModel.Route>();
    }

    public static Snapshot read(MapView mapView) {
        final Snapshot snap = new Snapshot();

        mapView.getRootGroup().deepForEachItem(new MapGroup.MapItemsCallback() {
            @Override
            public boolean onItemFunction(MapItem item) {
                if (item instanceof Route) {
                    GpxModel.Route r = toRoute((Route) item);
                    if (r != null) snap.routes.add(r);
                } else if (item instanceof Marker && isUserWaypoint((Marker) item)) {
                    Marker m = (Marker) item;
                    GeoPoint gp = m.getPoint();
                    snap.waypoints.add(new GpxModel.Point(
                            m.getTitle(), gp.getLatitude(), gp.getLongitude(),
                            msl(gp), m.getMetaString("remarks", null)));
                }
                return false; // keep iterating
            }
        });
        return snap;
    }

    /** User-dropped waypoints ("b-m-p-w"). Skips route control points, other users, SPIs, etc. */
    private static boolean isUserWaypoint(Marker m) {
        String type = m.getType();
        return type != null && type.equals("b-m-p-w") && m.getVisible()
                && m.getMetaString("routeUID", null) == null;
    }

    private static GpxModel.Route toRoute(Route route) {
        GpxModel.Route out = new GpxModel.Route(route.getTitle());
        int n = route.getNumPoints();
        for (int i = 0; i < n; i++) {
            com.atakmap.android.maps.PointMapItem p = route.getPointMapItem(i);
            if (p == null) continue;
            GeoPoint gp = p.getPoint();
            String name = p.getTitle();
            if (name == null || name.length() == 0) name = "RP" + (i + 1);
            out.add(new GpxModel.Point(name, gp.getLatitude(), gp.getLongitude(), msl(gp), null));
        }
        return out.points.isEmpty() ? null : out;
    }

    /** ATAK stores HAE; convert to MSL when the SDK offers it, otherwise report unknown. */
    private static double msl(GeoPoint gp) {
        if (!gp.isAltitudeValid()) return Double.NaN;
        try {
            double msl = com.atakmap.coremap.maps.conversion.EGM96.getMSL(gp);
            return Double.isNaN(msl) ? Double.NaN : msl;
        } catch (Throwable t) {
            return Double.NaN; // better to omit elevation than send a wrong one
        }
    }
}
