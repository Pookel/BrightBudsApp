package com.example.brightbuds_app.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.brightbuds_app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*
 ParentDashboardActivity

 Main hub for parents:
  - Manage child profiles
  - View reports and analytics
  - Give feedback
  - Manage family photos used in Family Gallery game

 Behaviour:
  - On open, asks parent to enter their PIN if one is stored in Firestore.
  - Shows welcome message and parent email.
  - Parent can change their avatar from gallery or camera.
  - Shows an offline banner when device is offline.
  - Close goes to ChildDashboardActivity.
  - Logout signs out and goes to LandingActivity.
*/
public class ParentDashboardActivity extends AppCompatActivity {

    // UI references
    private LinearLayout btnManageProfiles;
    private LinearLayout btnReports;
    private LinearLayout btnFeedback;
    private LinearLayout btnFamilyPhotos;   // btnCamera tile in XML

    private ImageView btnHome;
    private ImageView btnClose;
    private ImageView btnMute;
    private ImageView btnLogout;
    private ImageView imgParentAvatar;

    private LinearLayout offlineBanner;
    private TextView txtOfflineMessage;
    private TextView txtWelcome;
    private TextView txtEmail;

    // Media
    private MediaPlayer bgMusicPlayer;
    private MediaPlayer cardFlipPlayer;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // State
    private boolean pinVerified = false;
    private boolean isMuted = false;

