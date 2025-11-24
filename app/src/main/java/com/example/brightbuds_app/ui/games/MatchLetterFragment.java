package com.example.brightbuds_app.ui.games;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color; // <-- YOU ADDED THIS
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.brightbuds_app.R;
import com.example.brightbuds_app.adapters.MatchLetterAdapter;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;
import com.example.brightbuds_app.utils.Constants;
import com.example.brightbuds_app.utils.CurrentChildManager;
import com.example.brightbuds_app.utils.ModuleIds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * MatchLetterFragment
 *
 * Child taps the matching letter in colored circles to match the big center letter.
 * Tracks metrics for:
 *  - Correct answers
 *  - Attempts
 *  - Incorrect answers
 *  - Stars earned
 *  - Time per session
 *  - Times played
 */
public class MatchLetterFragment extends Fragment implements MatchLetterAdapter.OnOptionClickListener {

    private static final String TAG = "MatchLetterFragment";

    // Shared prefs keys
    private static final String PREFS = "brightbuds_match_letter_prefs";
    private static final String KEY_TIMES_PLAYED = "match_letter_times_played";

    // UI
    private TextView tvTargetLetter;
    private TextView tvScore;
    private TextView tvAttempts;
    private TextView tvIncorrect;
    private TextView tvStars;
    private TextView tvTimer;
    private ImageView imgCharacter;
    private ImageView btnHome;
    private ImageView btnClose;
    private RecyclerView rvOptions;

    private MatchLetterAdapter adapter;
    private final List<String> optionLetters = new ArrayList<>();
    private final List<Integer> optionBackgrounds = new ArrayList<>();

    // Game state
    private final Random rng = new Random();
    private char currentTargetLetter = 'A';
    private int correctAnswers = 0;
    private int attempts = 0;
    private int incorrectAnswers = 0;
    private int stars = 0;
    private int score = 0;
    private int wrongStreak = 0;
    private int timesPlayed = 0;

    // Timer
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private boolean timerRunning = false;
    private long sessionStartMs = 0L;
    private long elapsedMs = 0L;

    // Audio
    private MediaPlayer bgMusic;
    private TextToSpeech tts;

    // Progress + child link
    private ProgressService progressService;
    private String selectedChildId;

    // Local analytics
    private AnalyticsSessionManager analyticsManager;
    private String analyticsChildId;
    private boolean analyticsSaved = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = requireContext();

        progressService = new ProgressService(context);

        // Child that is currently selected for analytics
        analyticsChildId = CurrentChildManager.getCurrentChildId(context);

        SharedPreferences parentPrefs =
                context.getSharedPreferences("BrightBudsPrefs", Context.MODE_PRIVATE);
        selectedChildId = parentPrefs.getString("selectedChildId", null);

        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        timesPlayed = sp.getInt(KEY_TIMES_PLAYED, 0) + 1;
        sp.edit().putInt(KEY_TIMES_PLAYED, timesPlayed).apply();

