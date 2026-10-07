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
import android.net.TrafficStats;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {
    static final boolean OPEN = false;
    private static final String PAY_URL = "https://checkout.revolut.com/pay/044a268e-4869-4708-9b0f-8e302cc28c37";
    private WebView web;
    static final Object GATE = new Object();
    static volatile String pendingOut = "";
    static volatile int jobGen = 0;
    static volatile int pendingCode = -1;
    private volatile String speedJson = "";
    static volatile boolean ready = false;
    static volatile boolean jobLive = false;
    static volatile java.net.ServerSocket liveSock;

    private String cachedProps = "";
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView web = new WebView(this);
        this.web = web;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        web.addJavascriptInterface(new Deck(), "MeridianPhone");
        web.loadUrl("file:///android_asset/www/index.html");
        setContentView(web);
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

    private void launch(String cmd) {
        synchronized (GATE) {
            jobGen++;
            ready = false;
            pendingOut = "";
            pendingCode = -1;
        }
        int gen = jobGen;
        int port = openLive();
        Intent intent = new Intent();
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[] { "-lc", prepare(cmd, port) });
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
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
        try {
            while (liveSock == sock && !sock.isClosed()) {
                java.net.Socket client = sock.accept();
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                        if (sb.length() > 60000) sb.delete(0, sb.length() - 40000);
                        synchronized (GATE) {
                            if (!ready) pendingOut = sb.toString();
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    try { client.close(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private String prepare(String cmd, int port) {
        if (cmd == null) cmd = "";
        String hello = port > 0
            ? "printf '%s\\n' 'Termux' | /system/bin/toybox nc -w 2 127.0.0.1 " + port + " >/dev/null 2>&1 || true\n"
            : "";
        String run;
        if (port > 0) {
            run = "if [ -x /system/bin/script ]; then /system/bin/script -q -c \"bash \\\"$HOME/.meridian/run.sh\\\"\" \"$HOME/.meridian/live.log\" | /system/bin/toybox nc 127.0.0.1 " + port + "; status=${PIPESTATUS[0]}; "
                + "elif command -v script >/dev/null 2>&1; then script -q -f -c \"bash \\\"$HOME/.meridian/run.sh\\\"\" \"$HOME/.meridian/live.log\" | /system/bin/toybox nc 127.0.0.1 " + port + "; status=${PIPESTATUS[0]}; "
                + "else bash \"$HOME/.meridian/run.sh\" > \"$HOME/.meridian/live.log\" 2>&1 & pid=$!; pos=0; while kill -0 \"$pid\" 2>/dev/null; do size=$(wc -c < \"$HOME/.meridian/live.log\" 2>/dev/null || echo 0); if [ \"$size\" -gt \"$pos\" ]; then tail -c +$((pos+1)) \"$HOME/.meridian/live.log\" | /system/bin/toybox nc 127.0.0.1 " + port + " || true; pos=$size; fi; sleep 0.4; done; wait \"$pid\"; status=$?; fi\n";
        } else {
            run = "bash \"$HOME/.meridian/run.sh\" > \"$HOME/.meridian/live.log\" 2>&1; status=$?\n";
        }
        return "export DEBIAN_FRONTEND=noninteractive; export DEBCONF_NONINTERACTIVE_SEEN=true; "
            + "mkdir -p \"$PREFIX/etc/apt/apt.conf.d\" \"$PREFIX/etc/dpkg/dpkg.cfg.d\" \"$HOME/.meridian\" 2>/dev/null; "
            + "printf '%s\\n' 'Dpkg::Options {\"--force-confdef\";\"--force-confold\";};' > \"$PREFIX/etc/apt/apt.conf.d/99meridian\" 2>/dev/null; "
            + "printf '%s\\n' 'force-confdef' 'force-confold' > \"$PREFIX/etc/dpkg/dpkg.cfg.d/99meridian\" 2>/dev/null; "
            + "cat > \"$HOME/.meridian/cmd.sh\" << 'MERIDIAN_CMD_END'\n"
            + cmd + "\n"
            + "MERIDIAN_CMD_END\n"
            + "cat > \"$HOME/.meridian/run.sh\" << 'MERIDIAN_RUN_END'\n"
            + "bash \"$HOME/.meridian/cmd.sh\"\n"
            + "echo $? > \"$HOME/.meridian/code\"\n"
            + "MERIDIAN_RUN_END\n"
            + hello
            + (usesPackage(cmd)
                ? "alive=0; for d in /proc/[0-9]*; do c=$(cat \"$d/comm\" 2>/dev/null) || continue; case \"$c\" in apt|apt-get|dpkg) alive=1; kill \"$(basename \"$d\")\" 2>/dev/null || true ;; esac; done; "
                    + "if [ \"$alive\" = 1 ]; then sleep 1; for d in /proc/[0-9]*; do c=$(cat \"$d/comm\" 2>/dev/null) || continue; case \"$c\" in apt|apt-get|dpkg) kill -9 \"$(basename \"$d\")\" 2>/dev/null || true ;; esac; done; sleep 1; "
                    + "rm -f \"$PREFIX/var/lib/dpkg/lock-frontend\" \"$PREFIX/var/lib/dpkg/lock\" \"$PREFIX/var/cache/apt/archives/lock\" \"$PREFIX/var/lib/apt/lists/lock\"; echo Stopped the stuck installer.; fi; "
                    + "dpkg --configure -a --force-confdef --force-confold || true\n"
                : "alive=0; for d in /proc/[0-9]*; do c=$(cat \"$d/comm\" 2>/dev/null) || continue; case \"$c\" in apt|apt-get|dpkg) alive=1;; esac; done; "
                    + "if [ \"$alive\" = 0 ] && { [ -e \"$PREFIX/var/lib/dpkg/lock-frontend\" ] || [ -e \"$PREFIX/var/lib/dpkg/lock\" ]; }; then rm -f \"$PREFIX/var/lib/dpkg/lock-frontend\" \"$PREFIX/var/lib/dpkg/lock\" \"$PREFIX/var/cache/apt/archives/lock\" \"$PREFIX/var/lib/apt/lists/lock\"; fi\n")
            + ": > \"$HOME/.meridian/live.log\"\n"
            + run
            + "cat \"$HOME/.meridian/live.log\" 2>/dev/null || true\n"
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
            return pendingOut + "\nexit " + pendingCode;
        }
    }

    private String sh(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private String shellPath(String path) {
        String value = safePath(path);
        if ("$HOME".equals(value)) return "\"$HOME\"";
        if (value.startsWith("$HOME/")) return "\"$HOME\"/" + sh(value.substring(6));
        return sh(value);
    }

    private String safePath(String path) {
        if (path == null) return "$HOME";
        String value = path.trim();
        if (value.length() == 0 || value.contains("\n") || value.contains("..")) return "$HOME";
        if (value.startsWith("/")) {
            String home = "/data/data/com.termux/files/home";
            if (!value.equals(home) && !value.startsWith(home + "/")) return "$HOME";
        }
        return value;
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
                String brand = props.optString("brand", "");
                o.put("model", shown);
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
                int temp = bat == null ? 0 : bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                int volt = bat == null ? 0 : bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                o.put("temp", temp / 10.0);
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
                if (cachedProbe.length() > 0 && now - probeAt < 80) {
                    JSONObject cached = new JSONObject(cachedProbe);
                    cached.put("rx", net[0]);
                    cached.put("tx", net[1]);
                    cached.put("netClosed", netClosed);
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
                o.put("kernel", readLine("/proc/version"));
                o.put("cpuinfo", cpuName());
                String[] keys = new String[] { "brand", "device", "board", "maker", "hardware", "soc", "egl", "vulkan", "renderer", "glVersion", "glVendor", "platform", "abi", "sdk", "patch", "buildId", "built", "oneui", "fingerprint", "gpu", "ramType" };
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

        @JavascriptInterface
        public String run(String cmd) {
            if (!termuxInstalled()) return "Termux is not installed\nexit 1";
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
            if (!termuxInstalled()) {
                synchronized (GATE) {
                    pendingOut = "Termux is not installed";
                    pendingCode = 1;
                    ready = true;
                }
                return "missing";
            }
            jobLive = true;
            launch(cmd);
            return "ok";
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
                if (done) o.put("out", (out == null ? "" : out) + "\nexit " + code);
                else o.put("out", out == null ? "" : out);
                o.put("code", code);
                return o.toString();
            } catch (Exception ex) {
                return "{\"done\":true,\"out\":\"status error\",\"code\":1}";
            }
        }

        @JavascriptInterface
        public String unlocked() {
            if (OPEN) { markPaid(); return "yes"; }
            return new File(getFilesDir(), "full.key").isFile() ? "yes" : "no";
        }

        @JavascriptInterface
        public void pay() {
            pay("1");
        }

        @JavascriptInterface
        public void pay(String euros) {
            int amount = 1;
            try {
                amount = Integer.parseInt(euros == null ? "" : euros.trim());
            } catch (NumberFormatException ignored) {
                amount = 0;
            }
            if (amount < 1 || amount > 1000000) amount = 0;
            final int chosen = amount;
            if (chosen == 0) return;
            try {
                java.io.File out = new java.io.File(getFilesDir(), "pay-amount.txt");
                java.io.FileOutputStream stream = new java.io.FileOutputStream(out);
                stream.write(Integer.toString(chosen).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                stream.close();
            } catch (Exception ignored) {}
            runOnUiThread(new Runnable() { public void run() { showPay(); } });
        }

        @JavascriptInterface
        public String entries(String path) {
            return run("ls -1p --color=never " + shellPath(path));
        }

        @JavascriptInterface
        public String readFile(String path) {
            return run("cat " + shellPath(path));
        }

        @JavascriptInterface
        public String writeFile(String path, String body) {
            String b64 = android.util.Base64.encodeToString((body == null ? "" : body).getBytes(java.nio.charset.StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
            return run("printf %s " + sh(b64) + " | base64 -d > " + shellPath(path));
        }

        @JavascriptInterface
        public String removePath(String path) {
            return run("rm -rf " + shellPath(path));
        }

        @JavascriptInterface
        public String renamePath(String from, String to) {
            return run("mv " + shellPath(from) + " " + shellPath(to));
        }

        @JavascriptInterface
        public String makeDir(String path) {
            return run("mkdir -p " + shellPath(path));
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
            HttpURLConnection conn = null;
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
            } finally {
                if (conn != null) conn.disconnect();
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
                    String path = root.contains(":generateContent") ? root : root + "/models/" + which + ":generateContent";
                    url = new URL(path + "?key=" + java.net.URLEncoder.encode(key.trim(), "UTF-8"));
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
        return line.matches("(?i)^(pkg|apt|apt-get|dpkg|curl|wget|getprop|termux-[a-z0-9-]+|python3?|pip|npm|node|git|clang|cmake|make|ls|cat|free|df|uname|ping|ssh|vim|nvim)\\b.*");
    }

    private void markPaid() {
        try {
            java.io.File out = new java.io.File(getFilesDir(), "full.key");
            java.io.FileOutputStream stream = new java.io.FileOutputStream(out);
            stream.write("paid".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            stream.close();
        } catch (Exception ignored) {}
    }

    private void showPay() {
        if (web == null) return;
        WebView pay = new WebView(this);
        WebSettings settings = pay.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        pay.setWebViewClient(new android.webkit.WebViewClient() {
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("(document.body && document.body.innerText) || ''", new android.webkit.ValueCallback<String>() {
                    public void onReceiveValue(String value) {
                        String text = value == null ? "" : value.toLowerCase();
                        if (text.contains("payment successful") || text.contains("thank you") || text.contains("you're all set") || text.contains("you are all set") || text.contains("payment complete")) {
                            markPaid();
                            setContentView(web);
                            web.evaluateJavascript("if (window.onPaid) window.onPaid();", null);
                        }
                    }
                });
            }
        });
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.Button close = new android.widget.Button(this);
        close.setText("Close");
        close.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) { setContentView(web); }
        });
        box.addView(close);
        box.addView(pay, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(box);
        pay.loadUrl(PAY_URL);
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

    private double[] netRate() {
        long[] bytes = ifaceBytes();
        long now = System.currentTimeMillis();
        if (bytes == null) {
            netClosed = true;
            return new double[] { 0, 0 };
        }
        netClosed = false;
        if (prevRx >= 0 && now > prevNetAt) {
            if (bytes[0] < prevRx || bytes[1] < prevTx) {
                prevRx = bytes[0];
                prevTx = bytes[1];
                prevNetAt = now;
                return new double[] { shownRx, shownTx };
            }
            double sec = (now - prevNetAt) / 1000.0;
            if (sec >= 0.04 && sec < 5) {
                double rx = (bytes[0] - prevRx) / sec;
                double tx = (bytes[1] - prevTx) / sec;
                if (rx < 500000000 && tx < 500000000) {
                    shownRx = shownRx * 0.3 + rx * 0.7;
                    shownTx = shownTx * 0.3 + tx * 0.7;
                }
            }
        }
        prevRx = bytes[0];
        prevTx = bytes[1];
        prevNetAt = now;
        return new double[] { shownRx, shownTx };
    }

    private long[] ifaceBytes() {
        long[] best = null;
        long bestSum = -1;
        long[][] rows = new long[][] { trafficBytes(), sysNet(), procNet() };
        for (int i = 0; i < rows.length; i++) {
            long[] row = rows[i];
            if (row == null) continue;
            long sum = row[0] + row[1];
            if (sum > bestSum) {
                bestSum = sum;
                best = row;
            }
        }
        return best;
    }

    private long[] trafficBytes() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        if (rx < 0 || tx < 0) return null;
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
        if (cachedProps.length() > 0) return new JSONObject(cachedProps);
        JSONObject o = new JSONObject();
        o.put("soc", prop("ro.soc.model"));
        o.put("hardware", prop("ro.hardware"));
        o.put("brand", prop("ro.product.brand"));
        o.put("device", prop("ro.product.device"));
        o.put("board", prop("ro.product.board"));
        o.put("maker", prop("ro.product.manufacturer"));
        String gpu = prop("ro.hardware.vulkan");
        o.put("vulkan", gpu);
        String egl = prop("ro.hardware.egl");
        o.put("egl", egl);
        String[] gl = glStrings();
        o.put("renderer", gl[0]);
        o.put("glVersion", gl[1]);
        o.put("glVendor", gl[2]);
        if (gl[0].length() > 3 && !gl[0].equalsIgnoreCase("samsung") && !gl[0].equalsIgnoreCase(egl)) gpu = gl[0];
        else if (gpu.length() == 0) gpu = egl;
        if (gpu.length() == 0) gpu = prop("ro.hardware");
        o.put("gpu", gpu);
        o.put("shown", shownName());
        o.put("platform", prop("ro.board.platform"));
        o.put("abi", prop("ro.product.cpu.abi"));
        o.put("sdk", prop("ro.build.version.sdk"));
        o.put("patch", prop("ro.build.version.security_patch"));
        o.put("buildId", prop("ro.build.display.id"));
        o.put("built", prop("ro.build.date"));
        o.put("oneui", oneUi(prop("ro.build.version.oneui")));
        o.put("fingerprint", prop("ro.build.fingerprint"));
        o.put("ramType", ramKind());
        cachedProps = o.toString();
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
            String value = readLine(files[i]).trim();
            if (value.length() > 0) return value;
        }
        String[] keys = new String[] { "ro.boot.ddr_type", "ro.boot.hardware.ddr", "ro.vendor.ddr_type" };
        for (int i = 0; i < keys.length; i++) {
            String value = prop(keys[i]).trim();
            if (value.length() > 1) return value;
        }
        return "";
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

    private static String prop(String key) {
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
