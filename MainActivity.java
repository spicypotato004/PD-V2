package com.primadraw.plusv2;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Prima Draw Plus V2 as an Android app.
 * The drawing app itself is index.html (in assets). It is served from a https:// address inside
 * the app (WebViewAssetLoader) so the password lock, saved drawings and settings all work.
 * The bridge "PrimaAndroid" lets the page save files to the tablet and open the share sheet.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/index.html";
    private static final int PICK_FILES = 41;

    private WebView web;
    private WebViewAssetLoader assets;
    private ValueCallback<Uri[]> pickCallback;
    private long lastBack;

    private static final class Pending {
        String name, mime, mode;
        File file;
        OutputStream out;
    }
    private final Map<String, Pending> pending = new HashMap<>();
    private int counter;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 18));
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        cleanTemp();

        assets = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(58, 58, 58));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);                 // ignore the system font size so the layout stays the same
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        web.addJavascriptInterface(new Bridge(), "PrimaAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                openOutside(u);
                return true;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // the page ran out of memory or crashed: start it again instead of closing the app
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                view.destroy();
                if (view == web) web = null;
                recreate();
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pickCallback != null) pickCallback.onReceiveValue(null);
                pickCallback = callback;
                boolean images = true;
                String[] types = params.getAcceptTypes();
                if (types == null || types.length == 0) images = false;
                else for (String t : types) if (t == null || !t.trim().startsWith("image/")) { images = false; break; }
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType(images ? "image/*" : "*/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(pick, PICK_FILES);
                    return true;
                } catch (ActivityNotFoundException e) {
                    pickCallback = null;
                    return false;
                }
            }
        });

        web.setDownloadListener((url, userAgent, disposition, mime, length) -> {
            if (url != null && url.startsWith("http")) openOutside(Uri.parse(url));
        });

        setContentView(web);
        hideStatusBar();
        web.loadUrl(START);
    }

    private void hideStatusBar() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideStatusBar();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILES) {
            ValueCallback<Uri[]> cb = pickCallback;
            pickCallback = null;
            if (cb == null) return;
            Uri[] picked = null;
            if (resultCode == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    picked = new Uri[clip.getItemCount()];
                    for (int i = 0; i < picked.length; i++) picked[i] = clip.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    picked = new Uri[]{data.getData()};
                }
            }
            cb.onReceiveValue(picked);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        // the page closes panels first, then goes from the canvas to the gallery
        web.evaluateJavascript("(window.primaBack && window.primaBack()) ? 1 : 0", value -> {
            if ("1".equals(value)) return;
            long now = System.currentTimeMillis();
            if (now - lastBack < 2000) finish();
            else {
                lastBack = now;
                Toast.makeText(this, "Press back again to close", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void openOutside(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open that link", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- saving and sharing files from the page ----------

    private class Bridge {
        @JavascriptInterface
        public String begin(String name, String mime, String mode) {
            try {
                File dir = new File(getCacheDir(), "out");
                if (!dir.isDirectory() && !dir.mkdirs()) return "";
                Pending p = new Pending();
                p.name = safeName(name);
                String type = mime == null ? "" : mime.split(";")[0].trim().toLowerCase();   // "video/webm;codecs=vp9" -> "video/webm"
                p.mime = type.isEmpty() ? "application/octet-stream" : type;
                p.mode = mode;
                p.file = new File(dir, System.currentTimeMillis() + "-" + p.name);
                p.out = new FileOutputStream(p.file);
                synchronized (pending) {
                    String id = "f" + (++counter);
                    pending.put(id, p);
                    return id;
                }
            } catch (IOException e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean chunk(String id, String base64) {
            Pending p;
            synchronized (pending) { p = pending.get(id); }
            if (p == null) return false;
            try {
                p.out.write(Base64.decode(base64, Base64.DEFAULT));
                return true;
            } catch (IOException | IllegalArgumentException e) {
                return false;
            }
        }

        @JavascriptInterface
        public String finish(String id) {
            Pending p;
            synchronized (pending) { p = pending.remove(id); }
            if (p == null) return "error:the file got lost";
            try {
                p.out.close();
                if ("share".equals(p.mode) || Build.VERSION.SDK_INT < 29) {
                    share(p);
                    return "shared";
                }
                String where = saveToTablet(p);
                //noinspection ResultOfMethodCallIgnored
                p.file.delete();
                return "saved:" + where;
            } catch (Exception e) {
                return "error:" + (e.getMessage() == null ? "unknown" : e.getMessage());
            }
        }
    }

    private static String safeName(String name) {
        String n = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001f]", "_").trim();
        return n.isEmpty() ? "Prima Draw file" : n;
    }

    /** Saves into Pictures/Prima Draw, Movies/Prima Draw or Download/Prima Draw (Android 10 and newer). */
    private String saveToTablet(Pending p) throws IOException {
        Uri collection;
        String folder;
        if (p.mime.matches("image/(png|jpeg|webp|gif)")) {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_PICTURES + "/Prima Draw";
        } else if (p.mime.startsWith("video/")) {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_MOVIES + "/Prima Draw";
        } else {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            folder = Environment.DIRECTORY_DOWNLOADS + "/Prima Draw";
        }
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, p.name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, p.mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = cr.insert(collection, v);
        if (uri == null) throw new IOException("the tablet did not allow it");
        try (InputStream in = new FileInputStream(p.file); OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) throw new IOException("could not open the file");
            copy(in, out);
        } catch (IOException e) {
            cr.delete(uri, null, null);
            throw e;
        }
        v.clear();
        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, v, null, null);
        return folder;
    }

    private void share(Pending p) throws IOException {
        File dir = new File(getCacheDir(), "shared");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no space");
        File f = new File(dir, p.name);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        if (!p.file.renameTo(f)) {
            try (InputStream in = new FileInputStream(p.file); OutputStream out = new FileOutputStream(f)) { copy(in, out); }
            //noinspection ResultOfMethodCallIgnored
            p.file.delete();
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(p.mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri(p.name, uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, p.name);
        runOnUiThread(() -> {
            try {
                startActivity(chooser);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No app can take this file", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** Removes half-written files and files shared more than a day ago. */
    private void cleanTemp() {
        long dayAgo = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (String sub : new String[]{"out", "shared"}) {
            File[] files = new File(getCacheDir(), sub).listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (sub.equals("out") || f.lastModified() < dayAgo) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }
}
