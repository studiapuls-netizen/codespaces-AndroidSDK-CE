package ru.asmr1.app;

import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/**
 * ============================================================
 * НАТИВНЫЙ ПЛЕЕР
 * 
 * - Fullscreen ExoPlayer
 * - Автоматический PiP через setAutoEnterEnabled(true)
 * - При жесте Home → система сама сворачивает в PiP
 * - Кнопка "Закрыть" → возврат в MainActivity
 * - При закрытии сохраняет currentTime в SharedPreferences
 * ============================================================
 */
public class PipPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_URL = "video_url";
    public static final String EXTRA_CURRENT_TIME = "current_time";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_AUTHOR = "author";

    public static final String PREFS_NAME = "pip_state";
    public static final String KEY_RETURN_TIME_MS = "pip_return_time";
    public static final String KEY_RETURN_TIMESTAMP = "pip_return_timestamp";

    private ExoPlayer player;
    private PlayerView playerView;
    private boolean resultSaved = false;
    private long startPositionMs = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pip_player);

        // Fullscreen
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        Intent intent = getIntent();
        String videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL);
        startPositionMs = intent.getLongExtra(EXTRA_CURRENT_TIME, 0L);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String author = intent.getStringExtra(EXTRA_AUTHOR);

        // Кнопка "Закрыть"
        ImageButton closeBtn = findViewById(R.id.btn_close);
        if (closeBtn != null) {
            closeBtn.setOnClickListener(v -> saveAndFinish(true));
        }

        // Заголовок (можно отобразить)
        TextView titleView = findViewById(R.id.pip_title);
        if (titleView != null) {
            titleView.setText(title != null ? title : "");
        }

        // ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.pip_player_view);
        playerView.setPlayer(player);

        if (videoUrl != null && !videoUrl.isEmpty()) {
            MediaItem mediaItem = MediaItem.fromUri(videoUrl);
            player.setMediaItem(mediaItem, startPositionMs);
            player.setPlayWhenReady(true);
            player.prepare();
        }

        // ★ ВКЛЮЧАЕМ АВТОМАТИЧЕСКИЙ PiP (Android 12+)
        enableAutoPip();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    saveAndFinish(true);
                }
            }
        });
    }

    /**
     * ★ ГЛАВНОЕ: включаем авто-PiP.
     * 
     * setAutoEnterEnabled(true) — система сама переведёт Активность в PiP
     * при жесте Home. Не нужны onPause/onStop/onUserLeaveHint.
     * 
     * Работает на HyperOS, MIUI, OneUI, обычном Android 12+.
     */
    private void enableAutoPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {  // Android 12+
            try {
                PictureInPictureParams.Builder paramsBuilder = new PictureInPictureParams.Builder();
                paramsBuilder.setAspectRatio(new Rational(16, 9));
                paramsBuilder.setAutoEnterEnabled(true);   // ★ ГЛАВНАЯ МАГИЯ
                paramsBuilder.setSeamlessResizeEnabled(true);
                setPictureInPictureParams(paramsBuilder.build());
                System.out.println("✅ Auto-PiP включён (setAutoEnterEnabled)");
            } catch (Exception e) {
                System.out.println("❌ Ошибка авто-PiP: " + e.getMessage());
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8-11: PiP только через enterPictureInPictureMode()
            // Авто-режим недоступен
            System.out.println("⚠️ Android < 12 — авто-PiP недоступен");
        }
    }

    /**
     * Сохраняем время в SharedPreferences и завершаем.
     */
    private void saveAndFinish(boolean openMainApp) {
        if (resultSaved) return;
        resultSaved = true;

        long currentMs = 0;
        if (player != null) {
            currentMs = player.getCurrentPosition();
            try {
                player.setPlayWhenReady(false);
                player.pause();
                player.stop();
            } catch (Exception e) {}
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putLong(KEY_RETURN_TIME_MS, currentMs)
            .putLong(KEY_RETURN_TIMESTAMP, System.currentTimeMillis())
            .apply();

        System.out.println("💾 Сохранил " + (currentMs / 1000.0) + " сек");

        if (openMainApp) {
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(mainIntent);
        }

        finish();
    }

    /**
     * Обработка изменения режима PiP.
     * Просто для логов и на случай закрытия из PiP.
     */
    @Override
    public void onPictureInPictureModeChanged(boolean isInPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPip, newConfig);
        System.out.println("📺 PiP mode = " + isInPip);
    }

    @Override
    public void onBackPressed() {
        saveAndFinish(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!resultSaved) {
            long currentMs = 0;
            if (player != null) {
                currentMs = player.getCurrentPosition();
            }
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                .putLong(KEY_RETURN_TIME_MS, currentMs)
                .putLong(KEY_RETURN_TIMESTAMP, System.currentTimeMillis())
                .apply();
        }

        if (player != null) {
            try {
                player.setPlayWhenReady(false);
                player.stop();
                player.release();
            } catch (Exception e) {}
            player = null;
        }
    }
}
