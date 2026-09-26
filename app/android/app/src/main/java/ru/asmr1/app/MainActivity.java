package ru.asmr1.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private ActivityResultLauncher<Intent> pipLauncher;

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pipLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {}
        );
    }

    @Override
    public void onStart() {
        super.onStart();
        WebView webView = this.bridge.getWebView();
        if (webView != null) {
            webView.addJavascriptInterface(new AndroidPlayerBridge(), "AndroidPlayer");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        SharedPreferences prefs = getSharedPreferences(PipPlayerActivity.PREFS_NAME, Context.MODE_PRIVATE);
        long savedTime = prefs.getLong(PipPlayerActivity.KEY_RETURN_TIME_MS, -1L);
        long savedTimestamp = prefs.getLong(PipPlayerActivity.KEY_RETURN_TIMESTAMP, 0L);
        long now = System.currentTimeMillis();
        boolean isFresh = savedTimestamp > 0 && (now - savedTimestamp) < 10_000;

        if (isFresh && savedTime >= 0) {
            double timeSec = savedTime / 1000.0;
            WebView webView = this.bridge.getWebView();
            if (webView != null) {
                final double finalTime = timeSec;
                webView.post(() -> {
                    String js = "window.dispatchEvent(new CustomEvent('playerReturn', { detail: { time: " + finalTime + " } }));";
                    webView.evaluateJavascript(js, null);
                });
            }
            prefs.edit().remove(PipPlayerActivity.KEY_RETURN_TIME_MS).remove(PipPlayerActivity.KEY_RETURN_TIMESTAMP).apply();
        } else if (savedTimestamp > 0) {
            prefs.edit().remove(PipPlayerActivity.KEY_RETURN_TIME_MS).remove(PipPlayerActivity.KEY_RETURN_TIMESTAMP).apply();
        }
    }

    /**
     * JS-мост: window.AndroidPlayer
     */
    public class AndroidPlayerBridge {

        /**
         * Открыть нативный плеер.
         */
        @JavascriptInterface
        public boolean open(String url, double timeSec, String title, String author) {
            try {
                Intent intent = new Intent(MainActivity.this, PipPlayerActivity.class);
                intent.putExtra(PipPlayerActivity.EXTRA_VIDEO_URL, url);
                intent.putExtra(PipPlayerActivity.EXTRA_CURRENT_TIME, (long)(timeSec * 1000));
                intent.putExtra(PipPlayerActivity.EXTRA_TITLE, title);
                intent.putExtra(PipPlayerActivity.EXTRA_AUTHOR, author);
                pipLauncher.launch(intent);
                System.out.println("✅ Запуск нативного плеера: " + url);
                return true;
            } catch (Exception e) {
                System.out.println("❌ Ошибка: " + e.getMessage());
                return false;
            }
        }

        @JavascriptInterface
        public boolean isSupported() {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        }
    }
}
