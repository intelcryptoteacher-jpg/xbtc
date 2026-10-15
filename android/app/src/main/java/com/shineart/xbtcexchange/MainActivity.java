package com.shineart.xbtcexchange;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;
import android.view.View;
import androidx.webkit.WebViewAssetLoader;
public class MainActivity extends Activity {
 private WebView web;
 private ValueCallback<Uri[]> chooser;
 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved);
  LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
  layout.setPadding(0,32,0,24); layout.setBackgroundColor(Color.rgb(7,9,14));
  TextView note=new TextView(this);
  note.setText("XBTC Exchange · Preview\nBalances and trades are simulations. No live custody or payments.");
  note.setTextColor(Color.rgb(251,191,36)); note.setTextSize(16); note.setPadding(16,12,16,12);
  layout.addView(note);
  web=new WebView(this); layout.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(layout);
  WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
  s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  final WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return loader.shouldInterceptRequest(r.getUrl());}
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){
    Uri u=r.getUrl(); if("https".equals(u.getScheme()) && "appassets.androidplatform.net".equals(u.getHost()))return false;
    if("https".equals(u.getScheme()))try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){Toast.makeText(MainActivity.this,"No browser available",Toast.LENGTH_SHORT).show();}
    return true;
   }
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p){
    if(chooser!=null)chooser.onReceiveValue(null); chooser=cb;
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
    try{startActivityForResult(i,7);}catch(Exception e){chooser.onReceiveValue(null);chooser=null;}
    return true;
   }
  });
  web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==7 && chooser!=null){chooser.onReceiveValue(result==RESULT_OK && data!=null && data.getData()!=null ? new Uri[]{data.getData()}:null);chooser=null;}
 }
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){if(chooser!=null)chooser.onReceiveValue(null);web.destroy();super.onDestroy();}
}
