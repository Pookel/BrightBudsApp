package com.example.brightbuds_app.ui.games;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.adapters.MemoryMatchAdapter;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.models.MemoryCard;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;
import com.example.brightbuds_app.utils.Constants;
import com.example.brightbuds_app.utils.CurrentChildManager;
import com.example.brightbuds_app.utils.ModuleIds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MemoryMatchFragment extends Fragment implements MemoryMatchAdapter.OnCardClickListener {

    private static final String TAG = "MemoryMatchFragment";

    private static final String PREFS = "brightbuds_memory_match_prefs";
    private static final String KEY_TIMES_PLAYED = "memory_match_times_played";
    private static final String KEY_BEST_TIME_MS = "memory_match_best_time_ms";
    private static final String KEY_TOTAL_TIME_MS = "memory_match_total_time_ms";
    private static final String KEY_TOTAL_GAMES = "memory_match_total_games";

    private RecyclerView recyclerView;
    private TextView tvScore;
    private TextView tvAttempts;
    private TextView tvStars;
    private TextView tvTimer;

    private MemoryMatchAdapter adapter;
    private final List<MemoryCard> cards = new ArrayList<>();

    private int firstSelectedPos = -1;
    private int secondSelectedPos = -1;
    private boolean isBusy = false;
    private boolean hintShowing = false;

    private int attemptsCount = 0;
    private int matchesFound = 0;
    private int totalPairs;
    private int wrongStreak = 0;
    private int starsEarned = 0;
    private int score = 0;

    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;
    private long sessionStartMs = 0L;
    private long elapsedMs = 0L;
    private boolean timerRunning = false;

    private int timesPlayed;
    private long bestTimeMs;
    private long totalTimeMs;
    private int totalGames;

    private MediaPlayer bgMusicPlayer;
    private SoundPool soundPool;
    private int soundCorrectId;
    private int soundWrongId;
    private int flipSoundId;

    private ProgressService progressService;
    private String selectedChildId;

    private TextToSpeech tts;

    // Analytics
    private AnalyticsSessionManager analyticsManager;
    private String analyticsChildId;
    private boolean analyticsSaved = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = requireContext();

        progressService = new ProgressService(context);
        analyticsChildId = CurrentChildManager.getCurrentChildId(context);

        SharedPreferences parentPrefs =
                context.getSharedPreferences("BrightBudsPrefs", Context.MODE_PRIVATE);
        selectedChildId = parentPrefs.getString("selectedChildId", null);

        SharedPreferences sp =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        timesPlayed = sp.getInt(KEY_TIMES_PLAYED, 0) + 1;
        bestTimeMs = sp.getLong(KEY_BEST_TIME_MS, Long.MAX_VALUE);
        totalTimeMs = sp.getLong(KEY_TOTAL_TIME_MS, 0L);
        totalGames = sp.getInt(KEY_TOTAL_GAMES, 0);

        sp.edit().putInt(KEY_TIMES_PLAYED, timesPlayed).apply();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_memory_match, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {

        recyclerView = view.findViewById(R.id.recyclerViewMemoryMatch);
        tvScore = view.findViewById(R.id.tvMMScore);
        tvAttempts = view.findViewById(R.id.tvMMAttempts);
        tvStars = view.findViewById(R.id.tvMMStars);
        tvTimer = view.findViewById(R.id.tvMMTimer);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        // Analytics session for Memory Match
        analyticsManager = new AnalyticsSessionManager(
                requireContext(),
                analyticsChildId,
                ModuleIds.MODULE_MEMORY_MATCH
        );
        analyticsManager.startSession();
        analyticsSaved = false;

        setupCards();
        initSounds();
        startBackgroundMusic();
        initTextToSpeech();

        adapter = new MemoryMatchAdapter(cards, this, soundPool, flipSoundId);
        recyclerView.setAdapter(adapter);

        sessionStartMs = SystemClock.elapsedRealtime();
        startTimer();

        speakPrompt("Find the matching cards");
        updateHud();
    }

    // region Setup

    private void setupCards() {
        int[] allImages = new int[]{
                R.drawable.number_1,
                R.drawable.number_2,
                R.drawable.number_3,
                R.drawable.number_4,
                R.drawable.number_5,
                R.drawable.number_6,
                R.drawable.number_7,
                R.drawable.number_8,
                R.drawable.number_9,
                R.drawable.number_10,
                R.drawable.smiling_star,
                R.drawable.apple,
                R.drawable.monster_happy,
                R.drawable.monster_sad,
                R.drawable.monster_neutral,
                R.drawable.ic_brightbuds_logo,
                R.drawable.ic_monster
        };

        totalPairs = 2;

        List<Integer> chosenImages = new ArrayList<>();
        for (int image : allImages) {
            chosenImages.add(image);
        }
        Collections.shuffle(chosenImages);

        List<Integer> selectedForPairs = chosenImages.subList(0, totalPairs);

        List<MemoryCard> tempCards = new ArrayList<>();
        int idCounter = 0;
        for (int imgRes : selectedForPairs) {
            tempCards.add(new MemoryCard(idCounter++, imgRes));
            tempCards.add(new MemoryCard(idCounter++, imgRes));
        }

        Collections.shuffle(tempCards);
        cards.clear();
        cards.addAll(tempCards);
    }

    private void initSounds() {
        soundPool = new SoundPool.Builder().setMaxStreams(3).build();
        soundCorrectId = soundPool.load(getContext(), R.raw.memory_correct, 1);
        soundWrongId = soundPool.load(getContext(), R.raw.memory_wrong, 1);
        flipSoundId = soundPool.load(getContext(), R.raw.card_flip, 1);
    }

    private void startBackgroundMusic() {
        try {
            bgMusicPlayer = MediaPlayer.create(getContext(), R.raw.memory_match_bg);
            if (bgMusicPlayer != null) {
                bgMusicPlayer.setLooping(true);
                bgMusicPlayer.setVolume(0.4f, 0.4f);
                bgMusicPlayer.start();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error starting memory bg music", ex);
        }
    }

    private void initTextToSpeech() {
        tts = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ENGLISH);
                tts.setPitch(1.1f);
                tts.setSpeechRate(0.95f);
            }
        });
    }

    // endregion

    // region Timer

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

    // endregion

    // region Card click and matching

    @Override
    public void onCardClick(int position) {
        if (isBusy || hintShowing) {
            return;
        }

        MemoryCard card = cards.get(position);
        if (card.isMatched() || card.isFlipped()) {
            return;
        }

        card.setFlipped(true);
        adapter.notifyItemChanged(position);

        if (firstSelectedPos == -1) {
            firstSelectedPos = position;
        } else if (secondSelectedPos == -1 && position != firstSelectedPos) {
            secondSelectedPos = position;
            isBusy = true;
            attemptsCount++;
            updateHud();
            new Handler().postDelayed(this::checkForMatch, 600);
        }
    }

    private void checkForMatch() {
        if (firstSelectedPos == -1 || secondSelectedPos == -1) {
            isBusy = false;
            return;
        }

        MemoryCard first = cards.get(firstSelectedPos);
        MemoryCard second = cards.get(secondSelectedPos);

        if (first.getImageResId() == second.getImageResId()) {
            first.setMatched(true);
            second.setMatched(true);
            matchesFound++;
            score += 10;
            starsEarned++;
            wrongStreak = 0;
            playCorrectSound();
            speakPositive("Good match");
        } else {
            first.setFlipped(false);
            second.setFlipped(false);
            wrongStreak++;
            playWrongSound();
            speakPrompt("Try again");

            if (wrongStreak >= 3) {
                showHintForPair();
                wrongStreak = 0;
            }
        }

        adapter.notifyItemChanged(firstSelectedPos);
        adapter.notifyItemChanged(secondSelectedPos);

        firstSelectedPos = -1;
        secondSelectedPos = -1;
        isBusy = false;

        updateHud();

        if (matchesFound == totalPairs) {
            onGameCompleted();
        }
    }

    private void playCorrectSound() {
        if (soundPool != null) {
            soundPool.play(soundCorrectId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void playWrongSound() {
        if (soundPool != null) {
            soundPool.play(soundWrongId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void showHintForPair() {
        List<Integer> unmatched = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            if (!cards.get(i).isMatched()) {
                unmatched.add(i);
            }
        }
        if (unmatched.size() < 2) return;

        int firstIdx = -1;
        int secondIdx = -1;

        outer:
        for (int i = 0; i < unmatched.size(); i++) {
            for (int j = i + 1; j < unmatched.size(); j++) {
                int idx1 = unmatched.get(i);
                int idx2 = unmatched.get(j);
                if (cards.get(idx1).getImageResId() == cards.get(idx2).getImageResId()) {
                    firstIdx = idx1;
                    secondIdx = idx2;
                    break outer;
                }
            }
        }

        if (firstIdx == -1 || secondIdx == -1) {
            return;
        }

        final int f1 = firstIdx;
        final int f2 = secondIdx;

        hintShowing = true;

        MemoryCard c1 = cards.get(f1);
        MemoryCard c2 = cards.get(f2);
        c1.setFlipped(true);
        c2.setFlipped(true);
        adapter.notifyItemChanged(f1);
        adapter.notifyItemChanged(f2);

        View v1 = recyclerView.getLayoutManager().findViewByPosition(f1);
        View v2 = recyclerView.getLayoutManager().findViewByPosition(f2);
        if (v1 != null) pulse(v1);
        if (v2 != null) pulse(v2);

        new Handler().postDelayed(() -> {
            if (!c1.isMatched()) {
                c1.setFlipped(false);
                adapter.notifyItemChanged(f1);
            }
            if (!c2.isMatched()) {
                c2.setFlipped(false);
                adapter.notifyItemChanged(f2);
            }
            hintShowing = false;
        }, 1100);
    }

    private void onGameCompleted() {
        stopTimer();
        long time = elapsedMs;

        Toast.makeText(getContext(), "Great job. All pairs matched.", Toast.LENGTH_SHORT).show();
        speakPositive("Great job");

        saveLocalStats(time);

        // Mark analytics as completed and send to SQLite + Firestore
        endAnalyticsSession(true);

        new Handler().postDelayed(this::resetGame, 1200);
    }

    private void resetGame() {
        firstSelectedPos = -1;
        secondSelectedPos = -1;
        isBusy = false;
        hintShowing = false;

        attemptsCount = 0;
        matchesFound = 0;
        wrongStreak = 0;
        starsEarned = 0;
        score = 0;

        setupCards();
        adapter.notifyDataSetChanged();

        sessionStartMs = SystemClock.elapsedRealtime();
        elapsedMs = 0L;
        tvTimer.setText("Time: " + Constants.formatTime(0));
        startTimer();
        speakPrompt("Find the matching cards");
        updateHud();
    }

    // endregion

    // region Stats and metrics

    private void saveLocalStats(long timeTaken) {
        SharedPreferences sp =
                requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        totalGames = sp.getInt(KEY_TOTAL_GAMES, 0);
        totalTimeMs = sp.getLong(KEY_TOTAL_TIME_MS, 0L);
        bestTimeMs = sp.getLong(KEY_BEST_TIME_MS, Long.MAX_VALUE);

        totalGames += 1;
        totalTimeMs += timeTaken;
        if (timeTaken < bestTimeMs) {
            bestTimeMs = timeTaken;
        }

        sp.edit()
                .putInt(KEY_TOTAL_GAMES, totalGames)
                .putLong(KEY_TOTAL_TIME_MS, totalTimeMs)
                .putLong(KEY_BEST_TIME_MS, bestTimeMs)
                .apply();
    }

    private void updateHud() {
        tvScore.setText("Score: " + score);
        tvAttempts.setText("Attempts: " + attemptsCount);
        tvStars.setText("Stars: " + starsEarned);
    }

    // endregion

    // region Animations and helpers

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

    // endregion

    // region Text to speech

    private void speakPrompt(String text) {
        speakInternal(text, TextToSpeech.QUEUE_FLUSH);
    }

    private void speakPositive(String text) {
        speakInternal(text, TextToSpeech.QUEUE_ADD);
    }

    private void speakInternal(String text, int queueMode) {
        if (tts == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, queueMode, null, "memory_match_tts");
        } else {
            tts.speak(text, queueMode, null);
        }
    }

    // endregion

    // region Analytics and lifecycle

    private void saveSessionSummaryToFirestore(long timeSpentMs, boolean completed) {
        if (selectedChildId == null) return;

        int totalAttempts = attemptsCount;              // how many pair attempts
        int totalCorrect = matchesFound;                // how many correct pairs
        int incorrect = Math.max(0, totalAttempts - totalCorrect);
        int stars = starsEarned;
        int plays = 1;                                  // one Memory Match session

        progressService.recordGameSession(
                selectedChildId,
                ModuleIds.MODULE_MEMORY_MATCH,
                score,
                timeSpentMs,
                stars,
                totalCorrect,
                incorrect,
                plays,
                new DataCallbacks.GenericCallback() {
                    @Override
                    public void onSuccess(String r) { }

                    @Override
                    public void onFailure(Exception e) {
                        Log.w(TAG, "Failed to save memory match metrics", e);
                    }
                }
        );
    }


    private void endAnalyticsSession(boolean completed) {
        if (analyticsManager == null || analyticsSaved) {
            return;
        }

        int totalAttempts = attemptsCount;
        int totalCorrect = matchesFound;
        int stars = starsEarned;
        int completedFlag = completed ? 1 : 0;

        long durationMs = elapsedMs;
        if (durationMs <= 0L && sessionStartMs > 0L) {
            durationMs = SystemClock.elapsedRealtime() - sessionStartMs;
        }

        // Detailed analytics to SQLite
        analyticsManager.endSession(
                score,
                totalCorrect,
                totalAttempts,
                stars,
                completedFlag
        );
        analyticsManager = null;

        // Summary to Firestore
        saveSessionSummaryToFirestore(durationMs, completed);

        analyticsSaved = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        stopTimer();
        if (bgMusicPlayer != null && bgMusicPlayer.isPlaying()) {
            bgMusicPlayer.pause();
        }
        endAnalyticsSession(matchesFound == totalPairs);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bgMusicPlayer != null && !bgMusicPlayer.isPlaying()) {
            bgMusicPlayer.start();
        }
        if (!timerRunning && matchesFound < totalPairs) {
            sessionStartMs = SystemClock.elapsedRealtime() - elapsedMs;
            startTimer();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
        endAnalyticsSession(matchesFound == totalPairs);

        if (bgMusicPlayer != null) {
            try {
                if (bgMusicPlayer.isPlaying()) bgMusicPlayer.stop();
            } catch (Exception ignored) { }
            bgMusicPlayer.release();
            bgMusicPlayer = null;
        }

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
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