    // Activity Result for picking image
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> captureImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_dashboard);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        initViews();
        initImagePickers();
        initSounds();
        bindParentInfo();
        setupClickListeners();
        updateOfflineBanner();
        updateMuteIcon();

        // Disable dashboard actions until PIN is verified
        setDashboardEnabled(false);
        checkPinBeforeAccess();

        logParentAction("opened_parent_dashboard");
    }

    private void initViews() {
        btnManageProfiles = findViewById(R.id.btnManageProfiles);
        btnReports = findViewById(R.id.btnReports);
        btnFeedback = findViewById(R.id.btnFeedback);
        btnFamilyPhotos = findViewById(R.id.btnCamera); // Family Photos tile

        btnHome = findViewById(R.id.btnHome);
        btnClose = findViewById(R.id.btnClose);
        btnMute = findViewById(R.id.btnMute);
        btnLogout = findViewById(R.id.btnLogout);
        imgParentAvatar = findViewById(R.id.imgParentAvatar);

        offlineBanner = findViewById(R.id.offlineBanner);
        txtOfflineMessage = findViewById(R.id.txtOfflineMessage);
        txtWelcome = findViewById(R.id.txtWelcome);
        txtEmail = findViewById(R.id.txtEmail);
    }

    private void initImagePickers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null
                            && result.getData().getData() != null) {

                        Uri imageUri = result.getData().getData();

                        Glide.with(this)
                                .load(imageUri)
                                .circleCrop()
                                .into(imgParentAvatar);

                        saveAvatarToCloud(imageUri);
                        logParentAction("updated_avatar_gallery");
                    }
                }
        );

        captureImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null
                            && result.getData().getExtras() != null) {

                        Bitmap photo = (Bitmap) result.getData().getExtras().get("data");
                        if (photo != null) {
                            Glide.with(this)
                                    .load(photo)
                                    .circleCrop()
                                    .into(imgParentAvatar);

                            Uri cachedUri = saveBitmapToCache(photo);
                            if (cachedUri != null) {
                                saveAvatarToCloud(cachedUri);
                            }
                            logParentAction("updated_avatar_camera");
                        }
                    }
                }
        );
    }

    private void initSounds() {
        try {
            bgMusicPlayer = MediaPlayer.create(this, R.raw.classical_music);
            if (bgMusicPlayer != null) {
                bgMusicPlayer.setLooping(true);
                bgMusicPlayer.setVolume(0.25f, 0.25f);
            }
        } catch (Exception ignored) { }

        try {
            cardFlipPlayer = MediaPlayer.create(this, R.raw.card_flip);
        } catch (Exception ignored) { }
    }

    private void playCardFlipSound() {
        if (isMuted) return;
        try {
            if (cardFlipPlayer != null) {
                cardFlipPlayer.start();
            }
        } catch (Exception ignored) { }
    }

    private void bindParentInfo() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail();
            String name = currentUser.getDisplayName();

            if (name == null || name.trim().isEmpty()) {
                name = "Parent";
            }
            txtWelcome.setText("Welcome, " + name);

            if (email != null && !email.trim().isEmpty()) {
                txtEmail.setText(email);
            } else {
                txtEmail.setText("Signed in");
            }

            // Load avatar url from Firestore if available
            db.collection("parents")
                    .document(currentUser.getUid())
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot != null && snapshot.exists()) {
                            String avatarUrl = snapshot.getString("avatarUrl");
                            if (avatarUrl != null && !avatarUrl.trim().isEmpty()) {
                                Glide.with(this)
                                        .load(avatarUrl)
                                        .circleCrop()
                                        .into(imgParentAvatar);
                            }
                        }
                    });

        } else {
            txtWelcome.setText("Welcome");
            txtEmail.setText("Not signed in");
        }
    }

    private void updateOfflineBanner() {
        if (isOnline()) {
            offlineBanner.setVisibility(android.view.View.GONE);
        } else {
            offlineBanner.setVisibility(android.view.View.VISIBLE);
            txtOfflineMessage.setText("Offline. Some features will sync when back online.");
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void setDashboardEnabled(boolean enabled) {
        if (btnManageProfiles != null) btnManageProfiles.setEnabled(enabled);
        if (btnReports != null) btnReports.setEnabled(enabled);
        if (btnFeedback != null) btnFeedback.setEnabled(enabled);
        if (btnFamilyPhotos != null) btnFamilyPhotos.setEnabled(enabled);
        if (btnHome != null) btnHome.setEnabled(enabled);
        if (btnClose != null) btnClose.setEnabled(enabled);
        if (btnMute != null) btnMute.setEnabled(enabled);
        if (btnLogout != null) btnLogout.setEnabled(enabled);
        if (imgParentAvatar != null) imgParentAvatar.setEnabled(enabled);
    }

    private void checkPinBeforeAccess() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            goToLoginAndFinish();
            return;
        }

        String uid = currentUser.getUid();

        db.collection("parents")
                .document(uid)
                .get()
                .addOnSuccessListener(this::handlePinDocument)
                .addOnFailureListener(e -> {
                    Toast.makeText(
                            this,
                            "Could not verify PIN. Enabling dashboard.",
                            Toast.LENGTH_LONG
                    ).show();
                    pinVerified = true;
                    setDashboardEnabled(true);
                    startBackgroundMusic();
                });
    }

    private void handlePinDocument(DocumentSnapshot documentSnapshot) {
        if (documentSnapshot != null && documentSnapshot.exists()) {
            String storedPin = documentSnapshot.getString("dashboardPin");

            if (storedPin != null && !storedPin.trim().isEmpty()) {
                showPinDialog(storedPin.trim());
            } else {
                pinVerified = true;
                setDashboardEnabled(true);
                startBackgroundMusic();
            }
        } else {
            pinVerified = true;
            setDashboardEnabled(true);
            startBackgroundMusic();
        }
    }

    private void showPinDialog(String storedPin) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Parent PIN");

        final EditText input = new EditText(this);
        input.setHint("4 digit PIN");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setPadding(60, 40, 60, 40);
        builder.setView(input);

        builder.setCancelable(false);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String enteredPin = input.getText().toString().trim();
            if (enteredPin.equals(storedPin)) {
                Toast.makeText(this, "PIN accepted", Toast.LENGTH_SHORT).show();
                pinVerified = true;
                setDashboardEnabled(true);
                startBackgroundMusic();
                logParentAction("pin_verified");
            } else {
                Toast.makeText(this, "Incorrect PIN. Try again.", Toast.LENGTH_SHORT).show();
                showPinDialog(storedPin);
            }
        });

        builder.setNegativeButton("Logout", (dialog, which) -> {
            auth.signOut();
            logParentAction("pin_logout");
            goToLoginAndFinish();
        });

        builder.show();
    }

    // Logout and go to LandingActivity
    private void goToLoginAndFinish() {
        Intent intent = new Intent(ParentDashboardActivity.this, LandingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void setupClickListeners() {

        // Avatar click: choose picture from gallery or camera
        imgParentAvatar.setOnClickListener(v -> {
            if (!pinVerified) return;

            String[] options = {"Choose from gallery", "Use camera"};
            new AlertDialog.Builder(this)
                    .setTitle("Update parent picture")
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            Intent pickIntent = new Intent(
                                    Intent.ACTION_PICK,
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            );
                            pickImageLauncher.launch(pickIntent);
                        } else if (which == 1) {
                            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            captureImageLauncher.launch(cameraIntent);
                        }
                    })
                    .show();
        });

        // Manage Profiles  ManageProfilesActivity
        btnManageProfiles.setOnClickListener(v -> {
            if (!pinVerified) return;
            playCardFlipSound();
            logParentAction("open_manage_profiles");
            Intent intent = new Intent(
                    ParentDashboardActivity.this,
                    ManageProfilesActivity.class
            );
            startActivity(intent);
        });

        // Reports tile  ReportsDashboardActivity
        btnReports.setOnClickListener(v -> {
            if (!pinVerified) return;
            playCardFlipSound();
            logParentAction("open_reports_dashboard");
            Intent intent = new Intent(
                    ParentDashboardActivity.this,
                    ReportsDashboardActivity.class
            );
            startActivity(intent);
        });

        // Feedback tile  FeedbackActivity
        btnFeedback.setOnClickListener(v -> {
            if (!pinVerified) return;
            playCardFlipSound();
            logParentAction("open_feedback");
            Intent intent = new Intent(
                    ParentDashboardActivity.this,
                    FeedbackActivity.class
            );
            startActivity(intent);
        });

        // Family Photos  AddFamilyMemberActivity
        btnFamilyPhotos.setOnClickListener(v -> {
            if (!pinVerified) return;
            playCardFlipSound();
            logParentAction("open_family_photos");
            Intent intent = new Intent(
                    ParentDashboardActivity.this,
                    AddFamilyMemberActivity.class
            );
            startActivity(intent);
        });

        // Mute button
        btnMute.setOnClickListener(v -> {
            if (!pinVerified) return;
            isMuted = !isMuted;
            updateMuteIcon();
            logParentAction(isMuted ? "mute_on" : "mute_off");

            if (bgMusicPlayer != null) {
                try {
                    if (isMuted && bgMusicPlayer.isPlaying()) {
                        bgMusicPlayer.pause();
                    } else if (!isMuted && !bgMusicPlayer.isPlaying()) {
                        bgMusicPlayer.start();
                    }
                } catch (Exception ignored) { }
            }
        });

        // Logout button
        btnLogout.setOnClickListener(v -> {
            if (!pinVerified) return;
            if (!isMuted) {
                playCardFlipSound();
            }
            logParentAction("logout_clicked");
            auth.signOut();
            goToLoginAndFinish();
        });

        // Home  refresh this dashboard
        btnHome.setOnClickListener(v -> {
            if (!pinVerified) return;
            playCardFlipSound();
            logParentAction("home_clicked");
            Intent intent = new Intent(
                    ParentDashboardActivity.this,
                    ParentDashboardActivity.class
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

        // Close  go back to ChildDashboardActivity
        btnClose.setOnClickListener(v -> {
            if (!pinVerified) return;
            playCardFlipSound();
            logParentAction("close_clicked");

            Intent intent = new Intent(
                    ParentDashboardActivity.this,
                    ChildDashboardActivity.class
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void startBackgroundMusic() {
        if (isMuted) return;
        try {
            if (bgMusicPlayer != null && !bgMusicPlayer.isPlaying()) {
                bgMusicPlayer.start();
            }
        } catch (Exception ignored) { }
    }

    private void updateMuteIcon() {
        if (btnMute == null) return;
        if (isMuted) {
            btnMute.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
            btnMute.setAlpha(0.8f);
        } else {
            btnMute.clearColorFilter();
            btnMute.setAlpha(1.0f);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bgMusicPlayer != null && bgMusicPlayer.isPlaying()) {
            try {
                bgMusicPlayer.pause();
            } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOfflineBanner();
        if (pinVerified && !isMuted && bgMusicPlayer != null && !bgMusicPlayer.isPlaying()) {
            try {
                bgMusicPlayer.start();
            } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bgMusicPlayer != null) {
            try {
                if (bgMusicPlayer.isPlaying()) {
                    bgMusicPlayer.stop();
                }
            } catch (Exception ignored) { }
            bgMusicPlayer.release();
            bgMusicPlayer = null;
        }
        if (cardFlipPlayer != null) {
            try {
                cardFlipPlayer.release();
            } catch (Exception ignored) { }
            cardFlipPlayer = null;
        }
    }

    // Save an avatar image uri to Firebase Storage and store the download url in Firestore
    private void saveAvatarToCloud(Uri imageUri) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || imageUri == null) return;

        StorageReference ref = storage.getReference()
                .child("parent_avatars")
                .child(user.getUid() + ".jpg");

        ref.putFile(imageUri)
                .addOnSuccessListener(task -> ref.getDownloadUrl()
                        .addOnSuccessListener(downloadUri -> {
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("avatarUrl", downloadUri.toString());
                            db.collection("parents")
                                    .document(user.getUid())
                                    .set(updates, SetOptions.merge());
                        }))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not save avatar", Toast.LENGTH_SHORT).show());
    }

    // Save a bitmap to a cache file and return its uri
    private Uri saveBitmapToCache(Bitmap bitmap) {
        try {
            File file = new File(getCacheDir(),
                    "parent_avatar_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            return Uri.fromFile(file);
        } catch (IOException e) {
            return null;
        }
    }

    /*
     Simple activity logging helper.
     Writes an entry to Firestore with parent id, action and timestamp.
    */
    private void logParentAction(String action) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("parentId", user.getUid());
        log.put("action", action);
        log.put("timestamp", FieldValue.serverTimestamp());

        db.collection("parent_activity_logs").add(log);
    }

    /*
     Navigation summary:
      - Manage Profiles  ManageProfilesActivity
      - Reports         ReportsDashboardActivity
      - Feedback        FeedbackActivity
      - Family Photos   AddFamilyMemberActivity
      - Home            ParentDashboardActivity (refresh)
      - Close           ChildDashboardActivity
      - Logout          LandingActivity
    */
}
