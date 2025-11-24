package com.example.brightbuds_app.activities;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;

import java.util.Locale;

public class ChildDashboardActivity extends AppCompatActivity {

    private ImageView imgChildAvatar;
    private ImageView imgBubbles;

    private TextToSpeech textToSpeech;
    private SoundPool soundPool;
    private int cardFlipSoundId;

    // Background music for dashboard
    private MediaPlayer bgMusic;

    private String childId;
    private String childName;
    private String childAvatarKey;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);

        readIntentExtras();
        initViews();
        setupHeader();
        setupTts();
        setupSoundPool();
        setupBackgroundMusic();
        setupButtonClicks();
        setupHomeButton();
        startBackgroundAnimations();
    }

    private void readIntentExtras() {
        Intent intent = getIntent();
        childId = intent.getStringExtra("child_id");
        childName = intent.getStringExtra("child_name");
        childAvatarKey = intent.getStringExtra("child_avatar_key");
    }

    private void initViews() {
        imgChildAvatar = findViewById(R.id.imgChildAvatar);
        imgBubbles = findViewById(R.id.imgBubblesLanding);
    }

    private void setupHeader() {
        View tvChildNameView = findViewById(R.id.tvChildName);

        if (tvChildNameView instanceof android.widget.TextView) {
            android.widget.TextView tvChildName = (android.widget.TextView) tvChildNameView;
            if (!TextUtils.isEmpty(childName)) {
                tvChildName.setText(childName);
            }
        }

        // Resolve avatar using the key passed from ChildSelectionActivity
        if (!TextUtils.isEmpty(childAvatarKey)) {
            int resId = getResources().getIdentifier(
                    childAvatarKey,
                    "drawable",
                    getPackageName()
            );
            if (resId == 0) {
                resId = R.drawable.ic_child_avatar_placeholder;
            }
            imgChildAvatar.setImageResource(resId);
        } else {
            imgChildAvatar.setImageResource(R.drawable.ic_child_avatar_placeholder);
        }
    }

    private void setupTts() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                speakGreeting();
            }
        });
    }

    private void speakGreeting() {
        if (textToSpeech == null) return;

        String nameForSpeech = TextUtils.isEmpty(childName) ? "friend" : childName;
        String phrase = "Hello " + nameForSpeech;
        textToSpeech.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "child_greeting");
    }

    private void setupSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build();
        } else {
            soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
        }

        cardFlipSoundId = soundPool.load(this, R.raw.card_flip, 1);
    }

    // creative_fun background music
    private void setupBackgroundMusic() {
        bgMusic = MediaPlayer.create(this, R.raw.creative_fun);
        if (bgMusic != null) {
            bgMusic.setLooping(true);
            bgMusic.setVolume(0.3f, 0.3f);
            bgMusic.start();
        }
    }

    private void playCardFlip() {
        if (soundPool != null && cardFlipSoundId != 0) {
            soundPool.play(cardFlipSoundId, 1f, 1f, 1, 0, 1f);
        }
    }

    private void setupButtonClicks() {
        // Songs
        findViewById(R.id.btnABCSong).setOnClickListener(v ->
                openModuleWithSound(ABCSongActivity.class));

        findViewById(R.id.btn123Song).setOnClickListener(v ->
                openModuleWithSound(NumbersSongActivity.class));

        findViewById(R.id.btnShapesSong).setOnClickListener(v ->
                openModuleWithSound(ShapesSongActivity.class));

        // Games
        findViewById(R.id.btnMatchLetter).setOnClickListener(v ->
                openModuleWithSound(MatchLetterActivity.class));

        findViewById(R.id.btnFeedMonster).setOnClickListener(v ->
                openModuleWithSound(FeedMonsterActivity.class));

        findViewById(R.id.btnMemoryMatch).setOnClickListener(v ->
                openModuleWithSound(MemoryMatchActivity.class));

        findViewById(R.id.btnWordBuilder).setOnClickListener(v ->
                openModuleWithSound(WordBuilderActivity.class));

        findViewById(R.id.btnFamilyGallery).setOnClickListener(v ->
                openModuleWithSound(FamilyGalleryActivity.class));
    }

    private void openModuleWithSound(Class<?> activityClass) {
        playCardFlip();
        Intent intent = new Intent(ChildDashboardActivity.this, activityClass);
        intent.putExtra("child_id", childId);
        intent.putExtra("child_name", childName);
        intent.putExtra("child_avatar_key", childAvatarKey);
        startActivity(intent);
    }

    // Home icon bottom right - back to ChildSelectionActivity
    private void setupHomeButton() {
        ImageButton btnHome = findViewById(R.id.btnChildHome);
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(ChildDashboardActivity.this, ChildSelectionActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }
    }

    private void startBackgroundAnimations() {
        if (imgBubbles != null) {
            ObjectAnimator floatAnim = ObjectAnimator.ofFloat(
                    imgBubbles,
                    "translationY",
                    30f,
                    -30f
            );
            floatAnim.setDuration(9000);
            floatAnim.setRepeatCount(ValueAnimator.INFINITE);
            floatAnim.setRepeatMode(ValueAnimator.REVERSE);
            floatAnim.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }

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
    }
}
