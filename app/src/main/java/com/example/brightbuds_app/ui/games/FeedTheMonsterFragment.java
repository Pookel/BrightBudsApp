package com.example.brightbuds_app.ui.games;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;
import com.example.brightbuds_app.utils.CurrentChildManager;
import com.example.brightbuds_app.utils.ModuleIds;

import java.util.Locale;
import java.util.Random;

/**
 * FeedTheMonsterFragment
 *
 * Educational mini game where the child drags cookies into the monster mouth
 * to match the displayed target number.
 */
public class FeedTheMonsterFragment extends Fragment {

    // UI elements
    private ImageView bgImage;
    private ImageView imgMonster;
    private ImageView imgStar;
    private ImageView imgTargetNumber;
    private TextView tvScore;
    private TextView tvRound;
    private TextView tvTarget;
    private TextView tvStats;
    private ProgressBar progressRound;
    private FrameLayout playArea;
    private ImageButton btnHomeIcon;
    private ImageButton btnCloseIcon;

    // Game state
    private final Random rng = new Random();
    private int score = 0;
    private int round = 1;
    private int targetNumber = 5;
    private int totalCorrect = 0;
    private int totalIncorrect = 0;
    private int wrongStreak = 0;
    private int stars = 0;
    private int cookiesFedThisRound = 0;
    private boolean roundLocked = false;

    // Session tracking
    private long sessionStartMs = 0L;
    private int sessionRounds = 0;
    private int timesPlayed;

    // Shared preferences keys
    private static final String PREFS = "brightbuds_game_prefs";
    private static final String KEY_TIMES_PLAYED = "feed_monster_times_played";

    // Audio
    private MediaPlayer bgMusic;
    private TextToSpeech tts;

    // Analytics
    private AnalyticsSessionManager analyticsManager;
    private ProgressService progressService;
    private String childId;
    private boolean analyticsSaved = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed_the_monster, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        // Bind UI components
        bgImage = v.findViewById(R.id.bgImage);
        imgMonster = v.findViewById(R.id.imgMonster);
        imgStar = v.findViewById(R.id.imgStar);
        imgTargetNumber = v.findViewById(R.id.imgTargetNumber);
        tvScore = v.findViewById(R.id.tvScore);
        tvRound = v.findViewById(R.id.tvRound);
        tvTarget = v.findViewById(R.id.tvTarget);
        tvStats = v.findViewById(R.id.tvStats);
        progressRound = v.findViewById(R.id.progressRound);
        playArea = v.findViewById(R.id.playArea);
        btnHomeIcon = v.findViewById(R.id.btnHomeIcon);
        btnCloseIcon = v.findViewById(R.id.btnCloseIcon);

        // Child id
        childId = CurrentChildManager.getCurrentChildId(requireContext());

        // Analytics
        progressService = new ProgressService(requireContext());
        analyticsManager = new AnalyticsSessionManager(
                requireContext(),
                childId,
                ModuleIds.MODULE_FEED_MONSTER
        );
        analyticsManager.startSession();

        // Session start time
        sessionStartMs = SystemClock.elapsedRealtime();

        // Persistent play tracking for "times played" metric
        SharedPreferences sp = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        timesPlayed = sp.getInt(KEY_TIMES_PLAYED, 0) + 1;
        sp.edit().putInt(KEY_TIMES_PLAYED, timesPlayed).apply();

        // Background music setup
        bgMusic = MediaPlayer.create(requireContext(), R.raw.monster_music);
        if (bgMusic != null) {
            bgMusic.setLooping(true);
            bgMusic.setVolume(0.25f, 0.25f);
            bgMusic.start();
        }

