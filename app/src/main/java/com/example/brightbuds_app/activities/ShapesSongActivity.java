package com.example.brightbuds_app.activities;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.widget.ImageButton;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;
import com.example.brightbuds_app.utils.ModuleIds;

import java.util.Locale;

public class ShapesSongActivity extends AppCompatActivity {

    private VideoView videoView;
    private ImageButton btnPlay, btnPause, btnStop, btnHome, btnClose;

    private ProgressService progressService;

    private String childId;
    private String moduleId;

    private long sessionStartMs = 0L;
    private boolean sessionStarted = false;
    private boolean metricsSaved = false;

    private TextToSpeech tts;
    private AnalyticsSessionManager analyticsManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_module);

        progressService = new ProgressService(this);

        childId = getIntent().getStringExtra("child_id");
        moduleId = ModuleIds.MODULE_SHAPES_SONG;

        analyticsManager = new AnalyticsSessionManager(this, childId, moduleId);

        videoView = findViewById(R.id.videoViewSong);
        btnPlay = findViewById(R.id.btnVideoPlay);
        btnPause = findViewById(R.id.btnVideoPause);
        btnStop = findViewById(R.id.btnVideoStop);
        btnHome = findViewById(R.id.btnVideoHome);
        btnClose = findViewById(R.id.btnVideoClose);

        setupVideo(R.raw.shapes_song);
        setupButtons();
        setupTts();
    }

    private void setupVideo(int rawResId) {
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + rawResId);
        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            // Wait for child to press Play
        });
    }

    private void setupButtons() {
        btnPlay.setOnClickListener(v -> {
            if (!sessionStarted) {
                sessionStarted = true;
                sessionStartMs = SystemClock.elapsedRealtime();
                analyticsManager.startSession();
            }

            if (!videoView.isPlaying()) {
                videoView.start();
            }
        });

        btnPause.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.pause();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (videoView.isPlaying()) {
                videoView.stopPlayback();
            }
            setupVideo(R.raw.shapes_song);
            saveMetricsIfNeeded();
        });

        btnHome.setOnClickListener(v -> {
            saveMetricsIfNeeded();
            finish();
        });

        btnClose.setOnClickListener(v -> {
            saveMetricsIfNeeded();
            finish();
        });
    }

    private void setupTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.speak("Let us sing the shapes song",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "SHAPES_INTRO");
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) {
            videoView.pause();
        }
        saveMetricsIfNeeded();
    }

    @Override
    protected void onDestroy() {
        saveMetricsIfNeeded();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private void saveMetricsIfNeeded() {
        if (metricsSaved) return;
        if (!sessionStarted) return;                  // child never pressed play
        if (childId == null || moduleId == null) return;

        metricsSaved = true;

        long endMs = SystemClock.elapsedRealtime();
        long timeSpentMs = Math.max(0L, endMs - sessionStartMs);

        int durationMs = videoView != null ? videoView.getDuration() : 0;
        double fractionWatched = 0.0;
        if (durationMs > 0) {
            fractionWatched = timeSpentMs / (double) durationMs;
        }

        boolean completed = fractionWatched >= 0.8;
        int score = completed ? 100 : 50;
        int stars = completed ? 1 : 0;
        int completedFlag = completed ? 1 : 0;
        int plays = 1;   // one viewing session for this activity run

        // Detailed analytics to SQLite through AnalyticsSessionManager
        analyticsManager.endSession(
                score,
                0,          // totalCorrect (no quiz for songs)
                0,          // totalAttempts
                stars,
                completedFlag
        );

        // Summary to Firestore with full song metrics
        progressService.logVideoPlay(
                childId,
                moduleId,
                score,
                timeSpentMs,
                stars,
                completedFlag,
                plays,
                new DataCallbacks.GenericCallback() {
                    @Override public void onSuccess(String message) { }

                    @Override public void onFailure(Exception e) { }
                }
        );
    }

}
