package com.example.brightbuds_app.activities;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LandingActivity extends AppCompatActivity {

    private ImageButton btnABCSong, btn123Song, btnShapesSong;
    private ImageButton btnMatchLetter, btnFeedMonster, btnMemoryMatch;
    private ImageButton btnWordBuilder, btnFamilyGallery;
    private TextView tvLockedHint;

    private boolean isParentLoggedIn;
    private MediaPlayer bgPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_landing);

        initViews();
        setupSongButtons();
        startBubbleAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkParentLoginState();

        if (isParentLoggedIn) {
            stopBackgroundMusic();
            Intent intent = new Intent(this, ChildSelectionActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Guest mode
        setupLockedModulesForGuest();
        startBackgroundMusic();
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (bgPlayer != null && bgPlayer.isPlaying()) {
            bgPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBackgroundMusic();
    }

    private void initViews() {
        tvLockedHint = findViewById(R.id.tvLockedHint);

        btnABCSong = findViewById(R.id.btnABCSong);
        btn123Song = findViewById(R.id.btn123Song);
        btnShapesSong = findViewById(R.id.btnShapesSong);

        btnMatchLetter = findViewById(R.id.btnMatchLetter);
        btnFeedMonster = findViewById(R.id.btnFeedMonster);
        btnMemoryMatch = findViewById(R.id.btnMemoryMatch);

        btnWordBuilder = findViewById(R.id.btnWordBuilder);
        btnFamilyGallery = findViewById(R.id.btnFamilyGallery);
        // Note: no btnReports or btnSettings in this layout
    }

    private void checkParentLoginState() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            isParentLoggedIn = false;
            return;
        }

        // Require VERIFIED email
        if (!user.isEmailVerified()) {
            isParentLoggedIn = false;
            return;
        }

        // Verified + logged in
        isParentLoggedIn = true;
    }


    private void setupSongButtons() {
        // ic_abc_music → ABCSongActivity
        btnABCSong.setOnClickListener(v ->
                startActivity(new Intent(this, ABCSongActivity.class)));

        // ic_numbers → NumbersSongActivity
        btn123Song.setOnClickListener(v ->
                startActivity(new Intent(this, NumbersSongActivity.class)));

        // ic_shapes → ShapesSongActivity
        btnShapesSong.setOnClickListener(v ->
                startActivity(new Intent(this, ShapesSongActivity.class)));
    }

    private void setupLockedModulesForGuest() {
        // Hint text and its click → LoginActivity
        tvLockedHint.setText("Sign in or Register");
        tvLockedHint.setOnClickListener(v -> openParentSignInScreen());

        // One listener reused for all greyed out game buttons
        android.view.View.OnClickListener lockedClickListener = v -> openParentSignInScreen();

        btnMatchLetter.setOnClickListener(lockedClickListener);
        btnFeedMonster.setOnClickListener(lockedClickListener);
        btnMemoryMatch.setOnClickListener(lockedClickListener);
        btnWordBuilder.setOnClickListener(lockedClickListener);
        btnFamilyGallery.setOnClickListener(lockedClickListener);
    }

    private void openParentSignInScreen() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
    }



    private void startBubbleAnimation() {
        ImageView bubbles = findViewById(R.id.imgBubblesLanding);
        if (bubbles != null) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(
                    bubbles,
                    "translationY",
                    -40f,
                    40f
            );
            animator.setDuration(3000);
            animator.setRepeatCount(ObjectAnimator.INFINITE);
            animator.setRepeatMode(ObjectAnimator.REVERSE);
            animator.setInterpolator(new LinearInterpolator());
            animator.start();
        }
    }

    private void startBackgroundMusic() {
        if (bgPlayer == null) {
            bgPlayer = MediaPlayer.create(this, R.raw.creative_fun);
            if (bgPlayer != null) {
                bgPlayer.setLooping(true);
                bgPlayer.start();
            }
        } else if (!bgPlayer.isPlaying()) {
            bgPlayer.start();
        }
    }

    private void stopBackgroundMusic() {
        if (bgPlayer != null) {
            bgPlayer.stop();
            bgPlayer.release();
            bgPlayer = null;
        }
    }
}
