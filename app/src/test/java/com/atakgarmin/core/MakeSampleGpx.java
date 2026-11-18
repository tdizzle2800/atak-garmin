package com.atakgarmin.core;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Writes sample.gpx so you can hand-copy it to the Foretrex's Garmin/GPX folder as a first test. */
public class MakeSampleGpx {
    public static void main(String[] args) throws Exception {
        List<GpxModel.Point> w = new ArrayList<GpxModel.Point>();
        w.add(new GpxModel.Point("TEST1", 33.7490, -84.3880, 320.0, null));
        w.add(new GpxModel.Point("TEST2", 33.7550, -84.3900, 318.0, null));
        GpxModel.Route r = new GpxModel.Route("TESTRTE");
        r.add(new GpxModel.Point("R1", 33.7490, -84.3880));
        r.add(new GpxModel.Point("R2", 33.7520, -84.3890));
        r.add(new GpxModel.Point("R3", 33.7550, -84.3900));
        List<GpxModel.Route> rs = new ArrayList<GpxModel.Route>();
        rs.add(r);
        GpxWriter.Result res = GpxWriter.build(w, rs);
        FileOutputStream out = new FileOutputStream(args.length > 0 ? args[0] : "sample.gpx");
        out.write(res.gpx.getBytes("UTF-8"));
        out.close();
    }
}
