package com.atakmap.android.plugintemplate.plugin;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.atakgarmin.core.ForetrexLimits;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Writes a GPX file into GARMIN/GPX on a Foretrex plugged into the phone over USB-OTG.
 *
 * Android mounts the Foretrex's drive (small, unpartitioned FAT) but does not make it visible
 * to apps as a file path. Apps reach it through the Storage Access Framework: the user picks the
 * GARMIN drive once in Android's folder picker, ATAK keeps that permission, and later sends go
 * straight to the drive.
 */
public class GarminDriveExporter {

    public interface Callback {
        void onInfo(String message);
        void onSuccess(String message);
        void onError(String message);
    }

    private static final String TAG = "GarminDriveExporter";
    private static final String PREFS = "atak_garmin";
    private static final String KEY_TREE = "garmin_tree_uri";
    private static final int GARMIN_VENDOR_ID = 0x091e;
    private static final int USB_CLASS_MASS_STORAGE = 8;
    private static final AtomicInteger REQUEST_SEQ = new AtomicInteger();

    private final Activity activity;
    private final ContentResolver resolver;
    private final SharedPreferences prefs;

    public GarminDriveExporter(Activity activity) {
        this.activity = activity;
        this.resolver = activity.getContentResolver();
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Call on the UI thread. Asks for the GARMIN drive the first time, then writes. */
    public void export(final String fileName, final String gpx, final Callback cb) {
        String usbProblem = checkUsb();
        if (usbProblem != null) {
            cb.onError(usbProblem);
            return;
        }

        Uri saved = savedTree();
        if (saved != null) {
            writeInBackground(saved, fileName, gpx, cb);
            return;
        }

        if (!(activity instanceof ComponentActivity)) {
            cb.onError("This ATAK version cannot open Android's folder picker for the plugin.");
            return;
        }
        final ActivityResultLauncher<?>[] holder = new ActivityResultLauncher<?>[1];
        ActivityResultLauncher<Uri> launcher = ((ComponentActivity) activity).getActivityResultRegistry()
                .register("atak_garmin_tree_" + REQUEST_SEQ.incrementAndGet(),
                        new ActivityResultContracts.OpenDocumentTree(),
                        new ActivityResultCallback<Uri>() {
                            @Override public void onActivityResult(Uri tree) {
                                if (holder[0] != null) holder[0].unregister();
                                if (tree == null) {
                                    cb.onError("No folder chosen. Tap Send again and pick the GARMIN drive.");
                                    return;
                                }
                                try {
                                    resolver.takePersistableUriPermission(tree,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                } catch (SecurityException e) {
                                    Log.w(TAG, "could not persist permission", e);
                                }
                                prefs.edit().putString(KEY_TREE, tree.toString()).apply();
                                writeInBackground(tree, fileName, gpx, cb);
                            }
                        });
        holder[0] = launcher;
        cb.onInfo("Pick the GARMIN drive, then tap \"Use this folder\" and Allow.");
        launcher.launch(null);
    }

    /** Returns a user-facing problem with the USB connection, or null if a Garmin drive is there. */
    private String checkUsb() {
        UsbManager usb = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        if (usb == null) return null; // cannot tell; let the picker decide
        boolean garmin = false;
        for (UsbDevice d : usb.getDeviceList().values()) {
            if (d.getVendorId() != GARMIN_VENDOR_ID) continue;
            garmin = true;
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface itf = d.getInterface(i);
                if (itf.getInterfaceClass() == USB_CLASS_MASS_STORAGE) return null;
            }
        }
        if (garmin) {
            return "The Foretrex is connected but not in USB storage mode yet. "
                    + "Wait a few seconds after switching it on, then tap Send again.";
        }
        return "No Garmin found. Plug the Foretrex into the phone with a USB-OTG cable "
                + "and switch it on.";
    }

    /** The saved GARMIN drive, if ATAK still holds write permission for it. */
    private Uri savedTree() {
        String s = prefs.getString(KEY_TREE, null);
        if (s == null) return null;
        Uri tree = Uri.parse(s);
        for (UriPermission p : resolver.getPersistedUriPermissions()) {
            if (p.getUri().equals(tree) && p.isWritePermission()) return tree;
        }
        prefs.edit().remove(KEY_TREE).apply();
        return null;
    }

    private void writeInBackground(final Uri tree, final String fileName, final String gpx,
                                   final Callback cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    cb.onSuccess(write(tree, fileName, gpx));
                } catch (Exception e) {
                    Log.e(TAG, "write failed", e);
                    if (e instanceof IllegalArgumentException) {
                        cb.onError(e.getMessage());
                    } else if (e instanceof SecurityException) {
                        prefs.edit().remove(KEY_TREE).apply();
                        cb.onError("Lost access to the GARMIN drive. Tap Send again and pick it.");
                    } else {
                        cb.onError("Could not write to the Foretrex: " + e.getMessage()
                                + ". Check it is plugged in and in USB storage mode.");
                    }
                }
            }
        }, "garmin-export").start();
    }

    private String write(Uri tree, String fileName, String gpx) throws IOException {
        String treeId = DocumentsContract.getTreeDocumentId(tree);
        Uri dir = DocumentsContract.buildDocumentUriUsingTree(tree, treeId);

        // The user may pick the drive itself, its GARMIN folder or GARMIN/GPX. Document IDs look
        // like "0000-0000:GARMIN/GPX", so the part after ':' says where we are on the drive.
        // (The drive's own label is also "GARMIN", so its display name cannot tell them apart.)
        String[] path = ForetrexLimits.DEVICE_GPX_PATH;
        String rel = treeId.substring(treeId.indexOf(':') + 1).replaceAll("^/+|/+$", "");
        int start = -1;
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i <= path.length; i++) {
            if (rel.equalsIgnoreCase(prefix.toString())) { start = i; break; }
            if (i < path.length) prefix.append(i == 0 ? "" : "/").append(path[i]);
        }
        if (start < 0) {
            prefs.edit().remove(KEY_TREE).apply();
            throw new IllegalArgumentException("The chosen folder is not on the GARMIN drive. "
                    + "Tap Send again and pick the drive itself.");
        }
        displayName(dir); // fails fast if the drive is unplugged
        for (int i = start; i < path.length; i++) {
            Uri next = findChild(tree, dir, path[i]);
            if (next == null) {
                next = DocumentsContract.createDocument(resolver, dir,
                        DocumentsContract.Document.MIME_TYPE_DIR, path[i]);
                if (next == null) throw new IOException("could not create folder " + path[i]);
            }
            dir = next;
        }

        Uri existing = findChild(tree, dir, fileName);
        if (existing != null) DocumentsContract.deleteDocument(resolver, existing);

        Uri file = DocumentsContract.createDocument(resolver, dir, "application/octet-stream", fileName);
        if (file == null) throw new IOException("could not create " + fileName);

        byte[] bytes = gpx.getBytes(Charset.forName("UTF-8"));
        OutputStream out = resolver.openOutputStream(file, "w");
        if (out == null) throw new IOException("could not open " + fileName);
        try {
            out.write(bytes);
            out.flush();
        } finally {
            out.close();
        }

        String written = displayName(file);
        Log.i(TAG, "wrote " + bytes.length + " bytes to " + file);
        return "Sent " + (written != null ? written : fileName) + " (" + bytes.length
                + " bytes) to GARMIN/GPX. Unplug the Foretrex; it loads the file when it restarts.";
    }

    private String displayName(Uri doc) {
        Cursor c = resolver.query(doc,
                new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
        if (c == null) throw new IllegalStateException("drive not available");
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    private Uri findChild(Uri tree, Uri dir, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                DocumentsContract.getDocumentId(dir));
        Cursor c = resolver.query(children, new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
        if (c == null) throw new IllegalStateException("drive not available");
        try {
            while (c.moveToNext()) {
                if (name.equalsIgnoreCase(c.getString(1))) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                }
            }
        } finally {
            c.close();
        }
        return null;
    }
}
