package com.example.brightbuds_app.ui.games;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.adapters.FamilyGalleryAdapter;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.models.FamilyMember;
import com.example.brightbuds_app.services.DatabaseHelper;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;
import com.example.brightbuds_app.utils.Constants;
import com.example.brightbuds_app.utils.CurrentChildManager;
import com.example.brightbuds_app.utils.ModuleIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FamilyGalleryFragment extends Fragment {

    private static final String TAG = "FamilyGalleryFragment";

    private static final String PREFS_FAMILY = "brightbuds_family_prefs";
    private static final String KEY_TIMES_PLAYED = "family_times_played";

    private ViewPager2 viewPager;
    private FamilyGalleryAdapter adapter;

    private TextView tvScore;
    private TextView tvCorrect;
    private TextView tvAttempts;
    private TextView tvStars;
    private TextView tvTimer;
    private TextView tvRelationshipLine;
    private TextView tvNameLine;
    private TextView tvQuestion;
    private TextView btnOptionCorrect;
    private TextView btnOptionOther;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnHome;
    private ImageButton btnClose;

    private final List<FamilyMember> familyMembers = new ArrayList<>();
    private boolean[] questionAnswered;
    private boolean[] answeredCorrectly;
    private boolean[] viewedPhoto;
    private int currentIndex = 0;

    private int attempts = 0;
    private int correctAnswers = 0;
    private int incorrectAttempts = 0;
    private int starsEarned = 0;
    private int timesPlayed = 0;

    private long sessionStartMs = 0L;
    private long elapsedMs = 0L;
    private boolean timerRunning = false;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private MediaPlayer bgMusicPlayer;
    private SoundPool soundPool;
    private int soundFlipId;
    private int soundClapId;
    private TextToSpeech tts;
    private Handler ttsHandler;

    private ProgressService progressService;
    private DatabaseHelper localDb;
    private String selectedChildId;

    private GestureDetector gestureDetector;

    // Analytics
    private AnalyticsSessionManager analyticsManager;
    private String analyticsChildId;
    private boolean analyticsSaved = false;
    private boolean sessionSaved = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context context = requireContext();

        progressService = new ProgressService(context);
        localDb = new DatabaseHelper(context);

        SharedPreferences parentPrefs =
                context.getSharedPreferences("BrightBudsPrefs", Context.MODE_PRIVATE);
        selectedChildId = parentPrefs.getString("selectedChildId", null);

        SharedPreferences sp =
                context.getSharedPreferences(PREFS_FAMILY, Context.MODE_PRIVATE);
        timesPlayed = sp.getInt(KEY_TIMES_PLAYED, 0) + 1;
        sp.edit().putInt(KEY_TIMES_PLAYED, timesPlayed).apply();

        timerHandler = new Handler();
        ttsHandler = new Handler();

        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ENGLISH);
                tts.setPitch(1.0f);
                tts.setSpeechRate(0.95f);
            }
        });

        initSounds(context);
        initGestureDetector(context);

        // Child for analytics
        analyticsChildId = CurrentChildManager.getCurrentChildId(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_family_gallery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        tvScore = v.findViewById(R.id.tvFGScore);
        tvCorrect = v.findViewById(R.id.tvFGCorrect);
        tvAttempts = v.findViewById(R.id.tvFGAttempts);
        tvStars = v.findViewById(R.id.tvFGStars);
        tvTimer = v.findViewById(R.id.tvFGTimer);

        tvRelationshipLine = v.findViewById(R.id.tvRelationshipLine);
        tvNameLine = v.findViewById(R.id.tvNameLine);
        tvQuestion = v.findViewById(R.id.tvQuestion);
        btnOptionCorrect = v.findViewById(R.id.btnOptionCorrect);
        btnOptionOther = v.findViewById(R.id.btnOptionOther);

        btnPrev = v.findViewById(R.id.btnPrevPhoto);
        btnNext = v.findViewById(R.id.btnNextPhoto);

        btnHome = v.findViewById(R.id.btnHomeIcon);
        btnClose = v.findViewById(R.id.btnCloseIcon);

        viewPager = v.findViewById(R.id.familyViewPager);

        adapter = new FamilyGalleryAdapter(familyMembers);
        viewPager.setAdapter(adapter);

        // Start analytics session for Family Gallery
        if (analyticsChildId != null) {
            analyticsManager = new AnalyticsSessionManager(
                    requireContext(),
                    analyticsChildId,
                    ModuleIds.MODULE_FAMILY_GALLERY
            );
            analyticsManager.startSession();
            analyticsSaved = false;
        }

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {

            private int lastIndex = 0;
            private boolean first = true;

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);

                if (!first) {
                    handleUnansweredForIndex(lastIndex);
                }

                currentIndex = position;
                lastIndex = position;
                first = false;

                if (viewedPhoto != null && position < viewedPhoto.length) {
                    viewedPhoto[position] = true;
                }

                showCurrentPhotoInfo();
                playFlipSound();
                animateImageChange();

                updateHud();
            }
        });

        View pagerChild = viewPager.getChildAt(0);
        if (pagerChild != null) {
            pagerChild.setOnTouchListener((view, event) -> {
                if (gestureDetector != null) {
                    gestureDetector.onTouchEvent(event);
                }
                return false;
            });
        }

        btnPrev.setOnClickListener(v1 -> goToPreviousPhoto());
        btnNext.setOnClickListener(v12 -> goToNextPhoto());

        View.OnClickListener endSessionListener = click -> {
            saveSessionMetrics();
            endAnalyticsSession(hasViewedAllPhotosAtLeastOnce());
            stopAllAudio();
            requireActivity().finish();
        };
        btnHome.setOnClickListener(endSessionListener);
        btnClose.setOnClickListener(endSessionListener);

        btnOptionCorrect.setOnClickListener(v13 -> handleAnswer(true));
        btnOptionOther.setOnClickListener(v14 -> handleAnswer(false));

        loadFamilyMembersFromLocalDb();
        prepareStateArrays();
        adapter.notifyDataSetChanged();

        if (!familyMembers.isEmpty()) {
            currentIndex = 0;
            viewedPhoto[0] = true;
            showCurrentPhotoInfo();
        } else {
            Toast.makeText(requireContext(),
                    "No family photos found. Please add family members first.",
                    Toast.LENGTH_LONG).show();
        }

        startBackgroundMusic();
        sessionStartMs = System.currentTimeMillis();
        startTimer();
        updateHud();
    }

    // region Load data from SQLite

    private void loadFamilyMembersFromLocalDb() {
        familyMembers.clear();
        if (localDb == null) {
            Log.w(TAG, "Database not initialised, skipping family load");
            return;
        }

        long parentLocalId = localDb.getCurrentParentLocalId();
        List<FamilyMember> fromDb = localDb.getFamilyMembersForParent(parentLocalId);
        familyMembers.addAll(fromDb);

        Log.d(TAG, "Loaded " + familyMembers.size() + " family members from local DB");
    }

    private void prepareStateArrays() {
        int size = familyMembers.size();
        questionAnswered = new boolean[size];
        answeredCorrectly = new boolean[size];
        viewedPhoto = new boolean[size];
    }

    // endregion

    // region Sounds and music

    private void initSounds(Context context) {
        soundPool = new SoundPool.Builder().setMaxStreams(3).build();
        soundFlipId = soundPool.load(context, R.raw.card_flip, 1);
        soundClapId = soundPool.load(context, R.raw.clap_sound, 1);
    }

    private void startBackgroundMusic() {
        try {
            bgMusicPlayer = MediaPlayer.create(requireContext(), R.raw.classical_music);
            if (bgMusicPlayer != null) {
                bgMusicPlayer.setLooping(true);
                bgMusicPlayer.setVolume(0.2f, 0.2f);
                bgMusicPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting classical music", e);
        }
    }

    private void playFlipSound() {
        if (soundPool != null && soundFlipId != 0) {
            soundPool.play(soundFlipId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void playClapSound() {
        if (soundPool != null && soundClapId != 0) {
            soundPool.play(soundClapId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void stopAllAudio() {
        if (bgMusicPlayer != null) {
            try {
                if (bgMusicPlayer.isPlaying()) {
                    bgMusicPlayer.stop();
                }
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

    // region Gesture detector

    private void initGestureDetector(Context context) {
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 120;
            private static final int SWIPE_VELOCITY_THRESHOLD = 120;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD &&
                            Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            goToPreviousPhoto();
                        } else {
                            goToNextPhoto();
                        }
                        return true;
                    }
                }
                return false;
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

                elapsedMs = System.currentTimeMillis() - sessionStartMs;
                tvTimer.setText("Time: " + Constants.formatTime(elapsedMs));
                timerHandler.postDelayed(this, Constants.ONE_SECOND_MS);
            }
        };
        timerHandler.postDelayed(timerRunnable, Constants.ONE_SECOND_MS);
    }

    private void stopTimer() {
        timerRunning = false;
        if (timerHandler != null) {
            timerHandler.removeCallbacksAndMessages(null);
        }
    }

    // endregion

    // region Navigation

    private void goToPreviousPhoto() {
        if (familyMembers.isEmpty()) return;
        int index = viewPager.getCurrentItem();
        if (index > 0) {
            viewPager.setCurrentItem(index - 1, true);
        }
    }

    private void goToNextPhoto() {
        if (familyMembers.isEmpty()) return;
        int index = viewPager.getCurrentItem();
        if (index < familyMembers.size() - 1) {
            viewPager.setCurrentItem(index + 1, true);
        }
    }

    private void handleUnansweredForIndex(int index) {
        if (index < 0 || index >= familyMembers.size()) return;
        if (questionAnswered == null) return;

        if (!questionAnswered[index]) {
            attempts++;
            incorrectAttempts++;
        }
    }

    // endregion

    // region Display and TTS

    private void showCurrentPhotoInfo() {
        if (familyMembers.isEmpty()) return;
        if (currentIndex < 0 || currentIndex >= familyMembers.size()) return;

        FamilyMember member = familyMembers.get(currentIndex);

        String rel = member.getRelationship() != null
                ? member.getRelationship()
                : "family member";

        String name = member.getFirstName() != null
                ? member.getFirstName()
                : "someone special";

        String line1 = "This is my " + rel + ".";
        String line2 = "My " + rel.toLowerCase(Locale.getDefault()) + "'s name is " + name + ".";

        tvRelationshipLine.setText(line1);
        tvNameLine.setText(line2);

        tvQuestion.setText("Who is this?");
        btnOptionCorrect.setText("My " + rel);
        btnOptionOther.setText("Someone else");

        animateText(tvRelationshipLine);
        animateText(tvNameLine);

        speakNow(line1);
        speakDelayed(line2, 1500);
    }

    private void handleAnswer(boolean isCorrectOption) {
        if (familyMembers.isEmpty()) return;
        if (currentIndex < 0 || currentIndex >= familyMembers.size()) return;

        if (questionAnswered != null && questionAnswered[currentIndex]) {
            return;
        }

        attempts++;

        if (isCorrectOption) {
            correctAnswers++;
            if (answeredCorrectly != null) {
                answeredCorrectly[currentIndex] = true;
            }
            playClapSound();
            speakNow("Well done.");
        } else {
            incorrectAttempts++;
            speakNow("Let us say it together. " + tvRelationshipLine.getText()
                    + " " + tvNameLine.getText());
        }

        if (questionAnswered != null) {
            questionAnswered[currentIndex] = true;
        }

        updateHud();

        if (currentIndex < familyMembers.size() - 1) {
            viewPager.postDelayed(this::goToNextPhoto, 900);
        }
    }

    private void speakNow(String text) {
        if (tts == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "family_tts");
            } else {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        } catch (Exception e) {
            Log.e(TAG, "TTS speak error", e);
        }
    }

    private void speakDelayed(String text, long delayMs) {
        if (ttsHandler == null) return;
        ttsHandler.postDelayed(() -> speakNow(text), delayMs);
    }

    private void animateImageChange() {
        if (viewPager == null) return;

        viewPager.setAlpha(0f);
        viewPager.setScaleX(0.9f);
        viewPager.setScaleY(0.9f);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(viewPager, View.ALPHA, 0f, 1f);
        ObjectAnimator sx = ObjectAnimator.ofFloat(viewPager, View.SCALE_X, 0.9f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(viewPager, View.SCALE_Y, 0.9f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.setDuration(250);
        set.playTogether(alpha, sx, sy);
        set.start();
    }

    private void animateText(View v) {
        ObjectAnimator upX = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.05f);
        ObjectAnimator upY = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.05f);
        upX.setDuration(150);
        upY.setDuration(150);

        ObjectAnimator downX = ObjectAnimator.ofFloat(v, View.SCALE_X, 1.05f, 1f);
        ObjectAnimator downY = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1.05f, 1f);
        downX.setDuration(150);
        downY.setDuration(150);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(upX, downX);
        set.start();
    }

    // endregion

    // region Metrics and analytics

    private boolean hasViewedAllPhotosAtLeastOnce() {
        if (viewedPhoto == null || viewedPhoto.length == 0) return false;
        for (boolean v : viewedPhoto) {
            if (!v) return false;
        }
        return true;
    }

    private void updateHud() {
        int baseStars = correctAnswers / 3;
        int bonus = hasViewedAllPhotosAtLeastOnce() ? 1 : 0;
        starsEarned = baseStars + bonus;

        int score = starsEarned * 10;

        tvScore.setText("Score: " + score);
        tvCorrect.setText("Correct: " + correctAnswers);
        tvAttempts.setText("Attempts: " + attempts);
        tvStars.setText("Stars: " + starsEarned);
    }

    private void saveSessionMetrics() {
        if (sessionSaved) {
            return;
        }
        sessionSaved = true;

        if (selectedChildId == null) {
            Log.w(TAG, "No child selected. Skipping remote progress save.");
            return;
        }

        long totalTimeMs = elapsedMs;
        int score = starsEarned * 10;
        int plays = Math.max(1, timesPlayed);

        progressService.recordGameSession(
                selectedChildId,
                ModuleIds.MODULE_FAMILY_GALLERY,
                score,
                totalTimeMs,
                starsEarned,
                correctAnswers,
                incorrectAttempts,
                plays,
                new DataCallbacks.GenericCallback() {
                    @Override
                    public void onSuccess(String result) {
                        Log.d(TAG, "Family game progress saved successfully.");
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Failed to save family game progress", e);
                    }
                }
        );
    }

    private void endAnalyticsSession(boolean completed) {
        if (analyticsManager == null || analyticsSaved) {
            return;
        }

        int score = starsEarned * 10;
        int totalCorrect = correctAnswers;
        int totalAttempts = attempts;
        int stars = starsEarned;
        int completedFlag = completed ? 1 : 0;

        analyticsManager.endSession(
                score,
                totalCorrect,
                totalAttempts,
                stars,
                completedFlag
        );
        analyticsManager = null;
        analyticsSaved = true;
    }

    // endregion

    // region Lifecycle

    @Override
    public void onPause() {
        super.onPause();
        stopTimer();
        if (bgMusicPlayer != null && bgMusicPlayer.isPlaying()) {
            bgMusicPlayer.pause();
        }
        endAnalyticsSession(hasViewedAllPhotosAtLeastOnce());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bgMusicPlayer != null && !bgMusicPlayer.isPlaying()) {
            bgMusicPlayer.start();
        }
        if (!timerRunning && !familyMembers.isEmpty()) {
            sessionStartMs = System.currentTimeMillis() - elapsedMs;
            startTimer();
        }
    }

    @Override
    public void onDestroyView() {
        saveSessionMetrics();
        stopTimer();
        stopAllAudio();
        endAnalyticsSession(hasViewedAllPhotosAtLeastOnce());
        super.onDestroyView();
    }

    // endregion
}
