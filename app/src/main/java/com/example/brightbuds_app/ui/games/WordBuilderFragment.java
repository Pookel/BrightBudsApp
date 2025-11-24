package com.example.brightbuds_app.ui.games;

import android.content.ClipData;
import android.content.ClipDescription;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.services.ProgressService;
import com.example.brightbuds_app.utils.AnalyticsSessionManager;

import com.example.brightbuds_app.models.ChildProfile;
import com.example.brightbuds_app.services.ChildProfileDAO;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WordBuilderFragment extends Fragment implements
        View.OnTouchListener, View.OnDragListener {

    private static final String TAG = "WordBuilderFragment";
    private static final int MAX_TRAY_LETTERS = 8;

    // UI
    private LinearLayout targetSlotsContainer;
    private LinearLayout draggableLettersContainer;
    private LinearLayout draggableRowTop;
    private LinearLayout draggableRowBottom;

    private TextView tvScore;
    private TextView tvRound;
    private TextView tvStats;
    private TextView tvChildName;
    private TextView tvTargetWord;
    private ImageButton btnReplay;
    private ImageButton btnHomeIcon;
    private ImageButton btnCloseIcon;


    // Audio
    private MediaPlayer bgMusic;
    private MediaPlayer correctSound;
    private MediaPlayer wrongSound;
    private MediaPlayer clapSound;
    private TextToSpeech tts;

    // Game data
    private final List<String> wordPool = new ArrayList<>();
    private List<String> parentWords = new ArrayList<>();
    private String currentWord;
    private int currentIndex = 0;

    private final List<Character> targetLetters = new ArrayList<>();
    private int correctPlacedCount = 0;
    private boolean isParentWord = false;

    // Metrics per session
    private long sessionStartMs;
    private boolean sessionSaved = false;
    private int sessionScorePercent = 0;
    private int sessionPlays = 0;
    private int sessionCorrectLetters = 0;
    private int sessionAttemptsLetters = 0;

    // Parent vs default word stats
    private int parentCorrectLetters = 0;
    private int parentAttemptLetters = 0;
    private int defaultCorrectLetters = 0;
    private int defaultAttemptLetters = 0;

    // Time per word
    private long roundStartTime;

    // Services
    private ProgressService progressService;
    private ChildProfileDAO childProfileDao;
    private ChildProfile currentChildProfile;

    private String childId;
    private String childName;
    private String moduleIdWordBuilder;

    // Analytics
    private AnalyticsSessionManager analyticsManager;

    public WordBuilderFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        progressService = new ProgressService(requireContext());
        childProfileDao = new ChildProfileDAO(requireContext());


        Bundle args = getArguments();
        if (args != null) {
            childId = args.getString("child_id");
            childName = args.getString("child_name");
            moduleIdWordBuilder = args.getString("module_id");
        }

        loadWordsForChild();
        setupAudio();
        setupTts();

        sessionStartMs = SystemClock.elapsedRealtime();

        // Analytics session (unchanged)
        if (childId != null && moduleIdWordBuilder != null) {
            analyticsManager = new AnalyticsSessionManager(
                    requireContext(),
                    childId,
                    moduleIdWordBuilder
            );
            analyticsManager.startSession();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_word_builder, container, false);

        targetSlotsContainer = view.findViewById(R.id.targetSlotsContainer);
        draggableLettersContainer = view.findViewById(R.id.draggableLettersContainer);
        draggableRowTop = view.findViewById(R.id.draggableRowTop);
        draggableRowBottom = view.findViewById(R.id.draggableRowBottom);

        tvScore = view.findViewById(R.id.tvWBScore);
        tvRound = view.findViewById(R.id.tvWBRound);
        tvStats = view.findViewById(R.id.tvWBStats);
        tvChildName = view.findViewById(R.id.tvWBChildName);
        tvTargetWord = view.findViewById(R.id.tvWBTargetWord);
        btnReplay = view.findViewById(R.id.btnWBReplay);
        btnHomeIcon = view.findViewById(R.id.btnHomeIcon);
        btnCloseIcon = view.findViewById(R.id.btnCloseIcon);

        if (childName != null) {
            tvChildName.setText(childName);
        }

        btnReplay.setOnClickListener(v -> speakWordOnly());

        View.OnClickListener exitListener = v -> {
            saveSessionMetricsIfNeeded();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        };
        btnHomeIcon.setOnClickListener(exitListener);
        btnCloseIcon.setOnClickListener(exitListener);

        startRound();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bgMusic != null) {
            bgMusic.setLooping(true);
            bgMusic.start();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (bgMusic != null && bgMusic.isPlaying()) {
            bgMusic.pause();
        }
        saveSessionMetricsIfNeeded();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        saveSessionMetricsIfNeeded();
        releaseAudio();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    // Setup

    private void setupAudio() {
        // Background music removed (no looped music)
        bgMusic = null;
        correctSound = MediaPlayer.create(requireContext(), R.raw.memory_correct);
        wrongSound = MediaPlayer.create(requireContext(), R.raw.memory_wrong);
        clapSound = MediaPlayer.create(requireContext(), R.raw.clap_sound);
    }

    private void setupTts() {
        tts = new TextToSpeech(requireContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });
    }

    private void loadWordsForChild() {

        parentWords = new ArrayList<>();

        // Try load custom words from local ChildProfile for this child
        if (childId != null && childProfileDao != null) {
            currentChildProfile = childProfileDao.getChildById(childId);
            if (currentChildProfile != null) {
                List<String> custom = new ArrayList<>();
                if (!isEmpty(currentChildProfile.getWord1())) {
                    custom.add(currentChildProfile.getWord1());
                }
                if (!isEmpty(currentChildProfile.getWord2())) {
                    custom.add(currentChildProfile.getWord2());
                }
                if (!isEmpty(currentChildProfile.getWord3())) {
                    custom.add(currentChildProfile.getWord3());
                }
                if (!isEmpty(currentChildProfile.getWord4())) {
                    custom.add(currentChildProfile.getWord4());
                }

                // Normalise to upper case and remove duplicates
                for (String w : custom) {
                    String upper = w.trim().toUpperCase(Locale.US);
                    if (!upper.isEmpty() && !parentWords.contains(upper)) {
                        parentWords.add(upper);
                    }
                }
            }
        }

        // If no custom words set yet, you can keep a simple fallback
        if (parentWords.isEmpty()) {
            parentWords = new ArrayList<>(Arrays.asList("YES", "NO", "BAT", "TREE"));
        }

        // Default built in words
        List<String> defaultWords = Arrays.asList(
                "MOM", "DAD", "BALL", "CAR", "CAT","BOX", "BED",
                "DOG", "FROG", "SHIP", "FISH", "SUN" , "APPLE" , "JUICE"
        );

        wordPool.clear();
        wordPool.addAll(parentWords);
        wordPool.addAll(defaultWords);
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }



    // Game loop

    private void startRound() {
        if (wordPool.isEmpty()) return;

        if (currentIndex >= wordPool.size()) {
            currentIndex = 0;
        }

        currentWord = wordPool.get(currentIndex).toUpperCase(Locale.US);
        currentIndex++;

        isParentWord = parentWords.contains(currentWord);

        targetLetters.clear();
        for (char c : currentWord.toCharArray()) {
            targetLetters.add(c);
        }
        correctPlacedCount = 0;
        roundStartTime = SystemClock.elapsedRealtime();

        if (tvTargetWord != null) {
            tvTargetWord.setText(currentWord);
        }

        buildBoardForCurrentWord();

        speakWordOnly();
        speakInstruction("Now drag the letters to spell the word.");

        tvRound.setText("Word " + currentIndex + "/" + wordPool.size());
    }

    private void buildBoardForCurrentWord() {
        targetSlotsContainer.removeAllViews();
        if (draggableRowTop != null) draggableRowTop.removeAllViews();
        if (draggableRowBottom != null) draggableRowBottom.removeAllViews();

        // ----- DYNAMIC SLOT SIZE CALCULATION -----
        int letterCount = targetLetters.size();          // e.g. 3..8
        if (letterCount == 0) return;

        // Full screen width in pixels
        int screenWidthPx = requireContext()
                .getResources()
                .getDisplayMetrics()
                .widthPixels;

        // boardContainer has 24dp start/end margin in XML, so subtract that
        int horizontalBoardMarginPx = dpToPx(24) * 2;

        // Space available inside the whiteboard for all slots + margins
        int availableWidthPx = screenWidthPx - horizontalBoardMarginPx;

        //  keep a small side margin around each slot (left + right)
        int slotSideMarginPx = dpToPx(4);          // 4dp left + 4dp right
        int totalMarginPx = slotSideMarginPx * 2 * letterCount;

        // Remaining width purely for the square tiles
        int availableForTilesPx = Math.max(0, availableWidthPx - totalMarginPx);

        // Base size for each tile so that all letters fit in one row
        int dynamicTilePx = availableForTilesPx / letterCount;

        // Clamp size so they never become too tiny or too huge
        int minTilePx = dpToPx(32);
        int maxTilePx = dpToPx(60);               // your original size
        int slotSizePx = Math.max(minTilePx, Math.min(maxTilePx, dynamicTilePx));

        // ----- CREATE GREY TARGET SLOTS -----
        for (int i = 0; i < targetLetters.size(); i++) {
            ImageView slot = new ImageView(requireContext());
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(slotSizePx, slotSizePx);
            lp.setMargins(slotSideMarginPx, 0, slotSideMarginPx, 0);
            slot.setLayoutParams(lp);

            char letter = targetLetters.get(i);
            int resId = getLetterDrawable(letter);
            slot.setImageResource(resId);
            slot.setColorFilter(
                    Color.argb(200, 200, 200, 200),
                    PorterDuff.Mode.SRC_ATOP
            );
            slot.setTag(i);
            slot.setOnDragListener(this);
            targetSlotsContainer.addView(slot);
        }

        // ----- BUILD TRAY LETTERS -----
        List<Character> trayLetters = new ArrayList<>(targetLetters);
        Set<Character> used = new HashSet<>(targetLetters);
        char cursor = 'A';

        while (trayLetters.size() < MAX_TRAY_LETTERS && cursor <= 'Z') {
            if (!used.contains(cursor)) {
                trayLetters.add(cursor);
                used.add(cursor);
            }
            cursor++;
        }

        Collections.shuffle(trayLetters);

        for (int i = 0; i < trayLetters.size(); i++) {
            char letter = trayLetters.get(i);

            ImageView tile = new ImageView(requireContext());
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(dpToPx(60), dpToPx(60));
            lp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            tile.setLayoutParams(lp);

            int resId = getLetterDrawable(letter);
            tile.setImageResource(resId);
            tile.setTag(letter);
            tile.setOnTouchListener(this);

            if (i < 4 && draggableRowTop != null) {
                draggableRowTop.addView(tile);
            } else if (draggableRowBottom != null) {
                draggableRowBottom.addView(tile);
            }
        }
    }


    private int getLetterDrawable(char letter) {
        String resName = "letter_" + Character.toLowerCase(letter);
        return getResources().getIdentifier(
                resName,
                "drawable",
                requireContext().getPackageName()
        );
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // Drag and drop

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!(v instanceof ImageView)) return false;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            Object tag = v.getTag();
            if (!(tag instanceof Character)) return false;
            char letter = (char) tag;

            ClipData.Item item = new ClipData.Item(String.valueOf(letter));
            String[] mimeTypes = {ClipDescription.MIMETYPE_TEXT_PLAIN};
            ClipData dragData = new ClipData("letter", mimeTypes, item);

            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(dragData, shadowBuilder, v, 0);
            } else {
                v.startDrag(dragData, shadowBuilder, v, 0);
            }

            speakLetter(letter);
            return true;
        }
        return false;
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return event.getClipDescription().hasMimeType(
                        ClipDescription.MIMETYPE_TEXT_PLAIN);

            case DragEvent.ACTION_DRAG_ENTERED:
                v.setAlpha(0.7f);
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                v.setAlpha(1f);
                return true;

            case DragEvent.ACTION_DROP:
                v.setAlpha(1f);
                View draggedView = (View) event.getLocalState();
                if (!(draggedView instanceof ImageView)) return false;

                char draggedLetter = event.getClipData()
                        .getItemAt(0).getText().charAt(0);

                int slotIndex = (int) v.getTag();
                char expectedLetter = targetLetters.get(slotIndex);

                sessionAttemptsLetters++;
                if (isParentWord) {
                    parentAttemptLetters++;
                } else {
                    defaultAttemptLetters++;
                }

                if (draggedLetter == expectedLetter) {
                    onCorrectLetterDrop((ImageView) v, (ImageView) draggedView, draggedLetter);
                } else {
                    onWrongLetterDrop((ImageView) draggedView);
                }
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
                v.setAlpha(1f);
                return true;

            default:
                return true;
        }
    }

    private void onCorrectLetterDrop(ImageView slot, ImageView tile, char letter) {
        sessionCorrectLetters++;
        if (isParentWord) {
            parentCorrectLetters++;
        } else {
            defaultCorrectLetters++;
        }

        if (correctSound != null) {
            correctSound.start();
        }

        int resId = getLetterDrawable(letter);
        slot.setImageResource(resId);
        slot.clearColorFilter();

        tile.setVisibility(View.INVISIBLE);
        correctPlacedCount++;

        if (correctPlacedCount == targetLetters.size()) {
            onWordCompleted();
        }

        updateStatsLabel();
    }

    private void onWrongLetterDrop(ImageView tile) {
        if (wrongSound != null) {
            wrongSound.start();
        }
        updateStatsLabel();
    }

    private void onWordCompleted() {
        long roundTime = SystemClock.elapsedRealtime() - roundStartTime;
        Log.d(TAG, "Word " + currentWord + " completed in " + roundTime + " ms");

        if (clapSound != null) {
            clapSound.start();
        }

        // Say full word when completed
        speakWordOnly();

        sessionPlays++;

        if (sessionAttemptsLetters > 0) {
            sessionScorePercent = (int) Math.round(
                    (sessionCorrectLetters * 100.0) / sessionAttemptsLetters
            );
        } else {
            sessionScorePercent = 0;
        }
        tvScore.setText("Score: " + sessionScorePercent);

        updateStatsLabel();

        targetSlotsContainer.postDelayed(this::startRound, 2000);
    }

    private void updateStatsLabel() {
        if (tvStats == null) return;
        int incorrect = Math.max(0, sessionAttemptsLetters - sessionCorrectLetters);
        String statsText = "Correct: " + sessionCorrectLetters
                + "  Incorrect: " + incorrect
                + "  Played: " + sessionPlays
                + "  Stars: " + calculateStars(sessionScorePercent);
        tvStats.setText(statsText);
    }

    // Metrics saving (unchanged)

    private void saveSessionMetricsIfNeeded() {
        if (sessionSaved) return;
        sessionSaved = true;
        saveSessionMetrics();
    }

    private void saveSessionMetrics() {
        if (childId == null || moduleIdWordBuilder == null) return;

        long sessionEndMs = SystemClock.elapsedRealtime();
        long timeSpentMs = Math.max(0L, sessionEndMs - sessionStartMs);

        if (sessionAttemptsLetters > 0) {
            sessionScorePercent = (int) Math.round(
                    (sessionCorrectLetters * 100.0) / sessionAttemptsLetters
            );
        } else {
            sessionScorePercent = 0;
        }

        int stars = calculateStars(sessionScorePercent);
        int incorrectLetters = Math.max(0, sessionAttemptsLetters - sessionCorrectLetters);

        progressService.recordWordBuilderSession(
                childId,
                moduleIdWordBuilder,
                sessionScorePercent,
                timeSpentMs,
                stars,
                sessionCorrectLetters,
                incorrectLetters,
                sessionPlays,
                parentCorrectLetters,
                parentAttemptLetters,
                defaultCorrectLetters,
                defaultAttemptLetters,
                new DataCallbacks.GenericCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Log.d(TAG, "Word Builder session saved: " + message);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Failed to save Word Builder session", e);
                    }
                }
        );

        endAnalyticsSession();
    }

    private void endAnalyticsSession() {
        if (analyticsManager == null) return;

        int stars = calculateStars(sessionScorePercent);
        int completedFlag = sessionAttemptsLetters > 0 ? 1 : 0;

        analyticsManager.endSession(
                sessionScorePercent,
                sessionCorrectLetters,
                sessionAttemptsLetters,
                stars,
                completedFlag
        );
        analyticsManager = null;
    }

    private int calculateStars(int scorePercent) {
        if (scorePercent >= 90) return 3;
        if (scorePercent >= 60) return 2;
        if (scorePercent > 0) return 1;
        return 0;
    }

    // TTS helpers

    private void speakWordOnly() {
        if (tts != null && currentWord != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(currentWord, TextToSpeech.QUEUE_FLUSH, null, "WB_WORD");
            } else {
                tts.speak(currentWord, TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    private void speakLetter(char letter) {
        if (tts == null) return;
        String text = String.valueOf(letter);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WB_LETTER");
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    private void speakInstruction(String text) {
        if (tts != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null, "WB_TTS");
            } else {
                tts.speak(text, TextToSpeech.QUEUE_ADD, null);
            }
        }
    }

    private void releaseAudio() {
        try {
            if (bgMusic != null) {
                bgMusic.release();
                bgMusic = null;
            }
            if (correctSound != null) {
                correctSound.release();
                correctSound = null;
            }
            if (wrongSound != null) {
                wrongSound.release();
                wrongSound = null;
            }
            if (clapSound != null) {
                clapSound.release();
                clapSound = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error releasing audio", e);
        }
    }
}
