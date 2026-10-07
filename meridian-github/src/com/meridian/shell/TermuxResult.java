package com.meridian.shell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class TermuxResult extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int gen = intent.getIntExtra("gen", -1);
        if (gen != MainActivity.jobGen) return;
        String b64 = intent.getStringExtra("b64");
        if (b64 != null && b64.length() > 0) {
            try {
                String text = new String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
                synchronized (MainActivity.GATE) {
                    if (!MainActivity.ready) MainActivity.pendingOut = text;
                    MainActivity.GATE.notifyAll();
                }
            } catch (Exception ignored) {}
            return;
        }
        String out = "";
        int code = -1;
        Bundle bundle = intent.getBundleExtra("result");
        if (bundle == null) bundle = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT_BUNDLE");
        if (bundle != null) {
            String stdout = bundle.getString("stdout", "");
            String stderr = bundle.getString("stderr", "");
            out = stdout == null ? "" : stdout;
            if (stderr != null && stderr.length() > 0) {
                if (out.length() > 0) out += "\n";
                out += stderr;
            }
            if (bundle.containsKey("exitCode")) code = bundle.getInt("exitCode", -1);
            else code = bundle.getInt("exit_code", -1);
        }
        synchronized (MainActivity.GATE) {
            MainActivity.pendingOut = out;
            MainActivity.pendingCode = code;
            MainActivity.ready = true;
            MainActivity.GATE.notifyAll();
            try {
                if (MainActivity.liveSock != null) MainActivity.liveSock.close();
            } catch (Exception ignored) {}
        }
    }
}
