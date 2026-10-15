package com.shineart.xbtcexchange;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.webkit.*;
import android.widget.*;
import android.view.View;
import java.io.ByteArrayInputStream;
public class MainActivity extends Activity {
 private WebView web;
 private static final String HOME="https://appassets.androidplatform.net/index.html";
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
  root.setBackgroundColor(Color.rgb(7,9,14));
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  TextView banner=new TextView(this); banner.setText("XBTC · DEMO\nSimulatie — geen echte handel of mining"); banner.setTextSize(18); banner.setTextColor(Color.rgb(251,191,36)); banner.setPadding(16,12,16,12);root.addView(banner);
  Button reload=new Button(this); reload.setText("Opnieuw laden"); reload.setOnClickListener(v->web.loadUrl(HOME));root.addView(reload);
  web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setTextZoom(120);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
    if("appassets.androidplatform.net".equals(request.getUrl().getHost())){
     if("/index.html".equals(request.getUrl().getPath())){try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));}catch(Exception e){}}
     return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",null,new ByteArrayInputStream(new byte[0]));
    }return null;
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    Uri uri=request.getUrl();
    if(HOME.equals(uri.toString()))return false;
    if("https".equals(uri.getScheme())){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){Toast.makeText(MainActivity.this,"Geen browser beschikbaar",Toast.LENGTH_LONG).show();}}
    return true;
   }
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
    if(request.isForMainFrame())Toast.makeText(MainActivity.this,"Laden mislukt. Tik op Opnieuw laden.",Toast.LENGTH_LONG).show();
   }
  });
  web.loadUrl(HOME);
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
