package com.example.brightbuds_app.activities;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LauncherActivity extends AppCompatActivity {

    private MediaPlayer clapPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        ImageView imgBubbles = findViewById(R.id.imgBubbles);

        // Play clap sound once when splash shows
        clapPlayer = MediaPlayer.create(this, R.raw.clap_sound);
        if (clapPlayer != null) {
            clapPlayer.start();
        }

        // Simple floating animation for bubbles
        if (imgBubbles != null) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(
                    imgBubbles,
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

        // Short splash then decide where to go
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateNext, 1500);
    }

    private void navigateNext() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        Intent intent;
        if (currentUser != null && currentUser.isEmailVerified()) {
            // Parent is still logged in and verified, go straight to child selection
            intent = new Intent(LauncherActivity.this, ChildSelectionActivity.class);
        } else {
            // No logged in parent, show landing screen (guest mode with songs etc.)
            intent = new Intent(LauncherActivity.this, LandingActivity.class);
        }

        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clapPlayer != null) {
            clapPlayer.stop();
            clapPlayer.release();
            clapPlayer = null;
        }
    }
}
