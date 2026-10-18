package com.meridian.shell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Receives the result PendingIntent that Termux fires when a RUN_COMMAND job ends.
 * The receiver is not exported: a PendingIntent is sent with Meridian's own identity, so
 * Termux can still reach it, while other apps can no longer broadcast fake results into the console.
 */
public class TermuxResult extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getBundleExtra("result");
        if (bundle == null) bundle = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT_BUNDLE");
        // Termux sets "err" to something other than -1 when it refused the command before running it
        // (Pre-Execution failure), for example when allow-external-apps is not true.
        int err = bundle != null && bundle.containsKey("err") ? bundle.getInt("err", -1) : -1;
        String errmsg = bundle != null ? bundle.getString("errmsg", "") : "";
        if (errmsg == null) errmsg = "";
        String kind = errmsg.contains("allow-external-apps") ? "external" : "refused";
        int ping = intent.getIntExtra("ping", -1);
        if (ping >= 0) {
            if (err != -1) MainActivity.pingResult(ping, kind, errmsg);
            else {
                String stdout = bundle == null ? "" : bundle.getString("stdout", "");
                MainActivity.pingResult(ping, stdout != null && stdout.contains("meridian-link-ok") ? "ok" : "refused", stdout == null ? "" : stdout);
            }
            return;
        }
        int gen = intent.getIntExtra("gen", -1);
        if (gen != MainActivity.jobGen) return;
        String out = "";
        int code = -1;
        if (err != -1) {
            MainActivity.noteFault(kind, errmsg);
            out = "Termux blocked Meridian before the command started. Tap Fix Termux connection, then run this again."
                + (errmsg.length() > 0 ? "\n\n" + errmsg.trim() : "");
            code = 1;
        } else if (bundle != null) {
            String stdout = bundle.getString("stdout", "");
            String stderr = bundle.getString("stderr", "");
            out = stdout == null ? "" : stdout;
            if (stderr != null && stderr.length() > 0) {
                if (out.length() > 0) out += "\n";
                out += stderr;
            }
            if (bundle.containsKey("exitCode")) code = bundle.getInt("exitCode", -1);
            else code = bundle.getInt("exit_code", -1);
            // Termux moves oversized output to a file instead of the bundle; fall back to what the live socket captured.
            if (out.length() == 0 && bundle.getString("stdout_original_length", "0").length() > 1) out = MainActivity.pendingOut;
        }
        synchronized (MainActivity.GATE) {
            MainActivity.pendingOut = MainActivity.clipOut(out);
            MainActivity.pendingCode = code;
            MainActivity.ready = true;
            MainActivity.GATE.notifyAll();
            try {
                if (MainActivity.liveSock != null) MainActivity.liveSock.close();
            } catch (Exception ignored) {}
        }
    }
}
