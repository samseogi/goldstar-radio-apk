package com.samseogi.goldstarradio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private static final String RADIO_URL = "https://samseogi.github.io/goldstar-radio/";

    // ★ 백그라운드에서도 WebView가 절대 잠들지 않게 속이는 특수 WebView 클래스
    private static class KeepAliveWebView extends WebView {
        public KeepAliveWebView(Context context) {
            super(context);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            // 앱이 백그라운드로 내려가서 GONE/INVISIBLE 신호가 와도 무조건 VISIBLE(화면 켜짐)로 강제 고정!
            super.onWindowVisibilityChanged(View.VISIBLE);
        }
    }

    // APK 안내 제거 + 전 채널 무음 해제 + HLS 백그라운드 대용량 버퍼링 패치
    private static final String PATCH_SCRIPT = "javascript:(function() {" +
            // 1. 우측 상단 'APK 안내' 버튼 숨김
            "if (!document.getElementById('apk-patch-style')) {" +
            "  var style = document.createElement('style');" +
            "  style.id = 'apk-patch-style';" +
            "  style.innerHTML = 'button[onclick*=\"apk\"], .apk-btn, #apk-btn, #apkModal, [id*=\"apk\"], [class*=\"apk\"] { display: none !important; }';" +
            "  document.head.appendChild(style);" +
            "}" +
            "var els = document.querySelectorAll('button, a, div, span');" +
            "for (var i = 0; i < els.length; i++) {" +
            "  if (els[i].innerText && els[i].innerText.trim().indexOf('APK 안내') !== -1 && els[i].innerText.length < 25) {" +
            "    els[i].style.setProperty('display', 'none', 'important');" +
            "  }" +
            "}" +
            // 2. 페이지 가시성 API(visibilitychange)가 백그라운드 전환을 감지하지 못하게 차단
            "try {" +
            "  Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });" +
            "  Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });" +
            "} catch(e) {}" +
            // 3. Web Audio API (AudioContext) CORS 무음 현상 무력화
            "var AC = window.AudioContext || window.webkitAudioContext;" +
            "if (AC && !AC.prototype._patched) {" +
            "  AC.prototype._patched = true;" +
            "  AC.prototype.createMediaElementSource = function(el) {" +
            "    if (el && el.removeAttribute) el.removeAttribute('crossOrigin');" +
            "    return { connect: function() { return this; }, disconnect: function() {} };" +
            "  };" +
            "}" +
            // 4. <audio> crossOrigin 차단 해제
            "try {" +
            "  Object.defineProperty(HTMLMediaElement.prototype, 'crossOrigin', {" +
            "    get: function() { return null; }," +
            "    set: function() { this.removeAttribute('crossorigin'); }," +
            "    configurable: true" +
            "  });" +
            "} catch(e) {}" +
            "var audios = document.querySelectorAll('audio');" +
            "for (var j = 0; j < audios.length; j++) {" +
            "  audios[j].removeAttribute('crossorigin');" +
            "  audios[j].removeAttribute('crossOrigin');" +
            "}" +
            // 5. HLS.js 백그라운드 끊김 방지를 위해 버퍼 길이 확장 & URL 보정
            "if (window.Hls && Hls.prototype && !Hls.prototype._urlPatched) {" +
            "  Hls.prototype._urlPatched = true;" +
            "  var origLoadSource = Hls.prototype.loadSource;" +
            "  Hls.prototype.loadSource = function(url) {" +
            "    if (this.config) {" +
            "      this.config.maxBufferLength = 60;" +
            "      this.config.maxMaxBufferLength = 120;" +
            "      this.config.enableWorker = true;" +
            "    }" +
            "    if (typeof url === 'string') {" +
            "      url = url.replace('https://radio.bsod.kr/stream?stn=', 'https://radio.bsod.kr/stream/?stn=');" +
            "    }" +
            "    return origLoadSource.call(this, url);" +
            "  };" +
            "}" +
            "})();";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(Color.parseColor("#1A1412"));
            window.setNavigationBarColor(Color.parseColor("#1A1412"));
        }

        // 1. 백그라운드 끊김 방지 포그라운드 서비스 시작
        startRadioService();

        // 2. 안드로이드 배터리 최적화(절전 모드) 예외 권한 요청
        requestIgnoreBatteryOptimizations();

        // 3. 잠들지 않는 KeepAliveWebView 장착
        webView = new KeepAliveWebView(this);
        webView.setBackgroundColor(Color.parseColor("#1A1412"));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                view.evaluateJavascript(PATCH_SCRIPT, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(PATCH_SCRIPT, null);
            }
        });

        webView.loadUrl(RADIO_URL);
    }

    private void startRadioService() {
        try {
            Intent serviceIntent = new Intent(this, RadioService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("BatteryLife")
    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ★ 중요: 앱이 백그라운드로 내려가도 webView.onPause()나 pauseTimers()를 절대 호출하지 않고 계속 깨어있게 유지!
        if (webView != null) {
            webView.resumeTimers();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, RadioService.class));
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
