package com.meridian.shell;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.telephony.TelephonyManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    static final boolean OPEN = false;
    /** Where "Buy" sends people. Use a merchant-of-record that emails a licence key (Lemon Squeezy, Paddle, Gumroad). */
    private static final String PAY_URL = "https://checkout.revolut.com/pay/044a268e-4869-4708-9b0f-8e302cc28c37";
    /**
     * P-256 public key (X.509 SubjectPublicKeyInfo, base64) that signs licence keys.
     * Generate your own pair with tools/license/keygen.mjs and paste the public half here.
     */
    private static final String LICENSE_PUBKEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0kg0I0zYe441u6+ko7KBk9PRZ7W1+8LQlc6y3OdXuqZtJnFMvbqQT00Jr7zLjYlLa2ZMKhKLFtBBstjter7WCg==";
    /** Length of the free trial that unlocks Pro pages before a licence key is entered. */
    private static final int TRIAL_DAYS = 7;
    private WebView web;
    static final Object GATE = new Object();
    static volatile String pendingOut = "";
    /** Hard cap for one job's text crossing into the WebView. The tail is what the console shows. */
    static final int OUT_CAP = 24000;
    static volatile int jobGen = 0;
    static volatile int pendingCode = -1;
    private volatile String speedJson = "";
    static volatile boolean ready = false;
    static volatile boolean jobLive = false;
    static volatile java.net.ServerSocket liveSock;
    /** Random per-job secret: a local socket client must send it as its first line or be ignored. */
    static volatile String liveToken = "";

    private JSONObject cachedProps;
    private String cachedProbe = "";
    private long probeAt = 0;
    private long prevIdle = -1;
    private long prevTotal = -1;
    private int coreCount = 0;
    private long prevRx = -1;
    private long prevTx = -1;
    private long prevNetAt = 0;
    private double shownRx = 0;
    private double shownTx = 0;
    private boolean netClosed = false;
    private int netStall = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView web = new WebView(this);
        this.web = web;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        // file:///android_asset still loads with these off; they only block reading arbitrary files from the page.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        // Without a WebChromeClient, WebView silently cancels prompt()/confirm()/alert() and the
        // "New file" / "New folder" buttons do nothing.
        web.setWebChromeClient(new android.webkit.WebChromeClient());
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // Keep the shell inside its own page; external links go through Deck.open() and its allow-list.
                return true;
            }
        });
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.addJavascriptInterface(new Deck(), "MeridianPhone");
        web.loadUrl("file:///android_asset/www/index.html");
        setContentView(web);
        fitKeyboard(web);
        adoptLegacyUnlock();
    }

    /** Pads the page only while the keyboard covers it. A nav-bar inset stays below the threshold. */
    private void fitKeyboard(final WebView web) {
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
        web.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (MainActivity.this.web == null) return;
                android.graphics.Rect frame = new android.graphics.Rect();
                MainActivity.this.web.getWindowVisibleDisplayFrame(frame);
                int root = MainActivity.this.web.getRootView().getHeight();
                if (root <= 0) return;
                int overlap = root - frame.bottom;
                int pad = overlap > root / 6 ? overlap : 0;
                if (MainActivity.this.web.getPaddingBottom() != pad) MainActivity.this.web.setPadding(0, 0, 0, pad);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        // Let the page unwind its own navigation first; only leave the app when it says there is nothing to go back to.
        web.evaluateJavascript("(function(){ try { return window.meridianBack ? String(window.meridianBack()) : 'false'; } catch (e) { return 'false'; } })()",
            new android.webkit.ValueCallback<String>() {
                public void onReceiveValue(String value) {
                    if (value == null || !value.contains("true")) moveTaskToBack(true);
                }
            });
    }

    private void keepAwake(final boolean on) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private String readCap(InputStream stream, int cap) throws Exception {
        if (stream == null) return "";
        byte[] buf = new byte[2048];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = stream.read(buf)) > 0 && sb.length() < cap) {
            sb.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
        }
        stream.close();
        if (sb.length() > cap) return sb.substring(0, cap);
        return sb.toString();
    }

    /**
     * Sum of download_count on .apk assets from the public GitHub releases list.
     * The request is only that URL. It does not send a licence, a key, or the device model.
     */
    private String apkDownloadTotal() {
        String next = "https://api.github.com/repos/kukirin-ui/Meridian/releases?per_page=100";
        long total = 0;
        for (int page = 0; page < 5 && next != null; page++) {
            if (!releasesUrl(next)) return null;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(next).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Meridian");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                if (conn.getResponseCode() != 200) return null;
                JSONArray releases = new JSONArray(readCap(conn.getInputStream(), 1000000));
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject rel = releases.optJSONObject(i);
                    if (rel == null) continue;
                    JSONArray assets = rel.optJSONArray("assets");
                    if (assets == null) continue;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.optJSONObject(j);
                        if (asset == null) continue;
                        String name = asset.optString("name", "");
                        if (name.toLowerCase(Locale.US).endsWith(".apk")) total += asset.optLong("download_count", 0);
                    }
                }
                next = nextReleasePage(conn.getHeaderField("Link"));
            } catch (Exception ex) {
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return String.valueOf(total);
    }

    private boolean releasesUrl(String url) {
        if (url == null || !url.startsWith("https://api.github.com/")) return false;
        if (url.indexOf('@') >= 0 || url.indexOf(' ') >= 0 || url.indexOf('\\') >= 0) return false;
        if (url.startsWith("https://api.github.com/repos/kukirin-ui/Meridian/releases")) return true;
        return url.startsWith("https://api.github.com/repositories/") && url.indexOf("/releases") >= 0;
    }

    private String nextReleasePage(String link) {
        if (link == null) return null;
        String[] parts = link.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.indexOf("rel=\"next\"") < 0) continue;
            int a = part.indexOf('<');
            int b = part.indexOf('>');
            if (a < 0 || b <= a) return null;
            String url = part.substring(a + 1, b);
            return releasesUrl(url) ? url : null;
        }
        return null;
    }

    private String netErr(Exception ex) {
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return "{\"ok\":false,\"error\":" + JSONObject.quote(msg) + "}";
    }

    private long[] pull(String spec, int cap) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(spec).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(50000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Cache-Control", "no-store");
            long start = System.nanoTime();
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code);
            in = conn.getInputStream();
            long readStart = System.nanoTime();
            byte[] buf = new byte[16384];
            long bytes = 0;
            int n;
            while ((n = in.read(buf)) > 0 && bytes < cap) bytes += n;
            long readMs = Math.max(1, (System.nanoTime() - readStart) / 1000000L);
            long totalMs = Math.max(1, (System.nanoTime() - start) / 1000000L);
            if (bytes <= 0) throw new Exception("no bytes");
            return new long[] { bytes, readMs, totalMs };
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private long postUp(byte[] body) throws Exception {
        HttpURLConnection conn = null;
        OutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(50000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setFixedLengthStreamingMode(body.length);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            long start = System.nanoTime();
            out = conn.getOutputStream();
            out.write(body);
            out.flush();
            out.close();
            out = null;
            int code = conn.getResponseCode();
            long ms = Math.max(1, (System.nanoTime() - start) / 1000000L);
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            return ms;
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private boolean keepAlive(String pkg) {
        if (pkg == null) return true;
        if (pkg.equals(getPackageName())) return true;
        if (pkg.startsWith("com.termux")) return true;
        if (pkg.equals("android")) return true;
        if (pkg.contains("systemui") || pkg.contains("launcher")) return true;
        if (pkg.startsWith("com.android.phone") || pkg.startsWith("com.android.server")) return true;
        if (pkg.startsWith("com.android.bluetooth") || pkg.startsWith("com.android.nfc")) return true;
        if (pkg.startsWith("com.android.providers.")) return true;
        if (pkg.startsWith("com.google.android.gms") || pkg.startsWith("com.google.android.gsf")) return true;
        if (pkg.contains("inputmethod") || pkg.contains(".ime") || pkg.contains("honeyboard")) return true;
        return false;
    }

    private int killOthers(ActivityManager am) {
        int asked = 0;
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs != null) {
            for (int i = 0; i < procs.size(); i++) {
                android.app.ActivityManager.RunningAppProcessInfo info = procs.get(i);
                if (info.importance < 300) continue;
                String[] pkgs = info.pkgList;
                if (pkgs == null) continue;
                for (int j = 0; j < pkgs.length; j++) {
                    if (keepAlive(pkgs[j]) || !seen.add(pkgs[j])) continue;
                    am.killBackgroundProcesses(pkgs[j]);
                    asked++;
                }
            }
        }
        java.util.List<ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
        for (int i = 0; i < apps.size(); i++) {
            String pkg = apps.get(i).packageName;
            if (keepAlive(pkg) || !seen.add(pkg)) continue;
            if ((apps.get(i).flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            am.killBackgroundProcesses(pkg);
            asked++;
        }
        return asked;
    }

    private boolean termuxInstalled() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String randomToken() {
        byte[] raw = new byte[12];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder sb = new StringBuilder();
        for (byte b : raw) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    private Intent termuxIntent(String script, boolean background) {
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] { "-lc", script });
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", background);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        return intent;
    }

    /** Opens a visible Termux session running the command. Used for interactive programs (vim, btop, proot login) that cannot run headless. */
    private boolean openInTermux(String cmd) {
        if (cmd == null) cmd = "";
        String script = "mkdir -p \"$HOME/.meridian\"\n"
            + "printf -- %s " + sh(cmd) + " > \"$HOME/.meridian/open.sh\"\n"
            + "bash \"$HOME/.meridian/open.sh\"; echo; echo '[Meridian] Command finished. Press Enter to close.'; read -r _";
        boolean sent = sendToTermux(termuxIntent(script, false));
        wakeTermux();
        return sent;
    }

    /** Signals the running job to stop. The runner records its PID in ~/.meridian/pid; children are killed first so bash does not sit on a frozen child. */
    private boolean cancelJob() {
        int gen;
        synchronized (GATE) {
            if (ready) return false;
            gen = jobGen;
        }
        String script = "p=$(cat \"$HOME/.meridian/pid\" 2>/dev/null) || exit 0; [ -n \"$p\" ] || exit 0; "
            + "for d in /proc/[0-9]*; do pp=$(awk '/^PPid:/{print $2}' \"$d/status\" 2>/dev/null) || continue; [ \"$pp\" = \"$p\" ] && kill -TERM \"$(basename \"$d\")\" 2>/dev/null; done; "
            + "kill -TERM \"$p\" 2>/dev/null; sleep 1; kill -KILL \"$p\" 2>/dev/null; exit 0";
        boolean sent = sendToTermux(termuxIntent(script, true));
        synchronized (GATE) {
            if (gen == jobGen && !ready) {
                pendingOut = pendingOut + "\n[stopped by you]";
                pendingCode = 130;
                ready = true;
                jobGen++; // a late result from the killed job is ignored by TermuxResult
                GATE.notifyAll();
            }
            try { if (liveSock != null) liveSock.close(); } catch (Exception ignored) {}
        }
        return sent;
    }

    private void launch(String cmd) {
        synchronized (GATE) {
            jobGen++;
            ready = false;
            pendingOut = "";
            pendingCode = -1;
        }
        int gen = jobGen;
        liveToken = randomToken();
        int port = openLive();
        Intent intent = termuxIntent(prepare(cmd, port), true);
        Intent back = new Intent(this, TermuxResult.class);
        back.setAction("com.meridian.deck.TERMUX_RESULT");
        back.putExtra("gen", gen);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(this, 1000 + (gen % 50), back, flags);
        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
        if (sendToTermux(intent)) return;
        wakeTermux();
        try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
        if (sendToTermux(intent)) {
            returnToUs();
            return;
        }
        returnToUs();
        synchronized (GATE) {
            pendingOut = "Termux was asleep. Android blocked the command because Termux had no running process. Open Termux, leave it open for a second, then run this again.";
            pendingCode = 1;
            ready = true;
            GATE.notifyAll();
        }
    }

    private boolean sendToTermux(Intent intent) {
        try {
            startService(intent);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void wakeTermux() {
        try {
            Intent wake = getPackageManager().getLaunchIntentForPackage("com.termux");
            if (wake == null) return;
            wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(wake);
        } catch (Exception ignored) {}
    }

    private void returnToUs() {
        runOnUiThread(new Runnable() {
            public void run() {
                Intent back = new Intent(MainActivity.this, MainActivity.class);
                back.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(back);
            }
        });
    }

    private int openLive() {
        try {
            java.net.ServerSocket old = liveSock;
            liveSock = null;
            if (old != null) try { old.close(); } catch (Exception ignored) {}
            final java.net.ServerSocket sock = new java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"));
            liveSock = sock;
            new Thread(new Runnable() {
                public void run() { readLive(sock); }
            }).start();
            return sock.getLocalPort();
        } catch (Exception ex) {
            return 0;
        }
    }

    private void readLive(java.net.ServerSocket sock) {
        StringBuilder sb = new StringBuilder();
        boolean trimmed = false;
        long lastPublish = 0;
        try {
            while (liveSock == sock && !sock.isClosed()) {
                java.net.Socket client = sock.accept();
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    String first = reader.readLine();
                    // Any local app can connect to 127.0.0.1; only streams that know this job's token may write to the console.
                    if (first == null || !first.equals(liveToken)) continue;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                        if (sb.length() > OUT_CAP) {
                            sb.delete(0, sb.length() - (OUT_CAP - 32));
                            trimmed = true;
                        }
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - lastPublish >= 250) {
                            lastPublish = now;
                            publishLive(sb, trimmed);
                        }
                    }
                    publishLive(sb, trimmed);
                } catch (Exception ignored) {
                } finally {
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    /** Publishes the tail of the live log. Called off the WebView thread. */
    private void publishLive(StringBuilder sb, boolean trimmed) {
        String text = sb.toString();
        if (trimmed && !text.startsWith("[earlier output trimmed]")) text = "[earlier output trimmed]\n" + text;
        text = clipOut(text);
        synchronized (GATE) {
            if (!ready) pendingOut = text;
        }
    }

    /** Keeps the bridge payload inside OUT_CAP, preserving the newest text. */
    static String clipOut(String out) {
        if (out == null || out.length() <= OUT_CAP) return out == null ? "" : out;
        String note = "[earlier output trimmed]\n";
        int keep = OUT_CAP - note.length();
        return note + out.substring(out.length() - keep);
    }

    private String prepare(String cmd, int port) {
        if (cmd == null) cmd = "";
        // The user's text is one quoted printf argument. This heredoc is only the fixed runner, so a line in the command cannot close it.
        String runTag = "MERIDIAN_RUN_" + randomToken();
        String tok = "printf '%s\\n' '" + liveToken + "'";
        String nc = "/system/bin/toybox nc 127.0.0.1 " + port;
        String hello = port > 0 ? "{ " + tok + "; } | /system/bin/toybox nc -w 2 127.0.0.1 " + port + " >/dev/null 2>&1 || true\n" : "";
        String run;
        if (port > 0) {
            run = "if [ -x /system/bin/script ]; then { " + tok + "; /system/bin/script -q -c \"bash \\\"$HOME/.meridian/run.sh\\\"\" \"$HOME/.meridian/live.log\"; } | " + nc + "; status=${PIPESTATUS[0]}; "
                + "elif command -v script >/dev/null 2>&1; then { " + tok + "; script -q -f -c \"bash \\\"$HOME/.meridian/run.sh\\\"\" \"$HOME/.meridian/live.log\"; } | " + nc + "; status=${PIPESTATUS[0]}; "
                + "else bash \"$HOME/.meridian/run.sh\" > \"$HOME/.meridian/live.log\" 2>&1 & pid=$!; pos=0; while kill -0 \"$pid\" 2>/dev/null; do size=$(wc -c < \"$HOME/.meridian/live.log\" 2>/dev/null || echo 0); if [ \"$size\" -gt \"$pos\" ]; then { " + tok + "; tail -c +$((pos+1)) \"$HOME/.meridian/live.log\"; } | " + nc + " || true; pos=$size; fi; sleep 0.4; done; wait \"$pid\"; status=$?; fi\n";
        } else {
            run = "bash \"$HOME/.meridian/run.sh\" > \"$HOME/.meridian/live.log\" 2>&1; status=$?\n";
        }
        String locks = "\"$PREFIX/var/lib/dpkg/lock-frontend\" \"$PREFIX/var/lib/dpkg/lock\" \"$PREFIX/var/cache/apt/archives/lock\" \"$PREFIX/var/lib/apt/lists/lock\"";
        String scanApt = "alive=0; for d in /proc/[0-9]*; do c=$(cat \"$d/comm\" 2>/dev/null) || continue; case \"$c\" in apt|apt-get|dpkg) alive=1;; esac; done; ";
        return "export DEBIAN_FRONTEND=noninteractive; export DEBCONF_NONINTERACTIVE_SEEN=true; "
            + "mkdir -p \"$PREFIX/etc/apt/apt.conf.d\" \"$PREFIX/etc/dpkg/dpkg.cfg.d\" \"$HOME/.meridian\" 2>/dev/null; "
            + "printf '%s\\n' 'Dpkg::Options {\"--force-confdef\";\"--force-confold\";};' > \"$PREFIX/etc/apt/apt.conf.d/99meridian\" 2>/dev/null; "
            + "printf '%s\\n' 'force-confdef' 'force-confold' > \"$PREFIX/etc/dpkg/dpkg.cfg.d/99meridian\" 2>/dev/null; "
            + "printf -- %s " + sh(cmd) + " > \"$HOME/.meridian/cmd.sh\"\n"
            + "cat > \"$HOME/.meridian/run.sh\" << '" + runTag + "'\n"
            // The command runs as a child whose PID is recorded, so Stop can kill exactly this job.
            + "bash \"$HOME/.meridian/cmd.sh\" </dev/null & p=$!\n"
            + "echo $p > \"$HOME/.meridian/pid\"\n"
            + "wait $p; rc=$?\n"
            + "rm -f \"$HOME/.meridian/pid\"\n"
            + "echo $rc > \"$HOME/.meridian/code\"\n"
            + runTag + "\n"
            + hello
            // Never kill -9 a live dpkg: that is how package databases get corrupted. Only clear locks nobody holds.
            + scanApt
            + (usesPackage(cmd)
                ? "if [ \"$alive\" = 1 ]; then echo 'Another package command is still running in Termux. Wait for it, or stop it there, then run this again.'; exit 75; fi; "
                    + "rm -f " + locks + "; dpkg --configure -a --force-confdef --force-confold || true\n"
                : "if [ \"$alive\" = 0 ] && { [ -e \"$PREFIX/var/lib/dpkg/lock-frontend\" ] || [ -e \"$PREFIX/var/lib/dpkg/lock\" ]; }; then rm -f " + locks + "; fi\n")
            + ": > \"$HOME/.meridian/live.log\"\n"
            + run
            + "bytes=$(wc -c < \"$HOME/.meridian/live.log\" 2>/dev/null || echo 0)\n"
            + "if [ \"$bytes\" -gt 40000 ]; then printf '%s\\n' '[earlier output trimmed]'; tail -c 40000 \"$HOME/.meridian/live.log\"; else cat \"$HOME/.meridian/live.log\"; fi\n"
            + "if [ -f \"$HOME/.meridian/code\" ]; then status=$(cat \"$HOME/.meridian/code\"); fi\n"
            + "exit ${status:-0}\n";
    }

    private boolean usesPackage(String cmd) {
        if (cmd == null) return false;
        return cmd.matches("(?s).*(\\bpkg\\b|\\bapt-get\\b|\\bapt\\b|\\bdpkg\\b).*");
    }

    private String waitResult(long ms) {
        synchronized (GATE) {
            long end = ms <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + ms;
            while (!ready && System.currentTimeMillis() < end) {
                try {
                    GATE.wait(ms <= 0 ? 1000 : Math.min(500, end - System.currentTimeMillis()));
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            if (!ready) return "still running";
            return clipOut(pendingOut) + "\nexit " + pendingCode;
        }
    }

    private String sh(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /** Rejects a command the shell cannot carry. Quotes, pipes, newlines, and emoji stay: they are the script. */
    private String refuseCmd(String cmd) {
        if (cmd != null && cmd.indexOf('\0') >= 0) return "That command contains a character the shell cannot run.";
        if (cmd != null && cmd.length() > 100000) return "That command is too long to run from here.";
        return null;
    }

    /** Quoted shell path, or null when the path is outside the Termux home or malformed. Callers must refuse to act on null. */
    private String shellPath(String path) {
        String value = safePath(path);
        if (value == null) return null;
        if ("$HOME".equals(value)) return "\"$HOME\"";
        if (value.startsWith("$HOME/")) return "\"$HOME\"/" + sh(value.substring(6));
        return sh(value);
    }

    private static final String BAD_PATH = "That path is outside the Termux home, so nothing was done\nexit 1";

    /**
     * The original fell back to "$HOME" for any path it did not like, which turned
     * removePath("bad") into rm -rf "$HOME". Invalid paths are now rejected, never substituted.
     */
    private String safePath(String path) {
        if (path == null) return null;
        String value = path.trim();
        if (value.length() == 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) return null;
        for (String part : value.split("/")) if (part.equals("..")) return null;
        if (value.startsWith("/")) {
            String home = "/data/data/com.termux/files/home";
            if (!value.equals(home) && !value.startsWith(home + "/")) return null;
            return value;
        }
        if (value.equals("$HOME") || value.startsWith("$HOME/")) return value;
        return null;
    }

    class Deck {
        @JavascriptInterface
        public String status() {
            try {
                JSONObject props = props();
                JSONObject o = new JSONObject();
                String model = Build.MODEL == null ? "" : Build.MODEL;
                String shown = props.optString("shown", "");
                if (shown.length() < 2) shown = model;
                if (shown.length() == 0) shown = "This phone";
                String brand = present(props.optString("brand", ""));
                o.put("model", shown);
                // Build.MODEL and brand stay language-neutral. shown/marketname can be a
                // locale string from the ROM (for example a Croatian marketing name).
                o.put("product", model);
                o.put("brand", brand);
                o.put("rawModel", (brand.length() > 0 ? brand + " " : "") + model);
                o.put("release", Build.VERSION.RELEASE == null ? "" : Build.VERSION.RELEASE);
                o.put("abi", Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
                o.put("chip", props.optString("soc", ""));
                o.put("gpu", props.optString("gpu", ""));
                o.put("platform", props.optString("platform", ""));
                BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                int pct = bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                o.put("battery", pct);
                Intent bat = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                boolean tempKnown = bat != null && bat.hasExtra(BatteryManager.EXTRA_TEMPERATURE);
                int temp = tempKnown ? bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) : 0;
                int volt = bat == null ? 0 : bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                o.put("tempKnown", tempKnown && temp > 0);
                o.put("temp", tempKnown ? temp / 10.0 : 0);
                o.put("voltage", volt / 1000.0);
                int state = bat == null ? -1 : bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String stateName = "Battery";
                if (state == BatteryManager.BATTERY_STATUS_CHARGING) stateName = "Charging";
                else if (state == BatteryManager.BATTERY_STATUS_FULL) stateName = "Full";
                else if (state == BatteryManager.BATTERY_STATUS_DISCHARGING) stateName = "Discharging";
                else if (state == BatteryManager.BATTERY_STATUS_NOT_CHARGING) stateName = "Not charging";
                o.put("state", stateName);
                int micro = bm == null ? Integer.MIN_VALUE : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                o.put("currentMa", micro == Integer.MIN_VALUE ? 0 : Math.round(micro / 1000.0));
                int uah = bm == null ? Integer.MIN_VALUE : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                o.put("chargeMah", uah == Integer.MIN_VALUE || uah <= 0 ? 0 : Math.round(uah / 1000.0));
                try {
                    android.view.Display display = getWindowManager().getDefaultDisplay();
                    android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
                    display.getRealMetrics(dm);
                    o.put("width", Math.min(dm.widthPixels, dm.heightPixels));
                    o.put("height", Math.max(dm.widthPixels, dm.heightPixels));
                    o.put("dpi", dm.densityDpi);
                    o.put("hz", Math.round(display.getRefreshRate()));
                } catch (Exception ignored) {}
                o.put("termux", termuxInstalled());
                o.put("allowed", checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED);
                o.put("power", powerNow());
                return o.toString();
            } catch (Exception ex) {
                return "{\"error\":\"status\"}";
            }
        }

        @JavascriptInterface
        public String probe() {
            try {
                long now = System.currentTimeMillis();
                double[] net = netRate();
                long[] link = linkBytes();
                if (cachedProbe.length() > 0 && now - probeAt < 80) {
                    JSONObject cached = new JSONObject(cachedProbe);
                    cached.put("rx", net[0]);
                    cached.put("tx", net[1]);
                    cached.put("netClosed", netClosed);
                    cached.put("linkDown", link[0]);
                    cached.put("linkUp", link[1]);
                    return cached.toString();
                }
                JSONObject props = props();
                JSONObject o = new JSONObject();
                o.put("cpuPct", cpuPct());
                o.put("load1", load1());
                o.put("cores", coreCount);
                o.put("coresLive", cpuCores());
                o.put("clocks", cpuClocks());
                long[] mem = meminfo();
                o.put("memTotal", mem[0]);
                o.put("memFree", mem[1]);
                o.put("swapTotal", mem[2]);
                o.put("swapFree", mem[3]);
                o.put("memCached", mem[4]);
                o.put("memBuffers", mem[5]);
                o.put("memRaw", mem[6]);
                long[] zram = zram();
                o.put("zramTotal", zram[0]);
                o.put("zramOrig", zram[1]);
                o.put("zramComp", zram[2]);
                o.put("ramPlus", ramPlusBytes());
                long[] disk = diskBytes();
                o.put("diskTotal", disk[0]);
                o.put("diskFree", disk[1]);
                o.put("gpuLoad", gpuLoad());
                o.put("gpuHz", gpuHz());
                o.put("zones", thermals());
                o.put("rx", net[0]);
                o.put("tx", net[1]);
                o.put("netClosed", netClosed);
                o.put("linkDown", link[0]);
                o.put("linkUp", link[1]);
                o.put("kernel", readLine("/proc/version"));
                o.put("cpuinfo", cpuName());
                String[] keys = new String[] { "brand", "device", "board", "maker", "hardware", "soc", "egl", "vulkan", "renderer", "glVersion", "glVendor", "platform", "abi", "sdk", "patch", "buildId", "built", "oneui", "fingerprint", "gpu", "ramType", "ufs" };
                for (String key : keys) o.put(key, props.optString(key, ""));
                cachedProbe = o.toString();
                probeAt = now;
                return cachedProbe;
            } catch (Exception ex) {
                return "{\"error\":\"probe\"}";
            }
        }

        @JavascriptInterface
        public String bond() {
            if (!termuxInstalled()) return "missing";
            if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        requestPermissions(new String[] { "com.termux.permission.RUN_COMMAND" }, 7);
                    }
                });
                return "ask";
            }
            return "termux";
        }

        @JavascriptInterface
        public void termuxBattery() {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.parse("package:com.termux"));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public String openCare() {
            String[][] targets = new String[][] {
                { "com.samsung.android.lool", "com.samsung.android.sm.ui.ram.RamActivity2" },
                { "com.samsung.android.lool", "com.samsung.android.sm.ui.ram.RamActivity" },
                { "com.samsung.android.lool", "com.samsung.android.sm.ram.RamActivity2" }
            };
            for (int i = 0; i < targets.length; i++) {
                if (openPart(targets[i][0], targets[i][1])) return "open";
            }
            String[] pkgs = new String[] {
                "com.samsung.android.lool",
                "com.miui.securitycenter",
                "com.miui.cleanmaster",
                "com.coloros.phonemanager",
                "com.oplus.phonemanager",
                "com.huawei.systemmanager",
                "com.oneplus.security",
                "com.transsion.phonemaster"
            };
            for (int i = 0; i < pkgs.length; i++) {
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkgs[i]);
                    if (launch == null) continue;
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    return "open";
                } catch (Exception ignored) {}
            }
            try {
                Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settings);
                return "settings";
            } catch (Exception ignored) {}
            return "none";
        }

        /** Opens the system Settings app, or Developer options when `which` is "developer". */
        @JavascriptInterface
        public void openDeviceSettings(String which) {
            final String action = "developer".equals(which)
                ? Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                : Settings.ACTION_SETTINGS;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent intent = new Intent(action);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception ex) {
                        try {
                            Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(fallback);
                        } catch (Exception ignored) {}
                    }
                }
            });
        }

        /** This phone's own link, DNS, and listening sockets. No remote probe and no packet capture. */
        @JavascriptInterface
        public String networkPosture() {
            JSONObject o = new JSONObject();
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                Network net = cm == null ? null : cm.getActiveNetwork();
                NetworkCapabilities caps = (cm == null || net == null) ? null : cm.getNetworkCapabilities(net);
                if (caps != null) {
                    o.put("vpn", caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
                    o.put("wifi", caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
                    o.put("cell", caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
                    int down = caps.getLinkDownstreamBandwidthKbps();
                    int up = caps.getLinkUpstreamBandwidthKbps();
                    if (down > 0) o.put("downKbps", down);
                    if (up > 0) o.put("upKbps", up);
                }
                LinkProperties lp = (cm == null || net == null) ? null : cm.getLinkProperties(net);
                if (lp != null) {
                    String iface = lp.getInterfaceName();
                    if (iface != null && iface.length() > 0) o.put("iface", iface);
                    JSONArray dns = new JSONArray();
                    for (InetAddress address : lp.getDnsServers()) {
                        String host = address.getHostAddress();
                        if (host != null && host.length() > 0) dns.put(host);
                    }
                    o.put("dns", dns);
                    JSONArray addrs = new JSONArray();
                    for (LinkAddress link : lp.getLinkAddresses()) addrs.put(String.valueOf(link));
                    o.put("addrs", addrs);
                }
                o.put("listeners", localListeners());
            } catch (Exception ex) {
                try { o.put("error", "posture"); } catch (Exception ignored) {}
            }
            return o.toString();
        }

        @JavascriptInterface
        public String loadPlatforms() {
            return readPlatforms();
        }

        @JavascriptInterface
        public String savePlatforms(String json) {
            return writePlatforms(json);
        }

        @JavascriptInterface
        public String powerMode(String mode) {
            String which = mode == null ? "regular" : mode;
            try {
                android.content.ContentResolver cr = getContentResolver();
                if ("saver".equals(which)) {
                    android.provider.Settings.Global.putInt(cr, "low_power", 1);
                    return "set";
                } else if ("fast".equals(which)) {
                    android.provider.Settings.Global.putInt(cr, "low_power", 0);
                    android.provider.Settings.System.putInt(cr, "high_performance_mode", 1);
                    return "set";
                } else {
                    android.provider.Settings.Global.putInt(cr, "low_power", 0);
                    try { android.provider.Settings.System.putInt(cr, "high_performance_mode", 0); } catch (Exception ignored) {}
                    return "set";
                }
            } catch (Exception ex) {
                openPower(which);
                return "open";
            }
        }

        private void openPower(final String which) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if ("fast".equals(which)) {
                        if (openPart("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.setting.PowerModeSettingsActivity")) return;
                        if (openPart("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")) return;
                        if (openPart("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")) return;
                    }
                    try {
                        Intent i = new Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception ignored) {}
                }
            });
        }

        private boolean openPart(String pkg, String cls) {
            try {
                Intent i = new Intent();
                i.setComponent(new android.content.ComponentName(pkg, cls));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }

        @JavascriptInterface
        public void open(String url) {
            if (url == null) return;
            final String target = url.trim();
            if (!knownLink(target)) return;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void clip(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || text == null) return;
            cm.setPrimaryClip(ClipData.newPlainText("meridian", text));
        }

        /** Opens the system share sheet with a plain-text payload. Used for the hardware report. */
        @JavascriptInterface
        public void share(String title, String body) {
            if (title == null || body == null) return;
            final String subject = title.trim();
            final String text = body;
            if (subject.length() == 0 || text.length() == 0) return;
            if (subject.length() > 200 || text.length() > 100000) return;
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_SUBJECT, subject);
                        i.putExtra(Intent.EXTRA_TEXT, text);
                        startActivity(Intent.createChooser(i, subject));
                    } catch (Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public String run(String cmd) {
            if (!termuxInstalled()) return "Termux is not installed\nexit 1";
            String refused = refuseCmd(cmd);
            if (refused != null) return refused + "\nexit 1";
            if (jobLive) return "A command is already running\nexit 1";
            jobLive = true;
            try {
                launch(cmd);
                return waitResult(25000);
            } finally {
                jobLive = false;
            }
        }

        @JavascriptInterface
        public String startJob(String cmd) {
            String refused = refuseCmd(cmd);
            if (refused != null) return refused;
            if (!termuxInstalled()) {
                synchronized (GATE) {
                    pendingOut = "Termux is not installed";
                    pendingCode = 1;
                    ready = true;
                }
                return "missing";
            }
            if (jobLive) return "busy";
            jobLive = true;
            launch(cmd);
            return "ok";
        }

        @JavascriptInterface
        public String cancelJob() {
            boolean sent = MainActivity.this.cancelJob();
            jobLive = false;
            return sent ? "ok" : "none";
        }

        @JavascriptInterface
        public String openInTermux(String cmd) {
            if (!termuxInstalled()) return "missing";
            if (refuseCmd(cmd) != null) return "refused";
            return MainActivity.this.openInTermux(cmd) ? "ok" : "asleep";
        }

        @JavascriptInterface
        public void openTermux() {
            wakeTermux();
        }

        @JavascriptInterface
        public String version() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception ex) {
                return "";
            }
        }

        /** Shows the soft keyboard for the WebView. The page calls this when a text field is touched. */
        @JavascriptInterface
        public void showKeyboard() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (web == null) return;
                    web.requestFocus();
                    final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm == null) return;
                    if (!imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)) {
                        web.post(new Runnable() {
                            public void run() {
                                imm.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT);
                            }
                        });
                    }
                }
            });
        }

        @JavascriptInterface
        public void hideKeyboard() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (web == null) return;
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(web.getWindowToken(), 0);
                }
            });
        }

        /** Debug builds only. A release build returns no. paid, full, and unlocked are not accepted. */
        @JavascriptInterface
        public String devUnlock(String pass) {
            if (!debuggable()) return "no";
            String p = pass == null ? "" : pass.trim().replace("\r", "").replace("\n", "").replace(" ", "");
            if (!"MERIDIAN-DEV".equals(p)) return "no";
            markTier("premium");
            return "premium";
        }

        @JavascriptInterface
        public String workspace() {
            try {
                JSONObject o = new JSONObject();
                o.put("personal", claimsSku(readSmallText(slotFile("personal"), 12000)));
                o.put("enterprise", claimsSku(readSmallText(slotFile("enterprise"), 12000)));
                String active = readSmallText(new File(getFilesDir(), "slot-active"), 32);
                if (!knownSlot(active)) active = "personal";
                o.put("active", active);
                return o.toString();
            } catch (Exception ex) {
                return "{\"personal\":\"\",\"enterprise\":\"\",\"active\":\"personal\"}";
            }
        }

        @JavascriptInterface
        public String saveWorkspace(String slot, String key) {
            if (!knownSlot(slot)) return "{\"ok\":false,\"error\":\"Unknown workspace.\"}";
            String result = activate(key);
            try {
                JSONObject o = new JSONObject(result);
                if (!o.optBoolean("ok")) return result;
                String normalized = key == null ? "" : key.trim().replace("\r", "").replace("\n", "").replace(" ", "");
                writeText(slotFile(slot), normalized);
                writeText(new File(getFilesDir(), "slot-active"), slot);
                markTier(o.optString("sku", "pro"));
                return result;
            } catch (Exception ex) {
                return "{\"ok\":false,\"error\":\"The workspace could not be saved.\"}";
            }
        }

        @JavascriptInterface
        public String useWorkspace(String slot) {
            if (!knownSlot(slot)) return "{\"ok\":false,\"error\":\"Unknown workspace.\"}";
            String key = readSmallText(slotFile(slot), 12000);
            if (key.length() == 0) return "{\"ok\":false,\"error\":\"That workspace has no key yet.\"}";
            String result = activate(key);
            try {
                JSONObject o = new JSONObject(result);
                if (!o.optBoolean("ok")) return result;
                writeText(new File(getFilesDir(), "slot-active"), slot);
                markTier(o.optString("sku", "pro"));
                return result;
            } catch (Exception ex) {
                return "{\"ok\":false,\"error\":\"The workspace could not be opened.\"}";
            }
        }

        @JavascriptInterface
        public void haptic() {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (web != null) web.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
            });
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            MainActivity.this.keepAwake(on);
        }

        /**
         * Activates a licence key of the form MERIDIAN-<payload>.<signature> where payload is
         * base64url JSON {"sku":"pro"|"premium","iat":...,"ref":"..."} and signature is a P-256
         * ECDSA (SHA-256) signature over the raw payload bytes. Verified offline against LICENSE_PUBKEY.
         * The words paid, full, and unlocked are not keys and do not unlock.
         */
        @JavascriptInterface
        public String activate(String key) {
            if (LICENSE_PUBKEY.length() == 0) return "{\"ok\":false,\"error\":\"This build has no licence public key. See tools/license.\"}";
            try {
                String k = key == null ? "" : key.trim();
                if (k.length() > 0 && k.charAt(0) == '\uFEFF') k = k.substring(1).trim();
                k = k.replace("\r", "").replace("\n", "").replace(" ", "");
                if (k.startsWith("MERIDIAN-")) k = k.substring(9);
                int dot = k.indexOf('.');
                if (dot <= 0) return "{\"ok\":false,\"error\":\"That is not a Meridian key. It should start with MERIDIAN-\"}";
                byte[] payload = android.util.Base64.decode(k.substring(0, dot), android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
                byte[] sig = android.util.Base64.decode(k.substring(dot + 1), android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
                byte[] der = android.util.Base64.decode(LICENSE_PUBKEY, android.util.Base64.DEFAULT);
                java.security.PublicKey pub = java.security.KeyFactory.getInstance("EC").generatePublic(new java.security.spec.X509EncodedKeySpec(der));
                java.security.Signature verifier = java.security.Signature.getInstance("SHA256withECDSA");
                verifier.initVerify(pub);
                verifier.update(payload);
                if (!verifier.verify(sig)) return "{\"ok\":false,\"error\":\"The key did not verify.\"}";
                JSONObject claims = new JSONObject(new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                String sku = claims.optString("sku", "");
                if (!"pro".equals(sku) && !"premium".equals(sku)) return "{\"ok\":false,\"error\":\"That key is for a different product.\"}";
                markPaid(sku);
                return "{\"ok\":true,\"sku\":" + JSONObject.quote(sku) + "}";
            } catch (Exception ex) {
                return "{\"ok\":false,\"error\":\"The key could not be read.\"}";
            }
        }

        @JavascriptInterface
        public String payUrl() {
            return PAY_URL;
        }

        @JavascriptInterface
        public String jobStatus() {
            try {
                JSONObject o = new JSONObject();
                boolean done;
                String out;
                int code;
                synchronized (GATE) {
                    done = ready;
                    out = pendingOut;
                    code = pendingCode;
                }
                if (done) jobLive = false;
                o.put("done", done);
                out = clipOut(out);
                if (done) o.put("out", out + "\nexit " + code);
                else o.put("out", out);
                o.put("code", code);
                return o.toString();
            } catch (Exception ex) {
                return "{\"done\":true,\"out\":\"status error\",\"code\":1}";
            }
        }

        @JavascriptInterface
        public String unlocked() {
            File licence = new File(getFilesDir(), "full.key");
            if (OPEN) {
                if (!licence.isFile() || licence.length() == 0) markPaid("pro");
                return "yes";
            }
            if (licence.isFile() && licence.length() > 0) return "yes";
            if (debuggable()) {
                String dev = readSmallText(new File(getFilesDir(), "dev.unlock"), 32);
                if ("pro".equals(dev) || "premium".equals(dev)) return "yes";
            }
            return "no";
        }

        /** free, pro, or premium. An existing full.key from this install stays pro unless it says premium. */
        @JavascriptInterface
        public String tier() {
            if (debuggable()) {
                String dev = readSmallText(new File(getFilesDir(), "dev.unlock"), 32);
                if ("premium".equals(dev)) return "premium";
                if ("pro".equals(dev)) return "pro";
            }
            File licence = new File(getFilesDir(), "full.key");
            if (!licence.isFile() || licence.length() == 0) return "free";
            if ("premium".equals(readSmallText(licence, 32))) return "premium";
            return "pro";
        }

        @JavascriptInterface
        public void pay() {
            runOnUiThread(new Runnable() { public void run() { showPay(); } });
        }

        @JavascriptInterface
        public String entries(String path) {
            String p = shellPath(path);
            if (p == null) return BAD_PATH;
            return run("ls -1p --color=never " + p);
        }

        /** Reads at most 200 KB and refuses binary files, so a tap on a .gguf does not stall the bridge or dump garbage into the editor. */
        @JavascriptInterface
        public String readFile(String path) {
            String p = shellPath(path);
            if (p == null) return BAD_PATH;
            return run("f=" + p + "; if [ ! -f \"$f\" ]; then echo 'Not a file'; exit 1; fi; "
                + "if [ \"$(head -c 4000 \"$f\" | tr -d '\\000' | wc -c)\" -ne \"$(head -c 4000 \"$f\" | wc -c)\" ]; then echo \"[binary file, $(wc -c < \"$f\") bytes. Meridian only edits text.]\"; exit 2; fi; "
                + "s=$(wc -c < \"$f\"); if [ \"$s\" -gt 200000 ]; then head -c 200000 \"$f\"; printf '\\n[Meridian showed the first 200 KB of %s bytes. Saving is disabled for files this large.]\\n' \"$s\"; exit 3; fi; cat \"$f\"");
        }

        @JavascriptInterface
        public String writeFile(String path, String body) {
            String p = shellPath(path);
            if (p == null) return BAD_PATH;
            String b64 = android.util.Base64.encodeToString((body == null ? "" : body).getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
            return run("printf %s " + sh(b64) + " | base64 -d > " + p);
        }

        @JavascriptInterface
        public String removePath(String path) {
            String p = shellPath(path);
            if (p == null || "\"$HOME\"".equals(p)) return BAD_PATH;
            return run("rm -rf -- " + p);
        }

        @JavascriptInterface
        public String renamePath(String from, String to) {
            String a = shellPath(from);
            String b = shellPath(to);
            if (a == null || b == null || "\"$HOME\"".equals(a)) return BAD_PATH;
            return run("mv -n -- " + a + " " + b);
        }

        @JavascriptInterface
        public String makeDir(String path) {
            String p = shellPath(path);
            if (p == null) return BAD_PATH;
            return run("mkdir -p -- " + p);
        }

        @JavascriptInterface
        public void stopSpeed() {
            if ("run".equals(speedJson)) speedJson = "{\"ok\":false,\"error\":\"The test timed out.\"}";
        }

        @JavascriptInterface
        public String choosePlan(String sku) {
            try {
                if ("free".equals(sku)) {
                    new File(getFilesDir(), "full.key").delete();
                    new File(getFilesDir(), "dev.unlock").delete();
                    return "{\"ok\":true,\"sku\":\"free\"}";
                }
                if ("pro".equals(sku)) {
                    String now = tier();
                    if (!"pro".equals(now) && !"premium".equals(now)) {
                        return "{\"ok\":false,\"error\":\"Pro needs a licence key.\"}";
                    }
                    markTier("pro");
                    return "{\"ok\":true,\"sku\":\"pro\"}";
                }
                if ("premium".equals(sku)) {
                    String key = readSmallText(slotFile("enterprise"), 12000);
                    if (key.length() == 0) return "{\"ok\":false,\"error\":\"Paste a Premium key into Premium Enterprise.\"}";
                    return useWorkspace("enterprise");
                }
                return "{\"ok\":false,\"error\":\"Unknown plan.\"}";
            } catch (Exception ex) {
                return "{\"ok\":false,\"error\":\"The plan could not be changed.\"}";
            }
        }

        @JavascriptInterface
        public String startSpeed() {
            if ("run".equals(speedJson)) return "run";
            speedJson = "run";
            new Thread(new Runnable() {
                public void run() {
                    try {
                        pull("https://speed.cloudflare.com/__down?bytes=1000", 4000);
                        long best = Long.MAX_VALUE;
                        for (int i = 0; i < 3; i++) {
                            long[] row = pull("https://speed.cloudflare.com/__down?bytes=1000", 4000);
                            if (row[2] < best) best = row[2];
                        }
                        long[] down = pull("https://speed.cloudflare.com/__down?bytes=2000000", 2500000);
                        byte[] body = new byte[256 * 1024];
                        new java.util.Random().nextBytes(body);
                        long upMs = postUp(body);
                        JSONObject o = new JSONObject();
                        o.put("ok", true);
                        o.put("ping", best == Long.MAX_VALUE ? 0 : best);
                        o.put("downBytes", down[0]);
                        o.put("downMs", down[1]);
                        o.put("upBytes", body.length);
                        o.put("upMs", upMs);
                        speedJson = o.toString();
                    } catch (Throwable ex) {
                        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                        speedJson = "{\"ok\":false,\"error\":" + JSONObject.quote(msg) + "}";
                    }
                }
            }, "meridian-speed").start();
            return "run";
        }

        @JavascriptInterface
        public String speedStatus() {
            String now = speedJson;
            return now == null || now.length() == 0 ? "run" : now;
        }

        /**
         * Carrier / transport next to the speed test. ACCESS_NETWORK_STATE is a normal
         * permission. READ_PHONE_STATE is not requested — operator name is best-effort.
         */
        @JavascriptInterface
        public String networkName() {
            try {
                JSONObject o = new JSONObject();
                String kind = "";
                String name = "";
                try {
                    ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        Network net = cm.getActiveNetwork();
                        NetworkCapabilities caps = net == null ? null : cm.getNetworkCapabilities(net);
                        if (caps != null) {
                            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) kind = "wifi";
                            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) kind = "cell";
                            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) kind = "ethernet";
                            else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) kind = "vpn";
                        }
                    }
                } catch (Exception ignored) {}
                try {
                    TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
                    if (tm != null) {
                        String op = tm.getNetworkOperatorName();
                        if (op == null || op.trim().length() == 0) op = tm.getSimOperatorName();
                        if (op != null) name = op.trim();
                    }
                } catch (SecurityException ignored) {
                } catch (Exception ignored) {}
                if ("wifi".equals(kind)) {
                    o.put("kind", "wifi");
                    o.put("name", "Wi-Fi");
                } else if ("cell".equals(kind)) {
                    o.put("kind", "cell");
                    o.put("name", name.length() > 0 ? name : "Mobile");
                } else if ("ethernet".equals(kind)) {
                    o.put("kind", "ethernet");
                    o.put("name", "Ethernet");
                } else if ("vpn".equals(kind) && name.length() > 0) {
                    o.put("kind", "vpn");
                    o.put("name", name);
                } else if (name.length() > 0) {
                    o.put("kind", kind.length() > 0 ? kind : "cell");
                    o.put("name", name);
                } else {
                    o.put("kind", kind);
                    o.put("name", "—");
                }
                return o.toString();
            } catch (Exception ex) {
                return "{\"kind\":\"\",\"name\":\"—\"}";
            }
        }

        @JavascriptInterface
        public String netPing() {
            try {
                pull("https://speed.cloudflare.com/__down?bytes=1000", 8000);
                long best = Long.MAX_VALUE;
                for (int i = 0; i < 3; i++) {
                    long[] row = pull("https://speed.cloudflare.com/__down?bytes=1000", 8000);
                    if (row[2] < best) best = row[2];
                }
                if (best == Long.MAX_VALUE) return "{\"ok\":false,\"error\":\"no sample\"}";
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("ms", best);
                return o.toString();
            } catch (Exception ex) {
                return netErr(ex);
            }
        }

        @JavascriptInterface
        public String netDown() {
            try {
                long[] row = pull("https://speed.cloudflare.com/__down?bytes=8000000", 9000000);
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("bytes", row[0]);
                o.put("ms", row[1]);
                return o.toString();
            } catch (Exception ex) {
                return netErr(ex);
            }
        }

        @JavascriptInterface
        public String netUp() {
            try {
                byte[] warm = new byte[32768];
                new java.util.Random().nextBytes(warm);
                postUp(warm);
                byte[] body = new byte[2 * 1024 * 1024];
                new java.util.Random().nextBytes(body);
                long ms = postUp(body);
                if (ms <= 0) return "{\"ok\":false,\"error\":\"no bytes\"}";
                JSONObject o = new JSONObject();
                o.put("ok", true);
                o.put("bytes", body.length);
                o.put("ms", ms);
                return o.toString();
            } catch (Exception ex) {
                return netErr(ex);
            }
        }

        @JavascriptInterface
        public String dropCache() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                if (am == null) return "{\"error\":\"no activity manager\"}";
                long[] before = meminfo();
                int asked = killOthers(am);
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                long[] after = meminfo();
                JSONObject o = new JSONObject();
                o.put("ram", before[0]);
                o.put("ramPlus", ramPlusBytes());
                o.put("virtual", before[2]);
                o.put("freeBefore", before[6]);
                o.put("freeAfter", after[6]);
                o.put("availBefore", before[1]);
                o.put("availAfter", after[1]);
                o.put("asked", asked);
                return o.toString();
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "blocked" : ex.getMessage();
                return "{\"error\":" + JSONObject.quote(msg) + "}";
            }
        }

        /**
         * Non-blocking variant of askNet. The synchronous call froze the WebView for up to 50 s
         * while the model answered; this one returns at once and later calls
         * window.onAskDone(ticket, resultJson) on the page.
         */
        @JavascriptInterface
        public void askNetStart(final String ticket, final String base, final String key, final String model, final String goal) {
            new Thread(new Runnable() {
                public void run() {
                    final String result = askNet(base, key, model, goal);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (web == null) return;
                            web.evaluateJavascript("window.onAskDone && window.onAskDone(" + JSONObject.quote(ticket == null ? "" : ticket) + "," + JSONObject.quote(result) + ")", null);
                        }
                    });
                }
            }, "meridian-ask").start();
        }

        /** Days left in the 7-day trial, 0 when over. The first-run stamp lives in filesDir so clearing WebView storage does not restart it. */
        @JavascriptInterface
        public int trialLeft() {
            try {
                File stamp = new File(getFilesDir(), "first.run");
                long first;
                if (stamp.isFile()) {
                    first = stamp.lastModified();
                } else {
                    new java.io.FileOutputStream(stamp).close();
                    first = System.currentTimeMillis();
                    stamp.setLastModified(first);
                }
                long elapsedDays = (System.currentTimeMillis() - first) / 86400000L;
                long left = TRIAL_DAYS - elapsedDays;
                return left < 0 ? 0 : (int) left;
            } catch (Exception ex) {
                return 0;
            }
        }

        /** Public GitHub .apk download total. Calls back with a number, or "" when the request fails. */
        @JavascriptInterface
        public void loadDownloads() {
            new Thread(new Runnable() {
                public void run() {
                    final String n = apkDownloadTotal();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (web == null) return;
                            String arg = n == null ? "\"\"" : JSONObject.quote(n);
                            web.evaluateJavascript("window.onDownloads && window.onDownloads(" + arg + ")", null);
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public String askNet(String base, String key, String model, String goal) {
            if (key == null || key.trim().length() == 0) return "{\"error\":\"No API key saved.\"}";
            if (base == null || !base.startsWith("https://")) return "{\"error\":\"The address must start with https://\"}";
            String task = goal == null ? "" : goal.trim();
            if (task.length() > 2000) task = task.substring(0, 2000);
            String root = apiRoot(base);
            String blocked = blockedHost(base);
            if (blocked != null) return "{\"error\":" + JSONObject.quote(blocked) + "}";
            String kind = kindOf(root);
            String which = model == null ? "" : model.trim();
            if (which.length() == 0) {
                if ("gemini".equals(kind)) which = "gemini-2.0-flash";
                else if ("anthropic".equals(kind)) which = "claude-3-5-haiku-latest";
                else if (root.contains("api.x.ai")) which = "grok-3";
                else if (root.contains("api.openai.com")) which = "gpt-4o-mini";
                else if (root.contains("api.groq.com")) which = "openai/gpt-oss-20b";
                else return "{\"error\":\"Write the model name on the Key page.\"}";
            }
            boolean swapped = false;
            if (root.contains("api.groq.com") && ("llama-3.3-70b-versatile".equals(which) || "llama-3.1-8b-instant".equals(which))) {
                which = "openai/gpt-oss-20b";
                swapped = true;
            }
            String prompt = isQuestion(task)
                ? "Answer the user's question in plain sentences. Start with ANSWER: and then the answer itself. Do not add a shell command. Do not use curl. Do not tell the user to look it up. Task: " + task
                : "The user is on an Android phone, in Termux. If they want something installed or built, reply with shell commands only, one per line, no markdown. If they ask a question, start with ANSWER: and a short reply. Do not invent live numbers such as today's weather. For weather only, after the answer add one line: curl -fsS \"https://wttr.in/PLACE?format=3\". Task: " + task;
            HttpURLConnection conn = null;
            try {
                URL url;
                JSONObject body = new JSONObject();
                if ("gemini".equals(kind)) {
                    // The key travels in the x-goog-api-key header only; a ?key= query string ends up in proxy and server logs.
                    url = new URL(root.contains(":generateContent") ? root : root + "/models/" + which + ":generateContent");
                    JSONArray parts = new JSONArray();
                    JSONObject part = new JSONObject();
                    part.put("text", prompt);
                    parts.put(part);
                    JSONObject content = new JSONObject();
                    content.put("parts", parts);
                    JSONArray contents = new JSONArray();
                    contents.put(content);
                    body.put("contents", contents);
                } else if ("anthropic".equals(kind)) {
                    url = new URL(root + "/messages");
                    body.put("model", which);
                    body.put("max_tokens", 800);
                    JSONArray messages = new JSONArray();
                    JSONObject msg = new JSONObject();
                    msg.put("role", "user");
                    msg.put("content", prompt);
                    messages.put(msg);
                    body.put("messages", messages);
                } else {
                    url = new URL(root + "/chat/completions");
                    body.put("model", which);
                    JSONArray messages = new JSONArray();
                    JSONObject msg = new JSONObject();
                    msg.put("role", "user");
                    msg.put("content", prompt);
                    messages.put(msg);
                    body.put("messages", messages);
                }
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(40000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                if ("gemini".equals(kind)) conn.setRequestProperty("x-goog-api-key", key.trim());
                else if ("anthropic".equals(kind)) {
                    conn.setRequestProperty("x-api-key", key.trim());
                    conn.setRequestProperty("anthropic-version", "2023-06-01");
                } else conn.setRequestProperty("Authorization", "Bearer " + key.trim());
                byte[] raw = body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(raw.length);
                OutputStream out = conn.getOutputStream();
                out.write(raw);
                out.close();
                int code = conn.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String text = readCap(stream, 16000);
                String shown = url.toString().replaceAll("\\?.*", "");
                if (code < 200 || code >= 300) {
                    return "{\"error\":" + JSONObject.quote(apiError(code, text, shown)) + "}";
                }
                String packed = packReply(readContent(new JSONObject(text)), task);
                if (swapped && packed.indexOf("\"error\"") < 0) {
                    JSONObject done = new JSONObject(packed);
                    String note = "The Llama models are not on this Groq key. Asked openai/gpt-oss-20b instead.";
                    String answer = done.optString("answer", "");
                    done.put("answer", answer.length() == 0 ? note : note + " " + answer);
                    return done.toString();
                }
                return packed;
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                return "{\"error\":" + JSONObject.quote(msg) + "}";
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    private String apiRoot(String base) {
        String root = base == null ? "" : base.trim().replaceAll("/+$", "");
        root = root.replaceAll("(?i)/chat/completions$", "");
        root = root.replaceAll("(?i)/messages$", "");
        if (root.matches("(?i)https://api\\.x\\.ai")) return "https://api.x.ai/v1";
        if (root.matches("(?i)https://api\\.openai\\.com")) return "https://api.openai.com/v1";
        if (root.matches("(?i)https://api\\.groq\\.com")) return "https://api.groq.com/openai/v1";
        if (root.matches("(?i)https://openrouter\\.ai")) return "https://openrouter.ai/api/v1";
        if (root.matches("(?i)https://api\\.anthropic\\.com")) return "https://api.anthropic.com/v1";
        if (root.matches("(?i)https://generativelanguage\\.googleapis\\.com")) return "https://generativelanguage.googleapis.com/v1beta";
        return root;
    }

    private String kindOf(String root) {
        if (root.contains("generativelanguage.googleapis.com")) return "gemini";
        if (root.contains("api.anthropic.com")) return "anthropic";
        return "openai";
    }

    private String blockedHost(String base) {
        try {
            String host = new URL(base).getHost().toLowerCase();
            if (host.equals("google.com") || host.equals("www.google.com") || host.equals("gemini.google.com"))
                return "google.com is a website, not an API. Open Key and press Gemini, then paste your Google key.";
            if (host.equals("x.ai") || host.equals("www.x.ai") || host.equals("grok.com") || host.equals("console.x.ai"))
                return "That is the website. Open Key and press xAI, then paste your xAI key.";
            if (host.equals("openai.com") || host.equals("www.openai.com") || host.equals("platform.openai.com") || host.equals("chatgpt.com"))
                return "That is the website. Open Key and press OpenAI, then paste your OpenAI key.";
            if (host.equals("claude.ai") || host.equals("www.anthropic.com") || host.equals("console.anthropic.com"))
                return "That is the website. Open Key and press Anthropic, then paste your Anthropic key.";
        } catch (Exception ignored) {}
        return null;
    }

    private String readContent(JSONObject parsed) throws Exception {
        if (parsed.has("candidates")) {
            return parsed.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
        }
        if (parsed.has("content") && parsed.get("content") instanceof JSONArray) {
            JSONArray parts = parsed.getJSONArray("content");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.has("text")) sb.append(part.getString("text"));
            }
            return sb.toString();
        }
        Object content = parsed.getJSONArray("choices").getJSONObject(0).getJSONObject("message").get("content");
        return content == null ? "" : String.valueOf(content);
    }

    private String apiError(int code, String text, String url) {
        String body = text == null ? "" : text;
        String low = body.toLowerCase();
        if (low.contains("<!doctype") || low.contains("<html")) {
            return "HTTP " + code + ". " + url + " returned a website, not an answer. Open Key and press the company that sold you the key. google.com, x.ai and openai.com are websites.";
        }
        try {
            JSONObject err = new JSONObject(body);
            if (err.has("error")) {
                Object e = err.get("error");
                if (e instanceof JSONObject && ((JSONObject) e).has("message")) return "HTTP " + code + " " + ((JSONObject) e).getString("message");
                return "HTTP " + code + " " + String.valueOf(e);
            }
        } catch (Exception ignored) {}
        String bit = body.length() > 180 ? body.substring(0, 180) : body;
        return "HTTP " + code + (bit.length() == 0 ? "" : " " + bit);
    }

    private boolean isQuestion(String task) {
        String t = task == null ? "" : task.trim().toLowerCase();
        if (t.contains("install") || t.contains("pkg ") || t.contains("termux") || t.contains("command")) return false;
        return t.startsWith("what") || t.startsWith("who") || t.startsWith("why") || t.startsWith("when") || t.startsWith("where") || t.startsWith("explain") || t.startsWith("define") || t.startsWith("tell me") || t.contains("?");
    }

    private String packReply(String content, String task) {
        String raw = content == null ? "" : content.trim();
        JSONArray steps = new JSONArray();
        String answer = "";
        boolean marked = raw.startsWith("ANSWER:");
        String body = marked ? raw.substring(7).trim() : raw;
        String[] lines = body.split("\\n");
        StringBuilder prose = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0 || line.startsWith("```")) continue;
            line = line.replaceFirst("^\\d+[\\).\\s]+", "").trim();
            if (line.startsWith("$ ")) line = line.substring(2).trim();
            if (line.startsWith("ANSWER:")) line = line.substring(7).trim();
            if (line.length() == 0) continue;
            if (isCommand(line)) steps.put(line);
            else prose.append(prose.length() == 0 ? "" : " ").append(line);
        }
        answer = prose.toString().trim();
        if (isQuestion(task)) {
            JSONArray kept = new JSONArray();
            for (int i = 0; i < steps.length(); i++) {
                String line = steps.optString(i, "");
                if (line.toLowerCase().startsWith("curl") || line.toLowerCase().startsWith("wget")) continue;
                kept.put(line);
            }
            steps = kept;
        }
        if (answer.length() == 0 && steps.length() == 0) return "{\"error\":\"The model did not answer the question.\"}";
        JSONObject o = new JSONObject();
        try {
            if (answer.length() > 0) o.put("answer", answer);
            o.put("steps", steps);
        } catch (Exception ignored) {}
        return o.toString();
    }

    private boolean isCommand(String line) {
        // The old list dropped cd/mkdir/echo/export/chmod lines as prose, so multi-step scripts came back mangled.
        return line.matches("(?i)^(?:(?:pkg|apt|apt-get|dpkg|curl|wget|getprop|termux-[a-z0-9-]+|python3?|pip3?|npm|npx|node|git|clang|gcc|cmake|make|ninja|ls|cat|free|df|du|uname|ping|ssh|ssh-keygen|sshd|vim|nvim|nano|cd|mkdir|echo|printf|export|chmod|chown|cp|mv|rm|tar|unzip|zip|sed|awk|grep|rg|find|which|source|bash|sh|proot-distro|cargo|rustc|go|java|javac|ruby|php|lua|perl|ffmpeg|sqlite3|rsync|tmux|nproc|env|test|llama-cli|llama-server)\\b|clang\\+\\+|g\\+\\+|\\[ |\\./).*");
    }

    /** Writes pro or premium into files/full.key. Never writes the four letters paid. Does not downgrade premium. */
    private void markPaid(String sku) {
        try {
            java.io.File out = new java.io.File(getFilesDir(), "full.key");
            String current = readSmallText(out, 32);
            String next = ("premium".equals(sku) || "premium".equals(current)) ? "premium" : "pro";
            writeText(out, next);
            if (debuggable()) writeText(new File(getFilesDir(), "dev.unlock"), next);
        } catch (Exception ignored) {}
    }

    /** Writes the exact sku. Used when a verified workspace is selected, including a step down from Premium to Pro. */
    private void markTier(String sku) {
        try {
            String next = "premium".equals(sku) ? "premium" : "pro";
            writeText(new File(getFilesDir(), "full.key"), next);
            if (debuggable()) writeText(new File(getFilesDir(), "dev.unlock"), next);
        } catch (Exception ignored) {}
    }

    private boolean debuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private boolean knownSlot(String slot) {
        return "personal".equals(slot) || "enterprise".equals(slot);
    }

    private File slotFile(String slot) {
        return new File(getFilesDir(), "slot-" + slot + ".key");
    }

    private void writeText(File file, String text) throws Exception {
        FileOutputStream stream = new FileOutputStream(file);
        try {
            stream.write((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.flush();
        } finally {
            stream.close();
        }
    }

    /** Display-only sku from a stored key. Unlocking still goes through activate(). */
    private String claimsSku(String key) {
        try {
            String k = key == null ? "" : key.trim();
            if (k.startsWith("MERIDIAN-")) k = k.substring(9);
            int dot = k.indexOf('.');
            if (dot <= 0) return "";
            byte[] payload = android.util.Base64.decode(k.substring(0, dot), android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
            String sku = new JSONObject(new String(payload, java.nio.charset.StandardCharsets.UTF_8)).optString("sku", "");
            if ("pro".equals(sku) || "premium".equals(sku)) return sku;
        } catch (Exception ignored) {}
        return "";
    }

    private String readSmallText(File file, int max) {
        if (file == null || !file.isFile()) return "";
        try {
            int n = (int) Math.min(file.length(), max);
            if (n <= 0) return "";
            byte[] buf = new byte[n];
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            int got = in.read(buf);
            in.close();
            if (got <= 0) return "";
            String text = new String(buf, 0, got, java.nio.charset.StandardCharsets.UTF_8).trim();
            if (text.startsWith("\uFEFF")) text = text.substring(1).trim();
            return text;
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * Keeps files/full.key when this install already unlocked. Does not copy a Downloads
     * file whose body is the four letters paid, full, or unlocked. A MERIDIAN- file in
     * Downloads is adopted only after the same signature check as activate().
     */
    private void adoptLegacyUnlock() {
        File local = new File(getFilesDir(), "full.key");
        if (local.isFile() && local.length() > 0) return;
        File[] guesses = new File[] {
            new File("/storage/emulated/0/Download/full.key"),
            new File("/storage/emulated/0/Download/full.key.txt"),
            new File("/sdcard/Download/full.key"),
            new File("/sdcard/Download/full.key.txt")
        };
        for (File guess : guesses) {
            if (guess == null || !guess.isFile()) continue;
            String text = readSmallText(guess, 8192);
            if (text.equals("paid") || text.equalsIgnoreCase("full") || text.equalsIgnoreCase("unlocked")) continue;
            if (!text.startsWith("MERIDIAN-")) continue;
            new Deck().activate(text);
            if (local.isFile() && local.length() > 0) return;
        }
    }

    /**
     * The original embedded the checkout in a WebView and unlocked when the page text contained
     * "thank you". That is both spoofable (any page saying thank you) and fragile (a wording change
     * on the merchant side breaks every sale). Checkout now opens in the system browser and the
     * merchant emails a signed licence key that Deck.activate() verifies offline.
     */
    private void showPay() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(PAY_URL));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    private boolean knownLink(String target) {
        if ("mailto:Pozun.alen96@gmail.com".equals(target)) return true;
        if (!target.startsWith("https://")) return false;
        String host;
        try {
            host = android.net.Uri.parse(target).getHost();
        } catch (Exception ex) {
            return false;
        }
        if (host == null) return false;
        host = host.toLowerCase();
        String[] allowed = {
            "github.com", "wikipedia.org", "discord.com", "f-droid.org", "wiki.termux.com",
            "gitlab.com", "codeberg.org", "developer.android.com", "source.android.com",
            "kernel.org", "man7.org", "python.org", "nodejs.org", "npmjs.com", "pypi.org",
            "stackoverflow.com", "reddit.com", "news.ycombinator.com", "khronos.org",
            "gnu.org", "play.google.com", "termux.dev", "rust-lang.org", "crates.io",
            "go.dev", "mozilla.org", "archlinux.org", "debian.org", "cmake.org",
            "ffmpeg.org", "sqlite.org", "huggingface.co", "explainshell.com", "tldr.inbrowser.app",
            "console.groq.com", "groq.com", "aistudio.google.com", "ai.google.dev",
            "openrouter.ai", "platform.openai.com", "console.anthropic.com", "console.x.ai", "docs.x.ai"
        };
        for (int i = 0; i < allowed.length; i++) {
            String name = allowed[i];
            if (host.equals(name) || host.endsWith("." + name)) return true;
        }
        return false;
    }

    private int cpuPct() {
        int stat = statPct();
        int scaled = freqPct();
        if (stat > 0) return stat;
        return scaled >= 0 ? scaled : 0;
    }

    private int statPct() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/stat"));
            long idle = 0;
            long total = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("cpu")) break;
                if (line.startsWith("cpu ")) continue;
                String[] p = line.trim().split("\\s+");
                if (p.length < 5) continue;
                long rowIdle = parseLong(p[4]) + (p.length > 5 ? parseLong(p[5]) : 0);
                long row = 0;
                for (int i = 1; i < p.length && i <= 8; i++) row += parseLong(p[i]);
                idle += rowIdle;
                total += row;
            }
            int pct = 0;
            if (prevTotal >= 0 && total > prevTotal) {
                long dt = total - prevTotal;
                long di = idle - prevIdle;
                if (dt > 0) pct = (int) Math.round(100.0 * (dt - di) / dt);
                if (pct < 0) pct = 0;
                if (pct > 100) pct = 100;
            }
            prevTotal = total;
            prevIdle = idle;
            return pct;
        } catch (Exception ex) {
            return 0;
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        }
    }

    private int freqPct() {
        File dir = new File("/sys/devices/system/cpu");
        File[] kids = dir.listFiles();
        if (kids == null) return -1;
        int n = 0;
        int sum = 0;
        for (File cpu : kids) {
            if (!cpu.getName().matches("cpu\\d+")) continue;
            long cur = parseLong(readLine(new File(cpu, "cpufreq/scaling_cur_freq").getPath()));
            long max = parseLong(readLine(new File(cpu, "cpufreq/cpuinfo_max_freq").getPath()));
            long min = parseLong(readLine(new File(cpu, "cpufreq/cpuinfo_min_freq").getPath()));
            if (max <= min || cur <= 0) continue;
            int pct = (int) Math.round(100.0 * (cur - min) / (double) (max - min));
            if (pct < 0) pct = 0;
            if (pct > 100) pct = 100;
            sum += pct;
            n++;
        }
        return n == 0 ? -1 : (int) Math.round(sum / (double) n);
    }

    private String load1() {
        String line = readLine("/proc/loadavg");
        if (line.length() == 0) return "";
        int space = line.indexOf(' ');
        return space > 0 ? line.substring(0, space) : line;
    }

    private JSONArray cpuCores() {
        JSONArray arr = new JSONArray();
        coreCount = 0;
        try {
            for (int i = 0; i < 16; i++) {
                File file = new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
                if (!file.isFile()) {
                    if (i > 0 && !new File("/sys/devices/system/cpu/cpu" + i).isDirectory()) break;
                    continue;
                }
                coreCount++;
                long khz = parseLong(readLine(file.getPath()));
                JSONObject o = new JSONObject();
                o.put("id", i);
                o.put("mhz", khz <= 0 ? 0 : Math.round(khz / 1000.0));
                arr.put(o);
            }
        } catch (Exception ignored) {}
        return arr;
    }

    private JSONArray cpuClocks() {
        JSONArray arr = new JSONArray();
        try {
            String gov = readLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
            JSONObject o = new JSONObject();
            o.put("gov", gov);
            o.put("mhz", 0);
            arr.put(o);
        } catch (Exception ignored) {}
        return arr;
    }

    private String cpuName() {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Hardware") || line.startsWith("model name") || line.startsWith("Processor")) {
                    int cut = line.indexOf(':');
                    if (cut > 0) return line.substring(cut + 1).trim();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        }
        return "";
    }

    private long[] meminfo() {
        long[] out = new long[7];
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                long kb = memKb(line);
                if (line.startsWith("MemTotal:")) out[0] = kb;
                else if (line.startsWith("MemAvailable:")) out[1] = kb;
                else if (line.startsWith("SwapTotal:")) out[2] = kb;
                else if (line.startsWith("SwapFree:")) out[3] = kb;
                else if (line.startsWith("Cached:")) out[4] = kb;
                else if (line.startsWith("Buffers:")) out[5] = kb;
                else if (line.startsWith("MemFree:")) out[6] = kb;
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        }
        for (int i = 0; i < out.length; i++) out[i] *= 1024L;
        return out;
    }

    private long memKb(String line) {
        String[] p = line.trim().split("\\s+");
        return p.length > 1 ? parseLong(p[1]) : 0;
    }

    private long ramPlusBytes() {
        try {
            String[] keys = new String[] { "ram_expand_size", "zram_size" };
            for (int i = 0; i < keys.length; i++) {
                String v = Settings.Global.getString(getContentResolver(), keys[i]);
                if (v == null || v.trim().length() == 0) v = Settings.Secure.getString(getContentResolver(), keys[i]);
                if (v == null) continue;
                long n = parseLong(v.trim());
                if (n == 2 || n == 4 || n == 6 || n == 8 || n == 12) return n * 1073741824L;
                if (n == 2048 || n == 4096 || n == 6144 || n == 8192 || n == 12288) return n * 1048576L;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private long[] zram() {
        long[] out = new long[3];
        long biggest = 0;
        long orig = 0;
        long comp = 0;
        for (int i = 0; i < 8; i++) {
            String base = "/sys/block/zram" + i;
            long disk = parseLong(readLine(base + "/disksize"));
            if (disk <= 0) continue;
            String stat = readLine(base + "/mm_stat");
            long o = 0;
            long c = 0;
            if (stat.length() > 0) {
                String[] p = stat.trim().split("\\s+");
                if (p.length > 0) o = parseLong(p[0]);
                if (p.length > 1) c = parseLong(p[1]);
            }
            if (disk > biggest) {
                biggest = disk;
                orig = o;
                comp = c;
            }
        }
        out[0] = biggest;
        out[1] = orig;
        out[2] = comp;
        return out;
    }

    private long[] diskBytes() {
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = fs.getBlockSizeLong() * fs.getBlockCountLong();
            long free = fs.getBlockSizeLong() * fs.getAvailableBlocksLong();
            return new long[] { total, free };
        } catch (Exception ex) {
            return new long[] { 0, 0 };
        }
    }

    private String gpuHzFile = "";
    private String gpuLoadFile = "";

    private boolean namedGpu(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.contains("gpu") || n.contains("mali") || n.contains("g3d") || n.contains("kgsl")
            || n.contains("sgpu") || n.contains("xclipse") || n.contains("amdgpu");
    }

    private int readPct(String path) {
        String line = readLine(path).toLowerCase(Locale.US);
        if (line.length() == 0) return -1;
        if (line.contains("idle")) return 0;
        String num = line.replace("%", " ").trim().split("\\s+")[0];
        long v = parseLong(num);
        if (v < 0) return -1;
        if (v <= 100) return (int) v;
        if (v <= 1000) return (int) Math.min(100, Math.round(v / 10.0));
        return -1;
    }

    private int gpuLoad() {
        if (gpuLoadFile.length() > 0) {
            int v = readPct(gpuLoadFile);
            if (v >= 0) return v;
            gpuLoadFile = "";
        }
        String[] paths = new String[] {
            "/sys/kernel/gpu/gpu_busy",
            "/sys/kernel/gpu/gpu_utilization",
            "/sys/devices/platform/exynos-gpu/gpu_busy",
            "/sys/devices/platform/exynos-gpu/utilization",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/class/misc/mali0/device/utilization"
        };
        for (String path : paths) {
            int v = readPct(path);
            if (v >= 0) { gpuLoadFile = path; return v; }
        }
        File dir = new File("/sys/class/devfreq");
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                if (!namedGpu(kid.getName())) continue;
                String path = new File(kid, "load").getPath();
                String line = readLine(path);
                if (line.length() == 0) continue;
                String[] p = line.trim().split("\\s+");
                int v = -1;
                if (p.length >= 2) {
                    long a = parseLong(p[0]);
                    long b = parseLong(p[1]);
                    if (b > 0) v = (int) Math.min(100, Math.round(100.0 * a / b));
                } else v = readPct(path);
                if (v >= 0) { gpuLoadFile = path; return v; }
            }
        }
        return -1;
    }

    private int gpuHz() {
        if (gpuHzFile.length() > 0) {
            int mhz = toMhz(parseLong(readLine(gpuHzFile)));
            if (mhz > 0) return mhz;
            gpuHzFile = "";
        }
        String[] paths = new String[] {
            "/sys/kernel/gpu/gpu_clock",
            "/sys/kernel/gpu/gpu_freq",
            "/sys/devices/platform/exynos-gpu/gpu_clock",
            "/sys/devices/platform/exynos-gpu/gpu_freq",
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/misc/mali0/device/clock",
            "/sys/class/misc/mali0/device/cur_freq"
        };
        for (String path : paths) {
            int mhz = toMhz(parseLong(readLine(path)));
            if (mhz > 0) { gpuHzFile = path; return mhz; }
        }
        File dir = new File("/sys/class/devfreq");
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File kid : kids) {
                if (!namedGpu(kid.getName())) continue;
                String path = new File(kid, "cur_freq").getPath();
                int mhz = toMhz(parseLong(readLine(path)));
                if (mhz > 0) { gpuHzFile = path; return mhz; }
            }
        }
        File plat = new File("/sys/devices/platform");
        File[] nodes = plat.listFiles();
        if (nodes != null) {
            for (File node : nodes) {
                if (!namedGpu(node.getName())) continue;
                String[] rel = new String[] { "gpu_clock", "gpu_freq", "cur_freq", "devfreq/" + node.getName() + "/cur_freq" };
                for (String r : rel) {
                    String path = new File(node, r).getPath();
                    int mhz = toMhz(parseLong(readLine(path)));
                    if (mhz > 0) { gpuHzFile = path; return mhz; }
                }
            }
        }
        return 0;
    }

    private int toMhz(long raw) {
        if (raw <= 0) return 0;
        if (raw > 1000000L) return (int) Math.round(raw / 1000000.0);
        if (raw > 10000L) return (int) Math.round(raw / 1000.0);
        return (int) raw;
    }

    private JSONArray thermals() {
        JSONArray arr = new JSONArray();
        try {
            File dir = new File("/sys/class/thermal");
            File[] kids = dir.listFiles();
            if (kids != null) {
                java.util.Arrays.sort(kids);
                for (File kid : kids) {
                    if (!kid.getName().startsWith("thermal_zone")) continue;
                    String temp = readLine(new File(kid, "temp").getPath());
                    if (temp.length() == 0) continue;
                    long raw = parseLong(temp);
                    double c = raw > 1000 ? raw / 1000.0 : raw;
                    String type = readLine(new File(kid, "type").getPath());
                    JSONObject o = new JSONObject();
                    o.put("name", type.length() == 0 ? kid.getName() : type);
                    o.put("c", Math.round(c * 10.0) / 10.0);
                    arr.put(o);
                    if (arr.length() >= 24) break;
                }
            }
        } catch (Exception ignored) {}
        return arr;
    }

    private int netPick = -1;

    private double[] netRate() {
        long[] bytes = ifaceBytes(-1);
        long now = System.currentTimeMillis();
        if (bytes == null) {
            netClosed = true;
            shownRx = 0;
            shownTx = 0;
            prevRx = -1;
            netStall = 0;
            return new double[] { 0, 0 };
        }
        netClosed = false;
        if (prevRx >= 0 && now > prevNetAt) {
            double sec = (now - prevNetAt) / 1000.0;
            // A second probe in the same frame must not throw the byte delta away.
            if (sec < 0.04) return new double[] { shownRx, shownTx };
            if (bytes[0] < prevRx || bytes[1] < prevTx) {
                shownRx = 0;
                shownTx = 0;
                netStall = 0;
            } else if (sec < 8) {
                double rx = (bytes[0] - prevRx) / sec;
                double tx = (bytes[1] - prevTx) / sec;
                if (rx == 0 && tx == 0) netStall++;
                else netStall = 0;
                if (rx >= 0 && tx >= 0 && rx < 500000000 && tx < 500000000) {
                    shownRx = shownRx * 0.35 + rx * 0.65;
                    shownTx = shownTx * 0.35 + tx * 0.65;
                }
                if (netStall >= 5) {
                    netStall = 0;
                    int stuck = netPick;
                    long[] next = ifaceBytes(stuck);
                    if (next == null) netPick = stuck;
                    else {
                        prevRx = next[0];
                        prevTx = next[1];
                        prevNetAt = now;
                        shownRx = 0;
                        shownTx = 0;
                        return new double[] { 0, 0 };
                    }
                }
            }
        }
        prevRx = bytes[0];
        prevTx = bytes[1];
        prevNetAt = now;
        return new double[] { shownRx, shownTx };
    }

    /** skip >= 0 drops that source so a frozen counter can fall through to the next one. */
    private long[] ifaceBytes(int skip) {
        long[][] rows = new long[][] { trafficBytes(), activeIface(), sysNet(), procNet() };
        if (skip < 0 && netPick >= 0 && netPick < rows.length && rows[netPick] != null) return rows[netPick];
        int previous = netPick;
        for (int i = 0; i < rows.length; i++) {
            if (i == skip) continue;
            if (rows[i] == null) continue;
            if (previous != i) {
                prevRx = -1;
                shownRx = 0;
                shownTx = 0;
            }
            netPick = i;
            return rows[i];
        }
        if (skip >= 0 && skip < rows.length && rows[skip] != null) {
            netPick = skip;
            return rows[skip];
        }
        netPick = -1;
        return null;
    }

    private long[] activeIface() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network net = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = (cm == null || net == null) ? null : cm.getLinkProperties(net);
            if (lp == null) return null;
            String name = lp.getInterfaceName();
            if (name == null || name.length() == 0 || name.indexOf('/') >= 0 || name.indexOf('.') >= 0) return null;
            long r = parseLong(readLine("/sys/class/net/" + name + "/statistics/rx_bytes"));
            long t = parseLong(readLine("/sys/class/net/" + name + "/statistics/tx_bytes"));
            if (r <= 0 && t <= 0) return null;
            return new long[] { r, t };
        } catch (Exception ex) {
            return null;
        }
    }

    private long[] linkBytes() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            Network net = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = (cm == null || net == null) ? null : cm.getNetworkCapabilities(net);
            if (caps == null) return new long[] { 0, 0 };
            int down = caps.getLinkDownstreamBandwidthKbps();
            int up = caps.getLinkUpstreamBandwidthKbps();
            long downB = down > 0 ? (down * 1000L) / 8L : 0L;
            long upB = up > 0 ? (up * 1000L) / 8L : 0L;
            return new long[] { downB, upB };
        } catch (Exception ex) {
            return new long[] { 0, 0 };
        }
    }

    private long[] trafficBytes() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx < 0 || tx < 0 || (rx == 0 && tx == 0)) return null;
        return new long[] { rx, tx };
    }

    private long[] sysNet() {
        File dir = new File("/sys/class/net");
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        long rx = 0;
        long tx = 0;
        boolean any = false;
        for (int i = 0; i < kids.length; i++) {
            String name = kids[i].getName();
            if (name.equals("lo") || name.startsWith("dummy") || name.startsWith("ifb")) continue;
            long r = parseLong(readLine(new File(kids[i], "statistics/rx_bytes").getPath()));
            long t = parseLong(readLine(new File(kids[i], "statistics/tx_bytes").getPath()));
            if (r == 0 && t == 0) continue;
            rx += r;
            tx += t;
            any = true;
        }
        if (!any) return null;
        return new long[] { rx, tx };
    }

    private long[] procNet() {
        BufferedReader reader = null;
        long rx = 0;
        long tx = 0;
        boolean any = false;
        try {
            reader = new BufferedReader(new FileReader("/proc/net/dev"));
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String name = line.substring(0, colon).trim();
                if (name.equals("lo") || name.startsWith("dummy") || name.startsWith("ifb")) continue;
                String[] parts = line.substring(colon + 1).trim().split("\\s+");
                if (parts.length < 9) continue;
                long r = parseLong(parts[0]);
                long t = parseLong(parts[8]);
                if (r == 0 && t == 0) continue;
                rx += r;
                tx += t;
                any = true;
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        }
        if (!any) return null;
        return new long[] { rx, tx };
    }

    private JSONObject props() throws Exception {
        if (cachedProps != null) return cachedProps;
        JSONObject o = new JSONObject();
        o.put("soc", prop("ro.soc.model"));
        o.put("hardware", prop("ro.hardware"));
        o.put("brand", prop("ro.product.brand"));
        o.put("device", prop("ro.product.device"));
        o.put("board", prop("ro.product.board"));
        o.put("maker", prop("ro.product.manufacturer"));
        String[] gl = glStrings();
        String renderer = gl[0] == null ? "" : gl[0].trim();
        String glVersion = gl[1] == null ? "" : gl[1].trim();
        String glVendor = gpuVendor(gl[2], renderer);
        String gpu = marketingGpu(renderer);
        if (gpu.length() == 0 && !oemOnly(renderer)) gpu = renderer;
        String egl = glEsVersion();
        o.put("renderer", renderer);
        o.put("glVersion", glVersion);
        o.put("glVendor", glVendor);
        o.put("egl", egl);
        o.put("vulkan", gpu);
        o.put("gpu", gpu.length() > 0 ? gpu : renderer);
        o.put("shown", shownName());
        o.put("platform", prop("ro.board.platform"));
        o.put("abi", prop("ro.product.cpu.abi"));
        o.put("sdk", prop("ro.build.version.sdk"));
        o.put("patch", prop("ro.build.version.security_patch"));
        o.put("buildId", prop("ro.build.display.id"));
        o.put("built", prop("ro.build.date"));
        o.put("oneui", oneUi(prop("ro.build.version.oneui")));
        o.put("fingerprint", prop("ro.build.fingerprint"));
        o.put("brand", present(o.optString("brand", "")));
        o.put("maker", present(o.optString("maker", "")));
        o.put("ramType", ramKind());
        o.put("ufs", ufsVersion());
        cachedProps = o;
        return o;
    }

    private String shownName() {
        String[] keys = new String[] {
            "ro.product.marketname",
            "ro.product.vendor.marketname",
            "ro.product.odm.marketname",
            "ro.product.system.marketname",
            "ro.product.product.marketname"
        };
        for (String key : keys) {
            String value = prop(key);
            if (value.length() > 1 && !value.equals(Build.MODEL)) return value;
        }
        try {
            String named = android.provider.Settings.Global.getString(getContentResolver(), "device_name");
            if (named != null) {
                named = named.trim();
                if (named.length() > 1 && !named.equals(Build.MODEL)) return named;
            }
        } catch (Exception ignored) {}
        return Build.MODEL == null ? "" : Build.MODEL;
    }

    private String ramKind() {
        String[] files = new String[] {
            "/sys/class/sec/sec_hw_param/ddr_type",
            "/sys/devices/virtual/sec/sec_hw_param/ddr_type",
            "/sys/class/sec/sec_hw_param/lpddr_type",
            "/sys/devices/platform/ddr_type"
        };
        for (int i = 0; i < files.length; i++) {
            String named = normalizeRam(readLine(files[i]));
            if (named.length() > 0) return named;
        }
        String[] keys = new String[] { "ro.boot.ddr_type", "ro.boot.hardware.ddr", "ro.vendor.ddr_type" };
        for (int i = 0; i < keys.length; i++) {
            String named = normalizeRam(prop(keys[i]));
            if (named.length() > 0) return named;
        }
        return ramFromSoc();
    }

    /** Module type is not a public Android API. Known SoCs map to the memory they ship with. */
    private String ramFromSoc() {
        String blob = socBlob();
        if (containsAny(blob, "s5e9965", "exynos2600", "exynos 2600", "sm-s942", "m1s",
                "s5e9955", "exynos2500", "exynos 2500", "sm-s938", "sm-s931",
                "s5e9945", "exynos2400", "exynos 2400", "sm-s921", "sm-s926", "sm-s928",
                "sm8650", "sm8750", "sm8735", "sm8635", "mt6989", "mt6991",
                "dimensity 9300", "dimensity 9400", "tensor g4", "tensor g5", "zuma")) return "LPDDR5X";
        if (containsAny(blob, "sm8550", "sm8475", "sm8450", "s5e9925", "exynos2200", "exynos 2200", "tensor g3")) return "LPDDR5X";
        if (containsAny(blob, "sm7325", "sm6375", "sm6225", "mt6769", "mt6765", "helio g85", "helio g80", "helio g99")) return "LPDDR4X";
        return "";
    }

    private String ufsVersion() {
        String measured = ufsFromSys();
        if (measured.length() > 0) return measured;
        String blob = socBlob();
        if (containsAny(blob, "s5e9965", "exynos2600", "exynos 2600", "sm-s942", "m1s", "sm8750", "sm8735")) return "UFS 4.1 class";
        if (containsAny(blob, "s5e9955", "exynos2500", "exynos 2500", "s5e9945", "exynos2400", "exynos 2400", "sm8650", "sm8550")) return "UFS 4.0 class";
        if (containsAny(blob, "sm8450", "sm7325", "exynos2200", "exynos 2200")) return "UFS 3.1 class";
        return "";
    }

    private String socBlob() {
        String model = Build.MODEL == null ? "" : Build.MODEL;
        String device = Build.DEVICE == null ? "" : Build.DEVICE;
        return (prop("ro.soc.model") + " " + prop("ro.hardware") + " " + prop("ro.product.board") + " "
            + prop("ro.board.platform") + " " + model + " " + device + " " + prop("ro.product.device")).toLowerCase(Locale.US);
    }

    private static boolean containsAny(String blob, String... needles) {
        for (int i = 0; i < needles.length; i++) if (blob.contains(needles[i])) return true;
        return false;
    }

    private String ufsFromSys() {
        String[] direct = new String[] {
            "/sys/block/sda/device/spec_version",
            "/sys/block/sda/device/rev",
            "/sys/class/scsi_host/host0/ufs_version",
            "/sys/devices/platform/soc/1d84000.ufshc/ufs_version",
            "/sys/devices/platform/112b0000.ufshc/ufs_version",
            "/sys/devices/platform/soc/1d84000.ufshc/device_descriptor/specification_version"
        };
        for (int i = 0; i < direct.length; i++) {
            String hit = parseUfs(readLine(direct[i]));
            if (hit.length() > 0) return hit;
        }
        File plat = new File("/sys/devices/platform");
        File[] kids = plat.listFiles();
        if (kids == null) return "";
        int seen = 0;
        for (int i = 0; i < kids.length && seen < 48; i++) {
            seen++;
            String hit = ufsIn(kids[i]);
            if (hit.length() > 0) return hit;
            File[] grand = kids[i].listFiles();
            if (grand == null) continue;
            for (int j = 0; j < grand.length && j < 24; j++) {
                hit = ufsIn(grand[j]);
                if (hit.length() > 0) return hit;
            }
        }
        return "";
    }

    private static String ufsIn(File dir) {
        if (dir == null || !dir.getName().toLowerCase(Locale.US).contains("ufs")) return "";
        String hit = parseUfs(readLine(new File(dir, "ufs_version").getPath()));
        if (hit.length() > 0) return hit;
        return parseUfs(readLine(new File(dir, "device_descriptor/specification_version").getPath()));
    }

    private static String parseUfs(String raw) {
        if (raw == null) return "";
        String v = raw.trim().toLowerCase(Locale.US).replace("ufs", "").trim();
        if (v.length() == 0 || v.equals("0")) return "";
        if (v.startsWith("4.2") || v.equals("420") || v.equals("0420")) return "UFS 4.2";
        if (v.startsWith("4.1") || v.equals("410") || v.equals("0410")) return "UFS 4.1";
        if (v.startsWith("4.0") || v.equals("400") || v.equals("0400")) return "UFS 4.0";
        if (v.startsWith("3.1") || v.equals("310") || v.equals("0310")) return "UFS 3.1";
        if (v.startsWith("3.0") || v.equals("300")) return "UFS 3.0";
        if (v.startsWith("2.2") || v.equals("220")) return "UFS 2.2";
        if (v.startsWith("2.1") || v.equals("210")) return "UFS 2.1";
        if (v.startsWith("2.0") || v.equals("200")) return "UFS 2.0";
        return "";
    }

    private static String normalizeRam(String raw) {
        if (raw == null) return "";
        String low = raw.trim().toLowerCase(Locale.US).replace("_", "").replace(" ", "").replace("-", "");
        if (low.length() == 0) return "";
        if (low.contains("lpddr5x")) return "LPDDR5X";
        if (low.contains("lpddr5")) return "LPDDR5";
        if (low.contains("lpddr4x")) return "LPDDR4X";
        if (low.contains("lpddr4")) return "LPDDR4";
        if (low.contains("lpddr3")) return "LPDDR3";
        return "";
    }

    private String glEsVersion() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return "";
            android.content.pm.ConfigurationInfo info = am.getDeviceConfigurationInfo();
            if (info == null) return "";
            String version = info.getGlEsVersion();
            return version == null ? "" : version.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean oemOnly(String value) {
        if (value == null) return true;
        String v = value.toLowerCase(Locale.US).replace(".", "").replace(",", "").replace("  ", " ").trim();
        return v.length() == 0
            || v.equals("samsung")
            || v.equals("samsung electronics")
            || v.equals("samsung electronics co ltd")
            || v.equals("samsung electronics co ltd.");
    }

    /** GL_VENDOR on Samsung GPUs is often the phone maker. Prefer the company that built the GPU. */
    private static String gpuVendor(String glVendor, String renderer) {
        String r = renderer == null ? "" : renderer.toLowerCase(Locale.US);
        if (r.contains("xclipse")) return "AMD";
        if (r.contains("adreno")) return "Qualcomm";
        if (r.contains("mali") || r.contains("immortalis")) return "ARM";
        if (r.contains("powervr")) return "Imagination";
        if (r.contains("maleoon")) return "Huawei";
        if (r.contains("nvidia") || r.contains("tegra")) return "NVIDIA";
        if (glVendor == null || oemOnly(glVendor)) return "";
        return glVendor.trim();
    }

    private static String marketingGpu(String renderer) {
        if (renderer == null || renderer.length() == 0) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "(?i)(xclipse\\s*\\d+|adreno\\s*(?:\\d+[a-z]*)|mali-?[a-z0-9]+|immortalis-?[a-z0-9]+|powervr\\s*[a-z0-9]+)"
        ).matcher(renderer);
        if (!m.find()) return "";
        String name = m.group(1).replaceAll("\\s+", " ").trim();
        return present(name);
    }

    private static String present(String raw) {
        if (raw == null) return "";
        String v = raw.trim().replaceAll("\\s+", " ");
        if (v.length() == 0) return "";
        boolean hasUpper = false;
        for (int i = 0; i < v.length(); i++) if (Character.isUpperCase(v.charAt(i))) { hasUpper = true; break; }
        if (hasUpper) return v;
        StringBuilder sb = new StringBuilder(v.length());
        boolean up = true;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == ' ' || c == '-' || c == '/' || c == '_') { sb.append(c); up = true; continue; }
            if (up && Character.isLetter(c)) { sb.append(Character.toUpperCase(c)); up = false; }
            else { sb.append(c); up = false; }
        }
        return sb.toString();
    }

    private JSONArray localListeners() {
        JSONArray out = new JSONArray();
        readListeners("/proc/net/tcp", false, out);
        readListeners("/proc/net/tcp6", true, out);
        return out;
    }

    private static void readListeners(String path, boolean v6, JSONArray out) {
        if (out.length() >= 24) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null && out.length() < 24) {
                if (first) { first = false; continue; }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 4) continue;
                if (!"0A".equalsIgnoreCase(parts[3])) continue;
                int colon = parts[1].indexOf(':');
                if (colon <= 0) continue;
                String ip = v6 ? "" : hexIp4(parts[1].substring(0, colon));
                if (!v6 && ip.length() == 0) continue;
                int port = Integer.parseInt(parts[1].substring(colon + 1), 16);
                if (port <= 0 || port > 65535) continue;
                out.put(v6 ? ("tcp6:" + port) : (ip + ":" + port));
            }
        } catch (Exception ignored) {
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        }
    }

    private static String hexIp4(String hex) {
        if (hex == null || hex.length() != 8) return "";
        try {
            long v = Long.parseLong(hex, 16);
            return (v & 255) + "." + ((v >> 8) & 255) + "." + ((v >> 16) & 255) + "." + ((v >> 24) & 255);
        } catch (Exception ex) {
            return "";
        }
    }

    private String effectiveTier() {
        File licence = new File(getFilesDir(), "full.key");
        if (licence.isFile() && licence.length() > 0) {
            if ("premium".equals(readSmallText(licence, 32))) return "premium";
            return "pro";
        }
        try {
            if (new Deck().trialLeft() > 0) return "pro";
        } catch (Exception ignored) {}
        return "free";
    }

    private int platformCap() {
        String tier = effectiveTier();
        if ("premium".equals(tier)) return 32;
        if ("pro".equals(tier)) return 8;
        return 1;
    }

    private File platformFile() {
        return new File(getFilesDir(), "api-platforms.bin");
    }

    private SecretKey platformKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias("meridian.platforms")) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                "meridian.platforms",
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
            kg.init(spec);
            kg.generateKey();
        }
        return (SecretKey) ks.getKey("meridian.platforms", null);
    }

    private String readPlatforms() {
        File file = platformFile();
        if (!file.isFile() || file.length() < 13 || file.length() > 65536) return "{}";
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] blob = new byte[(int) file.length()];
            int off = 0;
            while (off < blob.length) {
                int n = in.read(blob, off, blob.length - off);
                if (n < 0) break;
                off += n;
            }
            if (off < 13) return "{}";
            byte[] iv = Arrays.copyOfRange(blob, 0, 12);
            byte[] ct = Arrays.copyOfRange(blob, 12, off);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, platformKey(), new GCMParameterSpec(128, iv));
            String json = new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
            if (json.length() == 0 || json.charAt(0) != '{') return "{}";
            return json;
        } catch (Exception ex) {
            return "{}";
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
        }
    }

    private String writePlatforms(String json) {
        if (json == null) return "Nothing to store.";
        String body = json.trim();
        if (body.length() == 0 || body.length() > 32000 || body.charAt(0) != '{') return "That key file is not valid.";
        try {
            JSONObject root = new JSONObject(body);
            JSONArray items = root.optJSONArray("items");
            int count = items == null ? 0 : items.length();
            int cap = platformCap();
            if (count > cap) {
                return cap == 1
                    ? "Free keeps one API platform. Pro keeps the list."
                    : "This plan keeps " + cap + " API platforms.";
            }
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) return "That key file is not valid.";
                    String base = item.optString("base", "");
                    String key = item.optString("key", "");
                    if (base.length() > 200 || key.length() > 512 || item.optString("model", "").length() > 120) return "That field is too long.";
                    if (base.length() > 0 && !base.startsWith("https://")) return "The address must start with https://";
                }
            }
            JSONObject active = root.optJSONObject("active");
            if (active != null) {
                String base = active.optString("base", "");
                if (base.length() > 0 && !base.startsWith("https://")) return "The address must start with https://";
                if (active.optString("key", "").length() > 512) return "That field is too long.";
            }
            byte[] plain = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] iv = new byte[12];
            new java.security.SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, platformKey(), new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain);
            File out = platformFile();
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(out);
                fos.write(iv);
                fos.write(ct);
                fos.flush();
            } finally {
                if (fos != null) fos.close();
            }
            return "ok";
        } catch (Exception ex) {
            return "The key could not be stored.";
        }
    }

    private String powerNow() {
        try {
            if (android.provider.Settings.Global.getInt(getContentResolver(), "low_power", 0) == 1) return "saver";
        } catch (Exception ignored) {}
        try {
            if (android.provider.Settings.System.getInt(getContentResolver(), "high_performance_mode", 0) == 1) return "fast";
        } catch (Exception ignored) {}
        return "regular";
    }

    private String[] glStrings() {
        String[] out = new String[] { "", "", "" };
        android.opengl.EGLDisplay display = android.opengl.EGL14.EGL_NO_DISPLAY;
        android.opengl.EGLContext context = android.opengl.EGL14.EGL_NO_CONTEXT;
        android.opengl.EGLSurface surface = android.opengl.EGL14.EGL_NO_SURFACE;
        try {
            display = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY);
            if (display == android.opengl.EGL14.EGL_NO_DISPLAY) return out;
            int[] version = new int[2];
            if (!android.opengl.EGL14.eglInitialize(display, version, 0, version, 1)) return out;
            int[] configAttr = new int[] {
                android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
                android.opengl.EGL14.EGL_SURFACE_TYPE, android.opengl.EGL14.EGL_PBUFFER_BIT,
                android.opengl.EGL14.EGL_NONE
            };
            android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
            int[] count = new int[1];
            if (!android.opengl.EGL14.eglChooseConfig(display, configAttr, 0, configs, 0, 1, count, 0) || count[0] < 1) return out;
            int[] contextAttr = new int[] { android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, android.opengl.EGL14.EGL_NONE };
            context = android.opengl.EGL14.eglCreateContext(display, configs[0], android.opengl.EGL14.EGL_NO_CONTEXT, contextAttr, 0);
            int[] surfaceAttr = new int[] { android.opengl.EGL14.EGL_WIDTH, 1, android.opengl.EGL14.EGL_HEIGHT, 1, android.opengl.EGL14.EGL_NONE };
            surface = android.opengl.EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttr, 0);
            if (!android.opengl.EGL14.eglMakeCurrent(display, surface, surface, context)) return out;
            String renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            String glVersion = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VERSION);
            String vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR);
            out[0] = renderer == null ? "" : renderer.trim();
            out[1] = glVersion == null ? "" : glVersion.trim();
            out[2] = vendor == null ? "" : vendor.trim();
        } catch (Throwable ignored) {
        } finally {
            try {
                if (display != android.opengl.EGL14.EGL_NO_DISPLAY) {
                    android.opengl.EGL14.eglMakeCurrent(display, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT);
                    if (surface != android.opengl.EGL14.EGL_NO_SURFACE) android.opengl.EGL14.eglDestroySurface(display, surface);
                    if (context != android.opengl.EGL14.EGL_NO_CONTEXT) android.opengl.EGL14.eglDestroyContext(display, context);
                    android.opengl.EGL14.eglTerminate(display);
                }
            } catch (Throwable ignored) {}
        }
        return out;
    }

    private static String oneUi(String raw) {
        long n = parseLong(raw);
        if (n < 10000) return raw;
        long major = n / 10000;
        long minor = (n / 100) % 100;
        return major + "." + minor;
    }

    private static java.lang.reflect.Method sysPropGet;
    private static boolean sysPropTried = false;

    /** android.os.SystemProperties.get is an in-process read; the getprop fallback forks a process per key (~25 forks on first paint). */
    private static String prop(String key) {
        try {
            if (!sysPropTried) {
                sysPropTried = true;
                sysPropGet = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class);
            }
            if (sysPropGet != null) {
                Object v = sysPropGet.invoke(null, key, "");
                return v == null ? "" : String.valueOf(v).trim();
            }
        } catch (Throwable ignored) {
            sysPropGet = null;
        }
        try {
            Process process = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            process.waitFor();
            return line == null ? "" : line.trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private static String readLine(String path) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception ex) {
            return "";
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) {}
        }
    }

    private static long parseLong(String raw) {
        if (raw == null) return 0;
        String v = raw.trim();
        if (v.length() == 0) return 0;
        int i = 0;
        if (v.charAt(0) == '-') i = 1;
        while (i < v.length() && Character.isDigit(v.charAt(i))) i++;
        if (i == 0 || (v.charAt(0) == '-' && i == 1)) return 0;
        try {
            return Long.parseLong(v.substring(0, i));
        } catch (Exception ex) {
            return 0;
        }
    }
}
