package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;

/**
 * FeedbackActivity
 *
 * Simple parent feedback screen:
 *  - Parent can enter a subject and message and select a star rating.
 *  - Sends feedback using an email Intent to the admin address.
 *  - Plays soft classical background music.
 *  - Home icon (bottom right) returns to ParentDashboardActivity.
 *  - Close icon (bottom left) goes back to ParentDashboardActivity.
 */
public class FeedbackActivity extends AppCompatActivity {

    private static final String ADMIN_EMAIL = "meleciaraman@gmail.com";

    private EditText edtSubject;
    private EditText edtMessage;
    private RatingBar ratingBar;
    private TextView txtCharCount;

    private ImageButton btnHome;
    private ImageButton btnClose;
    private ImageView btnSendFeedback;

    private MediaPlayer bgMusic;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        initViews();
        initMusic();
        setupListeners();
    }

    private void initViews() {
        edtSubject = findViewById(R.id.edtFeedbackSubject);
        edtMessage = findViewById(R.id.edtFeedbackMessage);
        ratingBar = findViewById(R.id.ratingFeedback);
        txtCharCount = findViewById(R.id.txtCharCount);

        btnHome = findViewById(R.id.btnHomeIcon);
        btnClose = findViewById(R.id.btnCloseIcon);
        btnSendFeedback = findViewById(R.id.btnSendFeedback);
    }

    private void initMusic() {
        // Play classical music softly in the background
        bgMusic = MediaPlayer.create(this, R.raw.classical_music);
        if (bgMusic != null) {
            bgMusic.setLooping(true);
            bgMusic.setVolume(0.25f, 0.25f);
            bgMusic.start();
        }
    }

    private void setupListeners() {

        // Character counter for feedback message
        edtMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s != null ? s.length() : 0;
                txtCharCount.setText(length + "/500");
            }

            @Override
            public void afterTextChanged(android.text.Editable s) { }
        });

        // Send feedback as email
        btnSendFeedback.setOnClickListener(v -> {
            if (validateFields()) {
                sendFeedbackEmail();
            }
        });

        // Home icon: return to ParentDashboardActivity
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(FeedbackActivity.this, ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Close icon: return to ParentDashboardActivity
        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(FeedbackActivity.this, ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    /**
     * Validate that the subject and message are not empty
     * and that the message is at least a few characters long.
     */
    private boolean validateFields() {
        String subject = edtSubject.getText() != null ? edtSubject.getText().toString().trim() : "";
        String message = edtMessage.getText() != null ? edtMessage.getText().toString().trim() : "";

        if (TextUtils.isEmpty(subject)) {
            edtSubject.setError("Please enter a subject");
            return false;
        }

        if (TextUtils.isEmpty(message)) {
            edtMessage.setError("Please enter your feedback");
            return false;
        }

        if (message.length() < 10) {
            edtMessage.setError("Please add a little more detail");
            return false;
        }

        return true;
    }

    /**
     * Build an ACTION_SENDTO email intent to send the parent feedback
     * to the BrightBuds admin email.
     */
    private void sendFeedbackEmail() {
        String subject = edtSubject.getText().toString().trim();
        String message = edtMessage.getText().toString().trim();
        float rating = ratingBar.getRating();

        String body = "Feedback rating: " + rating + " / 5\n\n" +
                "Message:\n" + message + "\n\n" +
                "Sent from BrightBuds parent app.";

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:" + ADMIN_EMAIL));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "BrightBuds Feedback: " + subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        if (emailIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(Intent.createChooser(emailIntent, "Send feedback"));
        } else {
            Toast.makeText(this, "No email app found on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void stopMusic() {
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

    @Override
    protected void onPause() {
        super.onPause();
        if (bgMusic != null && bgMusic.isPlaying()) {
            bgMusic.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bgMusic != null && !bgMusic.isPlaying()) {
            bgMusic.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMusic();
    }
}
