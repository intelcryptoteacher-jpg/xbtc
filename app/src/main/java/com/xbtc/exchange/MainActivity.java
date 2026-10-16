package com.xbtc.exchange;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private WebView webView;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread miningThread;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equals(uri.getScheme()) && "android_asset".equals(uri.getHost())) {
                    return false;
                }
                if ("https".equals(uri.getScheme()) && "explorer.solana.com".equals(uri.getHost())) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
                // Prevent arbitrary network pages from receiving the local JavaScript bridge.
                return true;
            }
        });
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        webView.addJavascriptInterface(new MiningBridge(), "XbtcPhoneWork");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void report(String state, long hashes, String proof) {
        if (webView == null) return;
        final String script = "window.xbtcMiningUpdate && window.xbtcMiningUpdate("
                + JSONObject.quote(state) + "," + hashes + "," + JSONObject.quote(proof) + ");";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    private final class MiningBridge {
        @JavascriptInterface public void start() {
            synchronized (MainActivity.this) {
                if (running.get() || (miningThread != null && miningThread.isAlive())) {
                    report("A proof-of-work test is already running.", 0, "");
                    return;
                }
                BatteryManager power = (BatteryManager) getSystemService(BATTERY_SERVICE);
                int percent = power == null ? -1 : power.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (percent >= 0 && percent < 25) {
                    report("Battery under 25%. Charge your phone before testing.", 0, "");
                    return;
                }
                running.set(true);
                miningThread = new Thread(MainActivity.this::runLimitedWork, "XBTC-preview-work");
                miningThread.start();
            }
        }
        @JavascriptInterface public void stop() {
            running.set(false);
        }
    }

    /**
     * Non-network, capped educational SHA-256 proof-of-work experiment.
     * Not compatible with Bitcoin or any XBTC chain; awards no coins.
     * One foreground thread, < 30 seconds, sleeps between bounded batches.
     */
    private void runLimitedWork() {
        long attempts = 0;
        long began = SystemClock.elapsedRealtime();
        String challenge = "XBTC-PHONE-EXPERIMENT-v1|" + UUID.randomUUID();
        String firstProof = "";
        boolean stopped = false;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            report("Testing SHA-256 proofs on this phone. No tokens or payments.", 0, "");
            while (running.get() && SystemClock.elapsedRealtime() - began < 30000) {
                for (int j = 0; j < 250 && running.get(); j++) {
                    byte[] digest = sha.digest((challenge + "|" + attempts).getBytes(StandardCharsets.UTF_8));
                    // A local 12-leading-zero-bit target, not any actual chain difficulty.
                    if (firstProof.isEmpty() && digest[0] == 0 && (digest[1] & 0xf0) == 0) {
                        StringBuilder hex = new StringBuilder(64);
                        for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
                        firstProof = "nonce=" + attempts + " sha256=" + hex;
                    }
                    attempts++;
                }
                if (attempts % 1250 == 0) {
                    report("Running: capped local test (max 30 seconds).", attempts, firstProof);
                }
                Thread.sleep(50);
            }
            stopped = !running.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopped = true;
        } catch (Exception e) {
            report("Test error: " + e.getClass().getSimpleName(), attempts, "");
        } finally {
            running.set(false);
            report(stopped ? "Stopped. No XBTC earned." : "Local test finished. No XBTC earned.",
                    attempts, firstProof);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        running.set(false); // no background mining
    }
    @Override protected void onDestroy() {
        running.set(false);
        if (webView != null) {
            webView.removeJavascriptInterface("XbtcPhoneWork");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