        // Init TTS
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ENGLISH);
                tts.setPitch(1.1f);
                tts.setSpeechRate(0.95f);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_match_letter, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        tvTargetLetter = view.findViewById(R.id.tvTargetLetter);
        tvScore = view.findViewById(R.id.tvMLScore);
        tvAttempts = view.findViewById(R.id.tvMLAttempts);
        tvIncorrect = view.findViewById(R.id.tvMLIncorrect);
        tvStars = view.findViewById(R.id.tvMLStars);
        tvTimer = view.findViewById(R.id.tvMLTimer);
        imgCharacter = view.findViewById(R.id.imgCharacter);
        btnHome = view.findViewById(R.id.btnHomeIcon);
        btnClose = view.findViewById(R.id.btnCloseIcon);
        rvOptions = view.findViewById(R.id.rvLetterOptions);

        // Start analytics session for Match Letter
        analyticsManager = new AnalyticsSessionManager(
                requireContext(),
                analyticsChildId,
                ModuleIds.MODULE_MATCH_LETTER
        );
        analyticsManager.startSession();

        // Character default
        Glide.with(this)
                .load(R.drawable.character_default)
                .into(imgCharacter);

        // Grid for letter options (still 3 columns)
        rvOptions.setLayoutManager(new GridLayoutManager(getContext(), 3));

        adapter = new MatchLetterAdapter(optionLetters, optionBackgrounds, this);
        rvOptions.setAdapter(adapter);

        // Background music
        try {
            bgMusic = MediaPlayer.create(getContext(), R.raw.happy_children);
            if (bgMusic != null) {
                bgMusic.setLooping(true);
                bgMusic.setVolume(0.4f, 0.4f);
                bgMusic.start();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error starting match letter bg music", ex);
        }

        // Navigation exit
        View.OnClickListener exitClick = v -> {
            endAnalyticsSession();
            finishGame();
        };
        btnHome.setOnClickListener(exitClick);
        btnClose.setOnClickListener(exitClick);

        resetSession();
        startNewRound();
        startTimer();
    }

    private void resetSession() {
        correctAnswers = 0;
        attempts = 0;
        incorrectAnswers = 0;
        stars = 0;
        score = 0;
        wrongStreak = 0;
        elapsedMs = 0L;
        sessionStartMs = SystemClock.elapsedRealtime();
        analyticsSaved = false;
        updateHud();
    }

    private void startNewRound() {
        wrongStreak = 0;

        // -------------------------------------------------
        // YOU ADDED THIS: Reset letter color to dark teal
        // (#043B49) at the start of every new round
        // -------------------------------------------------
        if (tvTargetLetter != null) {
            tvTargetLetter.setTextColor(Color.parseColor("#043B49"));
        }
        // -------------------------------------------------

        currentTargetLetter = (char) ('A' + rng.nextInt(26));
        tvTargetLetter.setText(String.valueOf(currentTargetLetter));

        speak("Find the letter " + currentTargetLetter);

        optionLetters.clear();
        optionBackgrounds.clear();

        optionLetters.add(String.valueOf(currentTargetLetter));

        while (optionLetters.size() < 5) {
            char candidate = (char) ('A' + rng.nextInt(26));
            String s = String.valueOf(candidate);
            if (!optionLetters.contains(s)) {
                optionLetters.add(s);
            }
        }

        Collections.shuffle(optionLetters, rng);

        int[] circleDrawables = new int[]{
                R.drawable.circle_red,
                R.drawable.circle_orange,
                R.drawable.circle_yellow,
                R.drawable.circle_green,
                R.drawable.circle_purple
        };

        for (int i = 0; i < optionLetters.size(); i++) {
            optionBackgrounds.add(circleDrawables[i % circleDrawables.length]);
        }

        adapter.notifyDataSetChanged();
    }

    @Override
    public void onOptionClicked(int position) {
        if (position < 0 || position >= optionLetters.size()) {
            return;
        }

        String chosen = optionLetters.get(position);
        attempts++;

        if (chosen.equalsIgnoreCase(String.valueOf(currentTargetLetter))) {
            handleCorrectTap(position);
        } else {
            handleIncorrectTap(position);
        }
    }

    private void handleCorrectTap(int position) {
        correctAnswers++;
        score += 10;
        stars++;
        wrongStreak = 0;

        // -------------------------------------------------
        // YOU ADDED THIS: Change middle letter to BLUE
        // when the child selects correctly.
        // -------------------------------------------------
        if (tvTargetLetter != null) {
            tvTargetLetter.setTextColor(Color.BLUE);
        }
        // -------------------------------------------------

        speak("Well done");
        showHappyCharacter();
        pulse(tvTargetLetter);
        adapter.markCorrect(position);

        updateHud();

        new Handler().postDelayed(this::startNewRound, 900);
    }

    private void handleIncorrectTap(int position) {
        incorrectAnswers++;
        wrongStreak++;

        speak("Try again");
        showSadCharacter();
        adapter.markIncorrect(position);

        updateHud();

        if (wrongStreak >= 3) {
            wrongStreak = 0;
            pulse(tvTargetLetter);
        }
    }

    private void updateHud() {
        tvScore.setText("Score: " + score);
        tvAttempts.setText("Attempts: " + attempts);
        tvIncorrect.setText("Incorrect: " + incorrectAnswers);
        tvStars.setText("Stars: " + stars);
        tvTimer.setText("Time: " + Constants.formatTime(elapsedMs));
    }

    private void startTimer() {
        timerRunning = true;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!timerRunning) return;
                elapsedMs = SystemClock.elapsedRealtime() - sessionStartMs;
                tvTimer.setText("Time: " + Constants.formatTime(elapsedMs));
                timerHandler.postDelayed(this, Constants.ONE_SECOND_MS);
            }
        };
        timerHandler.postDelayed(timerRunnable, Constants.ONE_SECOND_MS);
    }

    private void stopTimer() {
        timerRunning = false;
        timerHandler.removeCallbacksAndMessages(null);
    }

    private void saveSessionMetricsToFirestore(long timeSpentMs) {
        if (selectedChildId == null) {
            return;
        }

        int totalAttempts = attempts;
        int totalCorrect = correctAnswers;
        int starsTotal = stars;
        int completedFlag = attempts > 0 ? 1 : 0;

        progressService.recordGameSession(
                selectedChildId,
                ModuleIds.MODULE_MATCH_LETTER,
                score,
                timeSpentMs,
                starsTotal,
                totalCorrect,
                incorrectAnswers,
                timesPlayed,
                new DataCallbacks.GenericCallback() {
                    @Override
                    public void onSuccess(String result) {
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w(TAG, "Failed to save match-letter metrics", e);
                    }
                }
        );
    }

    private void speak(String text) {
        if (tts == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "match_letter_tts");
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private void showHappyCharacter() {
        if (imgCharacter == null) return;
        Glide.with(this)
                .load(R.drawable.character_happy)
                .into(imgCharacter);
    }

    private void showSadCharacter() {
        if (imgCharacter == null) return;
        Glide.with(this)
                .load(R.drawable.character_sad)
                .into(imgCharacter);
    }

    private void finishGame() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void pulse(View v) {
        ObjectAnimator upX = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.1f);
        ObjectAnimator upY = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.1f);
        upX.setDuration(160);
        upY.setDuration(160);
        upX.start();
        upY.start();

        v.postDelayed(() -> {
            ObjectAnimator dx = ObjectAnimator.ofFloat(v, View.SCALE_X, 1.1f, 1f);
            ObjectAnimator dy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1.1f, 1f);
            dx.setDuration(160);
            dy.setDuration(160);
            dx.start();
            dy.start();
        }, 180);
    }

    private void stopAudioAndTts() {
        if (bgMusic != null) {
            try {
                if (bgMusic.isPlaying()) {
                    bgMusic.stop();
                }
            } catch (Exception ignored) {
            }
            bgMusic.release();
            bgMusic = null;
        }
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {
            }
            tts.shutdown();
            tts = null;
        }
    }

    private void endAnalyticsSession() {
        if (analyticsManager == null || analyticsSaved) {
            return;
        }

        int totalAttempts = attempts;
        int totalCorrect = correctAnswers;
        int starsTotal = stars;
        int completedFlag = attempts > 0 ? 1 : 0;

        long durationMs = elapsedMs;
        if (durationMs <= 0L && sessionStartMs > 0L) {
            durationMs = SystemClock.elapsedRealtime() - sessionStartMs;
        }

        analyticsManager.endSession(
                score,
                totalCorrect,
                totalAttempts,
                starsTotal,
                completedFlag
        );
        analyticsManager = null;

        saveSessionMetricsToFirestore(durationMs);

        analyticsSaved = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        stopTimer();
        if (bgMusic != null && bgMusic.isPlaying()) {
            bgMusic.pause();
        }
        endAnalyticsSession();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bgMusic != null && !bgMusic.isPlaying()) {
            bgMusic.start();
        }
        if (!timerRunning) {
            sessionStartMs = SystemClock.elapsedRealtime() - elapsedMs;
            startTimer();
        }
    }

    @Override
    public void onDestroyView() {
        stopTimer();
        stopAudioAndTts();
        endAnalyticsSession();
        super.onDestroyView();
    }
}
