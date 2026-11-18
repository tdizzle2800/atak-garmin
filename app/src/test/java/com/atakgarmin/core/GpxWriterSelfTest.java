package com.atakgarmin.core;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/** Dependency-free self test (run with plain java; no JUnit needed). */
public class GpxWriterSelfTest {
    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        List<GpxModel.Point> wpts = new ArrayList<GpxModel.Point>();
        wpts.add(new GpxModel.Point("Rally <A> & \"B\"", 33.7490, -84.3880, 320.5, "Meet up"));
        wpts.add(new GpxModel.Point("Bad", 999, 0));

        GpxModel.Route r = new GpxModel.Route("Long Route Name Here");
        for (int i = 0; i < 600; i++) r.add(new GpxModel.Point("P" + i, 33 + i * 0.0001, -84 - i * 0.0001));

        GpxModel.Route tooShort = new GpxModel.Route("One");
        tooShort.add(new GpxModel.Point("only", 1, 1));

        List<GpxModel.Route> routes = new ArrayList<GpxModel.Route>();
        routes.add(r);
        routes.add(tooShort);

        GpxWriter.Result res = GpxWriter.build(wpts, routes);

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(res.gpx.getBytes("UTF-8")));
        check(doc.getElementsByTagName("wpt").getLength() == 1, "invalid waypoint skipped, valid kept");
        check(doc.getElementsByTagName("rte").getLength() == 1, "short route skipped");
        check(doc.getElementsByTagName("rtept").getLength() == ForetrexLimits.ROUTE_POINTS_MAX,
                "600-point route thinned to " + ForetrexLimits.ROUTE_POINTS_MAX);
        check(res.gpx.contains("RALLY &lt;A&gt;".substring(0, 5)), "name upper-cased");
        check(!res.warnings.isEmpty(), "warnings reported: " + res.warnings.size());

        List<GpxModel.Point> many = new ArrayList<GpxModel.Point>();
        for (int i = 0; i < 520; i++) many.add(new GpxModel.Point("W" + i, 10, 10));
        GpxWriter.Result res2 = GpxWriter.build(many, null);
        Document d2 = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(res2.gpx.getBytes("UTF-8")));
        check(d2.getElementsByTagName("wpt").getLength() == ForetrexLimits.MAX_WAYPOINTS,
                "waypoints capped at " + ForetrexLimits.MAX_WAYPOINTS);

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
