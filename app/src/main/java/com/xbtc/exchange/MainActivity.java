package com.xbtc.exchange;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Offline marketplace stays unprivileged; signing happens only in
 * WalletActivity, which is a native, non-exported Android screen.
 */
public class MainActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    private WebView webView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        webView.getSettings().setBlockNetworkLoads(true);
        webView.setWebViewClient(new WebViewClient() {
            private boolean navigation(Uri uri) {
                if ("xbtc".equals(uri.getScheme()) && "wallet".equals(uri.getHost())) {
                    startActivity(new Intent(MainActivity.this, WalletActivity.class));
                    return true;
                }
                String url = uri.toString();
                // Allow this single local screen (and its fragments). Do not load
                // remote webpages or untrusted local documents inside the WebView.
                return !url.equals(PAGE) && !url.startsWith(PAGE + "#");
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return navigation(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return navigation(Uri.parse(url));
            }
        });
        setContentView(webView);
        webView.loadUrl(PAGE);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
