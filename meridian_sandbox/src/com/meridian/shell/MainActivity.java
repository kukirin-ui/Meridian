package com.meridian.shell;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.concurrent.Executor;

/**
 * Mesh terminal host. The microphone runs only while this screen is in front.
 * TermuxResult still writes into the static fields below when a Termux job ends.
 */
public class MainActivity extends Activity {
    static final Object GATE = new Object();
    static volatile int jobGen = 0;
    static volatile String pendingOut = "";
    static volatile int pendingCode = -1;
    static volatile boolean ready = false;
    static volatile java.net.ServerSocket liveSock;

    private static final String KEY_ALIAS = "meridian_tee_key";
    private static final int MIC_REQUEST = 1024;

    private WebView webView;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean resumed = false;
    private int listenGen = 0;

    static String clipOut(String out) {
        if (out == null) return "";
        if (out.length() > 24000) return out.substring(out.length() - 24000);
        return out;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String[] resources = request.getResources();
                        ArrayList<String> audio = new ArrayList<String>();
                        if (resources != null) {
                            for (int i = 0; i < resources.length; i++) {
                                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[i])) audio.add(resources[i]);
                            }
                        }
                        if (audio.isEmpty()) request.deny();
                        else request.grant(audio.toArray(new String[audio.size()]));
                    }
                });
            }
        });
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/www/index.html");
        generateHardwareBoundKey();
        if (!hasMic()) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, MIC_REQUEST);
        }
    }

    private boolean hasMic() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_REQUEST && hasMic() && resumed) {
            ensureRecognizer();
            startListening();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (hasMic()) {
            ensureRecognizer();
            startListening();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        listenGen++;
        stopListening();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        resumed = false;
        listenGen++;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private void ensureRecognizer() {
        if (speechRecognizer != null) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                if (!resumed) return;
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return;
                scheduleRestart();
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) sendSpeechToWeb(matches.get(0), true);
                if (resumed) scheduleRestart();
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) sendSpeechToWeb(matches.get(0), false);
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (!resumed || speechRecognizer == null || speechIntent == null || !hasMic()) return;
        try {
            speechRecognizer.startListening(speechIntent);
        } catch (Exception ex) {
            scheduleRestart();
        }
    }

    private void scheduleRestart() {
        final int gen = listenGen;
        mainHandler.postDelayed(new Runnable() {
            public void run() {
                if (gen != listenGen || !resumed) return;
                startListening();
            }
        }, 700);
    }

    private void stopListening() {
        if (speechRecognizer == null) return;
        try { speechRecognizer.cancel(); } catch (Exception ignored) {}
    }

    private void sendSpeechToWeb(String text, boolean isFinal) {
        final String safe = jsQuote(text);
        final boolean fin = isFinal;
        runOnUiThread(new Runnable() {
            public void run() {
                if (webView == null) return;
                webView.evaluateJavascript(
                    "if(window.meridian&&window.meridian.onNativeSpeechResult){window.meridian.onNativeSpeechResult('" + safe + "'," + fin + ");}",
                    null);
            }
        });
    }

    private void tellPage(final boolean ok, final String detail) {
        final String safe = jsQuote(detail);
        runOnUiThread(new Runnable() {
            public void run() {
                if (webView == null) return;
                webView.evaluateJavascript(
                    "if(window.meridian&&window.meridian.onHardwareSignature){window.meridian.onHardwareSignature(" + ok + ",'" + safe + "');}",
                    null);
            }
        });
    }

    private static String jsQuote(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ").replace("<", "\\u003c");
    }

    private void generateHardwareBoundKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) return;
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= 30) {
                builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
            } else {
                builder.setUserAuthenticationValidityDurationSeconds(-1);
            }
            kpg.initialize(builder.build());
            kpg.generateKeyPair();
        } catch (Exception ignored) {}
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void requestBiometricSignature(final String payload) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (Build.VERSION.SDK_INT < 28) {
                        tellPage(false, "A hardware signature needs Android 9 or newer.");
                        return;
                    }
                    if (payload == null || payload.length() == 0 || payload.length() > 4000) {
                        tellPage(false, "That action could not be signed.");
                        return;
                    }
                    try {
                        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                        keyStore.load(null);
                        PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, null);
                        if (privateKey == null) {
                            tellPage(false, "This phone has no hardware key yet.");
                            return;
                        }
                        final Signature signature = Signature.getInstance("SHA256withECDSA");
                        signature.initSign(privateKey);
                        Executor executor = getMainExecutor();
                        BiometricPrompt.Builder builder = new BiometricPrompt.Builder(MainActivity.this)
                            .setTitle("Hardware signature")
                            .setSubtitle("Confirm this action on this phone")
                            .setNegativeButton("Cancel", executor, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    tellPage(false, "Cancelled.");
                                }
                            });
                        BiometricPrompt prompt = builder.build();
                        CancellationSignal cancel = new CancellationSignal();
                        prompt.authenticate(new BiometricPrompt.CryptoObject(signature), cancel, executor, new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                                try {
                                    Signature signed = result.getCryptoObject().getSignature();
                                    signed.update(payload.getBytes("UTF-8"));
                                    signed.sign();
                                    tellPage(true, "Signed on this phone.");
                                } catch (Exception ex) {
                                    tellPage(false, "The signature did not complete.");
                                }
                            }
                            @Override
                            public void onAuthenticationError(int errorCode, CharSequence errString) {
                                tellPage(false, "The signature was not confirmed.");
                            }
                        });
                    } catch (Exception ex) {
                        tellPage(false, "The signature did not start.");
                    }
                }
            });
        }
    }
}
