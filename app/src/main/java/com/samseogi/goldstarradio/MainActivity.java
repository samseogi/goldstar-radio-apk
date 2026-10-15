package com.samseogi.goldstarradio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
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

    // 페이지 로드 전/후로 주입할 패치 스크립트 (APK 안내 제거 + 전 채널 무음 해제 + URL 정규화)
    private static final String PATCH_SCRIPT = "javascript:(function() {" +
            // 1. 우측 상단 'APK 안내' 버튼 및 모달 CSS로 원천 차단
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
            // 2. Web Audio API (AudioContext)가 스트림을 가로채서 무음 만드는 현상(CORS 차단) 무력화
            "var AC = window.AudioContext || window.webkitAudioContext;" +
            "if (AC && !AC.prototype._patched) {" +
            "  AC.prototype._patched = true;" +
            "  AC.prototype.createMediaElementSource = function(el) {" +
            "    if (el && el.removeAttribute) el.removeAttribute('crossOrigin');" +
            "    return {" +
            "      connect: function() { return this; }," +
            "      disconnect: function() {}" +
            "    };" +
            "  };" +
            "}" +
            // 3. 기존 및 신규 <audio> 태그에서 crossOrigin 속성을 강제로 막아 CBS/SBS/지역방송 차단 해제
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
            // 4. radio.bsod.kr 스트림 URL 슬래시(/) 누락으로 인한 리다이렉트 실패 자동 보정
            "if (window.Hls && Hls.prototype && !Hls.prototype._urlPatched) {" +
            "  Hls.prototype._urlPatched = true;" +
            "  var origLoadSource = Hls.prototype.loadSource;" +
            "  Hls.prototype.loadSource = function(url) {" +
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

        webView = new WebView(this);
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

        // 파일 및 크로스 도메인 스트리밍 접근 제한 해제
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        // 데스크톱 브라우저로 인식시켜 모바일 스트림 제한 우회
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
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}