        // Text to speech setup
        tts = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ENGLISH);
                tts.setPitch(1.1f);
                tts.setSpeechRate(0.95f);
            }
        });

        // Close game and finish Activity
        View.OnClickListener endGame = view1 -> {
            endAnalyticsSession();
            stopAudioTts();
            requireActivity().finish();
        };

        btnHomeIcon.setOnClickListener(endGame);
        btnCloseIcon.setOnClickListener(endGame);

        // Start first round
        startRound(true);
    }

    // region Game setup and rounds

    private void startRound(boolean firstRound) {
        roundLocked = false;
        cookiesFedThisRound = 0;
        wrongStreak = 0;

        // Target number between 1 and 10
        targetNumber = 1 + rng.nextInt(10);

        imgMonster.setImageResource(R.drawable.monster_neutral);
        tvTarget.setText("Feed me");
        tvRound.setText("Round: " + round);
        tvScore.setText("Score: " + score);
        updateStats();

        progressRound.setMax(targetNumber);
        progressRound.setProgress(0);

        updateTargetNumberImage(targetNumber);

        // Remove any cookies left from previous round
        playArea.removeAllViews();
        createCookiesForRound(10);

        speakPrompt("Feed me " + targetNumber + " cookies");
        pulse(tvTarget);
    }

    private void updateTargetNumberImage(int number) {
        int resId;
        switch (number) {
            case 1:  resId = R.drawable.number_1;  break;
            case 2:  resId = R.drawable.number_2;  break;
            case 3:  resId = R.drawable.number_3;  break;
            case 4:  resId = R.drawable.number_4;  break;
            case 5:  resId = R.drawable.number_5;  break;
            case 6:  resId = R.drawable.number_6;  break;
            case 7:  resId = R.drawable.number_7;  break;
            case 8:  resId = R.drawable.number_8;  break;
            case 9:  resId = R.drawable.number_9;  break;
            case 10: resId = R.drawable.number_10; break;
            default: resId = 0; break;
        }

        if (resId != 0) {
            imgTargetNumber.setVisibility(View.VISIBLE);
            imgTargetNumber.setImageResource(resId);
        } else {
            imgTargetNumber.setVisibility(View.INVISIBLE);
        }
    }

    private void createCookiesForRound(int cookieCount) {
        playArea.post(() -> {
            int width = playArea.getWidth();
            int height = playArea.getHeight();
            if (width <= 0 || height <= 0) return;

            int size = dp(110);
            int margin = dp(6);

            for (int i = 0; i < cookieCount; i++) {
                ImageView cookie = new ImageView(requireContext());
                cookie.setImageResource(R.drawable.cookie);
                cookie.setScaleType(ImageView.ScaleType.FIT_CENTER);

                FrameLayout.LayoutParams lp =
                        new FrameLayout.LayoutParams(size, size);
                cookie.setLayoutParams(lp);

                int totalMaxX = width - size - margin;
                int halfWidth = width / 2;
                int minX = halfWidth;
                int maxX = totalMaxX;

                float startX = minX + rng.nextFloat() * Math.max(1, (maxX - minX));
                float startY = margin + rng.nextFloat() * Math.max(1, (height - size - margin));

                cookie.setX(startX);
                cookie.setY(startY);

                cookie.setOnTouchListener(cookieDragListener);
                playArea.addView(cookie);
            }
        });
    }

    // endregion

    // region Drag and drop logic

    private final View.OnTouchListener cookieDragListener = new View.OnTouchListener() {

        float dX;
        float dY;
        final int[] playAreaLocation = new int[2];

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (roundLocked) return false;

            playArea.getLocationOnScreen(playAreaLocation);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float touchX = event.getRawX() - playAreaLocation[0];
                    float touchY = event.getRawY() - playAreaLocation[1];
                    dX = view.getX() - touchX;
                    dY = view.getY() - touchY;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    float newX = event.getRawX() - playAreaLocation[0] + dX;
                    float newY = event.getRawY() - playAreaLocation[1] + dY;

                    float maxX = playArea.getWidth() - view.getWidth();
                    float maxY = playArea.getHeight() - view.getHeight();
                    newX = Math.max(0, Math.min(newX, maxX));
                    newY = Math.max(0, Math.min(newY, maxY));

                    view.setX(newX);
                    view.setY(newY);
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    handleCookieDrop(view);
                    return true;
                }
                default:
                    return false;
            }
        }
    };

    private void handleCookieDrop(View cookieView) {
        if (roundLocked) return;

        Rect monsterRect = new Rect();
        Rect cookieRect = new Rect();

        imgMonster.getGlobalVisibleRect(monsterRect);
        cookieView.getGlobalVisibleRect(cookieRect);

        boolean hitMonster = Rect.intersects(monsterRect, cookieRect);

        if (hitMonster) {
            handleCookieFed(cookieView);
        } else {
            handleMiss();
        }
    }

    private void handleCookieFed(View cookieView) {
        cookieView.setVisibility(View.INVISIBLE);
        cookiesFedThisRound++;

        progressRound.setProgress(Math.min(cookiesFedThisRound, targetNumber));

        speakNumber(String.valueOf(cookiesFedThisRound));

        if (cookiesFedThisRound == targetNumber) {
            handleCorrectAnswer();
        }
    }

    private void handleMiss() {
        totalIncorrect++;
        wrongStreak++;
        imgMonster.setImageResource(R.drawable.monster_sad);
        shake(imgMonster);
        speakPrompt("Try again");
        updateStats();

        if (wrongStreak >= 5 && !roundLocked) {
            roundLocked = true;
            imgMonster.postDelayed(this::advanceRound, 800);
        }
    }

    private void handleCorrectAnswer() {
        roundLocked = true;
        totalCorrect++;
        wrongStreak = 0;
        score += 10;
        stars++;

        imgMonster.setImageResource(R.drawable.monster_happy);
        showStarFlash();
        wiggle(imgMonster);

        speakPraise("Yay");
        updateStats();

        imgMonster.postDelayed(this::advanceRound, 900);
    }

    private void advanceRound() {
        round++;
        sessionRounds++;
        startRound(false);
    }

    // endregion

    // region Visual feedback

    private void updateStats() {
        tvStats.setText(
                "Correct: " + totalCorrect
                        + "  Incorrect: " + totalIncorrect
                        + "  Played: " + timesPlayed
                        + "  Stars: " + stars
        );
    }

    private void showStarFlash() {
        imgStar.setVisibility(View.VISIBLE);
        animateScale(imgStar, 1.4f);
        imgStar.postDelayed(() -> {
            animateScale(imgStar, 1.0f);
            imgStar.setVisibility(View.GONE);
        }, 600);
    }

    private void animateScale(View v, float toScale) {
        ObjectAnimator sx = ObjectAnimator.ofFloat(v, View.SCALE_X, toScale);
        ObjectAnimator sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, toScale);
        sx.setDuration(120);
        sy.setDuration(120);
        sx.start();
        sy.start();
    }

    private void pulse(View v) {
        ObjectAnimator upX = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.1f);
        ObjectAnimator upY = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.1f);
        upX.setDuration(160);
        upY.setDuration(160);
        upX.start();
        upY.start();

        v.postDelayed(() -> {
            ObjectAnimator downX = ObjectAnimator.ofFloat(v, View.SCALE_X, 1.1f, 1f);
            ObjectAnimator downY = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1.1f, 1f);
            downX.setDuration(160);
            downY.setDuration(160);
            downX.start();
            downY.start();
        }, 180);
    }

    private void wiggle(View v) {
        ObjectAnimator r1 = ObjectAnimator.ofFloat(v, View.ROTATION, -8f);
        ObjectAnimator r2 = ObjectAnimator.ofFloat(v, View.ROTATION, 8f);
        ObjectAnimator r3 = ObjectAnimator.ofFloat(v, View.ROTATION, 0f);
        r1.setDuration(80);
        r2.setDuration(80);
        r3.setDuration(80);
        r1.start();
        v.postDelayed(r2::start, 90);
        v.postDelayed(r3::start, 180);
    }

    private void shake(View v) {
        ObjectAnimator r1 = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, -dp(8));
        ObjectAnimator r2 = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, dp(8));
        ObjectAnimator r3 = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0);
        r1.setDuration(70);
        r2.setDuration(70);
        r3.setDuration(70);
        r1.start();
        v.postDelayed(r2::start, 80);
        v.postDelayed(r3::start, 160);
    }

    // endregion

    // region Analytics end and lifecycle

    private void endAnalyticsSession() {
        if (analyticsManager == null || analyticsSaved) {
            return;
        }

        long sessionEndMs = SystemClock.elapsedRealtime();
        long durationMs = Math.max(0L, sessionEndMs - sessionStartMs);
        int totalAttempts = totalCorrect + totalIncorrect;

        // Completed if at least one round was played
        int completedFlag = sessionRounds > 0 ? 1 : 0;

        // 1. Detailed analytics to SQLite via AnalyticsSessionManager
        analyticsManager.endSession(
                score,
                totalCorrect,
                totalAttempts,
                stars,
                completedFlag
        );
        analyticsManager = null;

        // 2. Summary to Firestore if we have a child id
        if (childId != null) {
            progressService.recordGameSession(
                    childId,
                    ModuleIds.MODULE_FEED_MONSTER,
                    score,
                    durationMs,     // timeSpentMs
                    stars,          // stars
                    totalCorrect,   // correct
                    totalIncorrect, // incorrect
                    timesPlayed,    // plays
                    new DataCallbacks.GenericCallback() {
                        @Override public void onSuccess(String result) { }

                        @Override public void onFailure(Exception e) { }
                    }
            );
        }

        analyticsSaved = true;
    }


    @Override
    public void onPause() {
        super.onPause();
        if (bgMusic != null && bgMusic.isPlaying()) {
            bgMusic.pause();
        }
        endAnalyticsSession();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bgMusic != null) {
            bgMusic.start();
        }
    }

    @Override
    public void onDestroyView() {
        endAnalyticsSession();
        super.onDestroyView();
        stopAudioTts();
    }

    // endregion

    // region Audio and helpers

    private void speakPrompt(String text) {
        speakInternal(text, TextToSpeech.QUEUE_FLUSH);
    }

    private void speakNumber(String text) {
        speakInternal(text, TextToSpeech.QUEUE_FLUSH);
    }

    private void speakPraise(String text) {
        speakInternal(text, TextToSpeech.QUEUE_ADD);
    }

    private void speakInternal(String text, int mode) {
        if (tts == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, mode, null, "feed_monster_tts");
        } else {
            tts.speak(text, mode, null);
        }
    }

    private int dp(int value) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (value * scale + 0.5f);
    }

    private void stopAudioTts() {
        if (bgMusic != null) {
            try {
                if (bgMusic.isPlaying()) bgMusic.stop();
            } catch (Exception ignored) { }
            bgMusic.release();
            bgMusic = null;
        }

        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) { }
            tts.shutdown();
            tts = null;
        }
    }

    // endregion
}
