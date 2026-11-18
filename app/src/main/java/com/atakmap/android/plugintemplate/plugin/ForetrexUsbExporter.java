package com.atakmap.android.plugintemplate.plugin;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.atakgarmin.core.ForetrexLimits;

import me.jahnen.libaums.core.UsbMassStorageDevice;
import me.jahnen.libaums.core.fs.FileSystem;
import me.jahnen.libaums.core.fs.UsbFile;
import me.jahnen.libaums.core.fs.UsbFileOutputStream;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Writes a GPX file onto a Foretrex 401 that is plugged into the phone over USB-OTG.
 *
 * The Foretrex 401 presents itself as USB mass storage. Android does not auto-mount that
 * for plugins, so this uses libaums to talk to the device directly (USB host mode).
 * The file lands in /Garmin/GPX/ on the device volume.
 */
public class ForetrexUsbExporter {

    public interface Callback {
        void onSuccess(String message);
        void onError(String message);
    }

    private static final String ACTION_USB_PERMISSION = "com.atakgarmin.USB_PERMISSION";

    private final Context context;

    public ForetrexUsbExporter(Context context) {
        this.context = context;
    }

    /** Finds the first connected mass-storage device, asks permission if needed, then writes. */
    public void export(final String fileName, final String gpx, final Callback cb) {
        final UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(context);
        if (devices.length == 0) {
            cb.onError("No USB storage device found. Plug the Foretrex in with a USB-OTG adapter "
                    + "and make sure it is switched on.");
            return;
        }
        final UsbMassStorageDevice dev = devices[0];
        final UsbManager usb = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        final UsbDevice raw = dev.getUsbDevice();

        if (usb.hasPermission(raw)) {
            new Thread(new Runnable() {
                @Override public void run() { write(dev, fileName, gpx, cb); }
            }).start();
            return;
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                context.unregisterReceiver(this);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    new Thread(new Runnable() {
                        @Override public void run() { write(dev, fileName, gpx, cb); }
                    }).start();
                } else {
                    cb.onError("USB permission was denied.");
                }
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent pi = PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_USB_PERMISSION).setPackage(context.getPackageName()), flags);
        usb.requestPermission(raw, pi);
    }

    private void write(UsbMassStorageDevice dev, String fileName, String gpx, Callback cb) {
        try {
            dev.init();
            if (dev.getPartitions().isEmpty()) {
                cb.onError("Device has no readable partition.");
                return;
            }
            FileSystem fs = dev.getPartitions().get(0).getFileSystem();
            UsbFile dir = fs.getRootDirectory();

            for (String part : ForetrexLimits.DEVICE_GPX_PATH) {
                UsbFile next = findChild(dir, part);
                if (next == null) next = dir.createDirectory(part);
                dir = next;
            }

            UsbFile existing = findChild(dir, fileName);
            if (existing != null) existing.delete();

            byte[] bytes = gpx.getBytes(Charset.forName("UTF-8"));
            UsbFile file = dir.createFile(fileName);
            file.setLength(bytes.length);
            UsbFileOutputStream out = new UsbFileOutputStream(file);
            try {
                out.write(bytes);
                out.flush();
            } finally {
                out.close();
            }
            cb.onSuccess("Wrote " + fileName + " (" + bytes.length + " bytes) to Garmin/GPX. "
                    + "Eject/unplug the Foretrex, then check its waypoint and route lists.");
        } catch (IOException e) {
            cb.onError("USB write failed: " + e.getMessage());
        } catch (RuntimeException e) {
            cb.onError("USB error: " + e);
        } finally {
            dev.close();
        }
    }

    private static UsbFile findChild(UsbFile dir, String name) throws IOException {
        for (UsbFile f : dir.listFiles()) {
            if (f.getName().equalsIgnoreCase(name)) return f;
        }
        return null;
    }
}
