package com.atakmap.android.plugintemplate.plugin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import com.atakgarmin.core.GpxWriter;
import com.atakmap.android.maps.MapView;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;
import com.atak.plugins.impl.PluginContextProvider;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Entry point. Adds a toolbar button; tapping it summarises what is on the map and offers to
 * send it to a USB-connected Foretrex 401 as a GPX file.
 *
 * Built against the ATAK-CIV 5.6.0 SDK (ATAK_VERSION in app/build.gradle).
 */
public class PluginTemplate implements IPlugin {

    private final IServiceController serviceController;
    private final Context pluginContext;
    private final IHostUIService uiService;
    private ToolbarItem toolbarItem;

    public PluginTemplate(IServiceController serviceController) {
        this.serviceController = serviceController;
        PluginContextProvider ctxProvider = serviceController.getService(PluginContextProvider.class);
        this.pluginContext = ctxProvider.getPluginContext();
        this.uiService = serviceController.getService(IHostUIService.class);

        toolbarItem = new ToolbarItem.Builder(
                "Foretrex",
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override public void onClick(ToolbarItem item) { showDialog(); }
                })
                .build();
    }

    @Override
    public void onStart() {
        if (uiService != null) uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        if (uiService != null) uiService.removeToolbarItem(toolbarItem);
    }

    private void showDialog() {
        final MapView mapView = MapView.getMapView();
        final Context ui = mapView.getContext();
        final AtakMapReader.Snapshot snap = AtakMapReader.read(mapView);
        final GpxWriter.Result result = GpxWriter.build(snap.waypoints, snap.routes);

        StringBuilder msg = new StringBuilder();
        msg.append(snap.waypoints.size()).append(" waypoint(s), ")
           .append(snap.routes.size()).append(" route(s) found on the map.");
        if (!result.warnings.isEmpty()) {
            msg.append("\n\nAdjustments for the Foretrex:");
            int shown = 0;
            for (String w : result.warnings) {
                if (shown++ >= 8) { msg.append("\n...and more"); break; }
                msg.append("\n- ").append(w);
            }
        }
        msg.append("\n\nPlug the Foretrex into the phone with a USB-OTG cable, then tap Send.");

        new AlertDialog.Builder(ui)
                .setTitle("Send to Foretrex 401")
                .setMessage(msg.toString())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        send(ui, result.gpx);
                    }
                })
                .show();
    }

    private void send(final Context ui, String gpx) {
        String name = "ATAK_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".gpx";
        new ForetrexUsbExporter(ui).export(name, gpx, new ForetrexUsbExporter.Callback() {
            @Override public void onSuccess(final String m) { toast(ui, m); }
            @Override public void onError(final String m) { toast(ui, m); }
        });
    }

    private void toast(final Context ui, final String m) {
        MapView.getMapView().post(new Runnable() {
            @Override public void run() { Toast.makeText(ui, m, Toast.LENGTH_LONG).show(); }
        });
    }
}
