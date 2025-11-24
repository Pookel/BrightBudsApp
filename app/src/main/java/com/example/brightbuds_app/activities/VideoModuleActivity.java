package com.example.brightbuds_app.activities;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.services.StorageService;
import com.example.brightbuds_app.services.DatabaseHelper;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;

public class VideoModuleActivity extends AppCompatActivity {

    private static final String TAG = "VideoModuleActivity";

    private VideoView videoView;
    private ImageButton btnPlay, btnPause, btnStop, btnHome, btnClose;

    private String storagePath;
    private String moduleId;
    private String moduleTitle;
    private String childId;

    private AnalyticsSessionManager analyticsManager;
    private ProgressService progressService;
    private long playbackStartMs = 0L;
    private boolean playbackStarted = false;
    private boolean analyticsEnded = false;

    private DatabaseHelper localDb;
    private boolean localAnalyticsRecorded = false;

    private Handler timerHandler;
    private Runnable timerRunnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_module);

        videoView = findViewById(R.id.videoViewSong);
        btnPlay = findViewById(R.id.btnVideoPlay);
        btnPause = findViewById(R.id.btnVideoPause);
        btnStop = findViewById(R.id.btnVideoStop);
        btnHome = findViewById(R.id.btnVideoHome);
        btnClose = findViewById(R.id.btnVideoClose);

        storagePath = getIntent().getStringExtra("storagePath");
        moduleId = getIntent().getStringExtra("moduleId");
        moduleTitle = getIntent().getStringExtra("moduleTitle");
        childId = getIntent().getStringExtra("childId");

        if (storagePath == null || storagePath.isEmpty()) {
            Toast.makeText(this, "Missing video path for this module.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "Loading video from: " + storagePath);

        progressService = new ProgressService(this);
        analyticsManager = new AnalyticsSessionManager(this, childId, moduleId);
        localDb = new DatabaseHelper(this);
        timerHandler = new Handler();

        loadVideoFromFirebase(storagePath);
        setupControls();
    }

    private void loadVideoFromFirebase(String path) {
        StorageService.getInstance().getOrDownloadFile(
                this,
                path,
                uri -> runOnUiThread(() -> setupVideoView(uri)),
                e -> {
                    Log.e(TAG, "Video download failed: " + e.getMessage());
                    runOnUiThread(() ->
                            Toast.makeText(this, "Video load error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
        );
    }

    private void setupVideoView(Uri uri) {
        videoView.setVideoURI(uri);

        videoView.setOnPreparedListener(mp -> {
        });

        videoView.setOnCompletionListener(mp -> {
            endAnalyticsIfNeeded();
        });
    }

    private void setupControls() {

        btnPlay.setOnClickListener(v -> {
            if (videoView == null) return;

            if (!playbackStarted) {
                playbackStarted = true;
                analyticsEnded = false;
                localAnalyticsRecorded = false;
                playbackStartMs = System.currentTimeMillis();
                analyticsManager.startSession();
                startTimer();
            }

            if (!videoView.isPlaying()) {
                videoView.start();
            }
        });

        btnPause.setOnClickListener(v -> {
            if (videoView != null && videoView.isPlaying()) {
                videoView.pause();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (videoView != null) {
                videoView.stopPlayback();
                videoView.resume();
            }
            endAnalyticsIfNeeded();
        });

        btnHome.setOnClickListener(v -> {
            endAnalyticsIfNeeded();
            finish();
        });

        btnClose.setOnClickListener(v -> {
            endAnalyticsIfNeeded();
            finish();
        });
    }

    private void startTimer() {
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    private void stopTimer() {
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void endAnalyticsIfNeeded() {
        if (!playbackStarted || analyticsEnded) return;

        long now = System.currentTimeMillis();
        long timeSpentMs = Math.max(0L, now - playbackStartMs);

        int durationMs = videoView != null ? videoView.getDuration() : 0;
        double fractionWatched = durationMs > 0 ? timeSpentMs / (double) durationMs : 0.0;

        boolean completedPlayback = fractionWatched >= 0.8;
        int score = completedPlayback ? 100 : 50;
        int stars = completedPlayback ? 1 : 0;
        int completedFlag = completedPlayback ? 1 : 0;

        analyticsManager.endSession(
                score,
                0,
                0,
                stars,
                completedFlag
        );

        saveLocalVideoAnalytics(playbackStartMs, now, score, stars, completedFlag);

        analyticsEnded = true;
        stopTimer();

        // ✅ FIXED CALL — now matches ProgressService signature
        if (childId != null && moduleId != null) {
            progressService.logVideoPlay(
                    childId,
                    moduleId,
                    score,
                    timeSpentMs,
                    stars,
                    completedFlag,
                    1, // plays
                    new DataCallbacks.GenericCallback() {
                        @Override
                        public void onSuccess(String result) {
                            Log.d(TAG, "Video progress saved.");
                        }

                        @Override
                        public void onFailure(Exception e) {
                            Log.e(TAG, "Failed to save video progress", e);
                        }
                    }
            );
        }
    }

    private void saveLocalVideoAnalytics(long startMs,
                                         long endMs,
                                         int score,
                                         int stars,
                                         int completedFlag) {

        if (localAnalyticsRecorded) return;
        if (childId == null || moduleId == null) return;
        if (startMs <= 0 || endMs <= startMs) return;

        long sessionTimeMs = endMs - startMs;

        try {
            localDb.recordGameSession(
                    childId,
                    moduleId,
                    startMs,
                    endMs,
                    score,
                    0,
                    0,
                    stars,
                    completedFlag
            );

            localAnalyticsRecorded = true;
            Log.d(TAG, "Local video analytics saved for " + moduleId);

        } catch (Exception e) {
            Log.e(TAG, "Error saving local video analytics", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
        endAnalyticsIfNeeded();
    }

    @Override
    protected void onDestroy() {
        endAnalyticsIfNeeded();
        super.onDestroy();
    }
}
