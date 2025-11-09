package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.models.ChildProfile;
import com.example.brightbuds_app.services.ChildProfileService;
import com.example.brightbuds_app.utils.EncryptionUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * RoleSelectionActivity:
 * Displays all children linked to the logged-in parent,
 * with decrypted names, progress %, and star ratings
 * based on the total of 7 available learning modules.
 */
public class RoleSelectionActivity extends AppCompatActivity {

    private static final String TAG = "RoleSelectionActivity";

    private FirebaseFirestore db;
    private LinearLayout profilesContainer;
    private TextView txtTitle;
    private ExecutorService executor;
    private static final int TOTAL_MODULES = 7; // Total learning modules available

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        db = FirebaseFirestore.getInstance();
        executor = Executors.newSingleThreadExecutor();

        profilesContainer = findViewById(R.id.profilesContainer);
        txtTitle = findViewById(R.id.txtChooseRole);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LandingActivity.class));
            finish();
            return;
        }

        if (!currentUser.isEmailVerified()) {
            showEmailVerificationRequired();
            return;
        }

        setupUI(currentUser);
    }

    private void setupUI(FirebaseUser currentUser) {
        txtTitle.setText("Choose Your Profile");
        findViewById(R.id.btnParent).setVisibility(View.GONE);
        findViewById(R.id.btnChild).setVisibility(View.GONE);
        loadChildrenProfiles(currentUser.getUid());
    }

    /** Load all children linked to this parent using the secure service */
    private void loadChildrenProfiles(String parentId) {
        Log.d(TAG, "🔄 Loading children for parent: " + parentId);
        profilesContainer.removeAllViews();
        showLoadingState();

        ChildProfileService childProfileService = new ChildProfileService();
        childProfileService.getChildrenForCurrentParent(new DataCallbacks.ChildrenListCallback() {
            @Override
            public void onSuccess(List<ChildProfile> children) {
                Log.d(TAG, "✅ Loaded " + children.size() + " children from Firestore");
                runOnUiThread(() -> {
                    profilesContainer.removeAllViews();
                    if (children.isEmpty()) {
                        showNoChildrenState();
                    } else {
                        displayChildrenProfiles(children);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "❌ Error loading children", e);
                runOnUiThread(() -> showErrorState());
            }
        });
    }

    /** Display decrypted children with accurate progress and stars */
    private void displayChildrenProfiles(List<ChildProfile> children) {
        LayoutInflater inflater = LayoutInflater.from(this);
        profilesContainer.removeAllViews();

        for (ChildProfile child : children) {
            CardView card = (CardView) inflater.inflate(R.layout.item_child_selection_card, profilesContainer, false);
            TextView txtChildName = card.findViewById(R.id.txtChildName);
            TextView txtLearningLevel = card.findViewById(R.id.txtLearningLevel);
            TextView txtStars = card.findViewById(R.id.txtStars);
            TextView txtProgress = card.findViewById(R.id.txtProgress);

            // Decrypt the name safely
            String decryptedName = EncryptionUtil.decrypt(child.getName());
            String displayName = (decryptedName != null && !decryptedName.isEmpty())
                    ? decryptedName
                    : (child.getDisplayName() != null ? child.getDisplayName() : "Child");

            String level = (child.getLearningLevel() != null && !child.getLearningLevel().isEmpty())
                    ? child.getLearningLevel()
                    : "Beginner";

            // Calculate progress & stars from completed modules
            int completedModules = child.getCompletedModules(); // make sure this exists in model
            double progress = ((double) completedModules / TOTAL_MODULES) * 100;
            int progressRounded = (int) Math.min(progress, 100);

            double stars = ((double) completedModules / TOTAL_MODULES) * 5;
            int starsRounded = Math.min((int) Math.round(stars), 5);

            txtChildName.setText(displayName);
            txtLearningLevel.setText("Level: " + level);
            txtStars.setText("⭐ " + starsRounded + " Star" + (starsRounded == 1 ? "" : "s"));
            txtProgress.setText("Progress: " + progressRounded + "%");

            card.setOnClickListener(v -> {
                Log.d(TAG, "👆 Selected child: " + displayName);
                openChildDashboard(child);
            });

            profilesContainer.addView(card);
            Log.d(TAG, "   ✅ Added card for " + displayName +
                    " | Completed=" + completedModules +
                    " | Progress=" + progressRounded + "%" +
                    " | Stars=" + starsRounded);
        }

        addParentProfileCard(inflater);
    }

    /** Navigate to child dashboard */
    private void openChildDashboard(ChildProfile child) {
        Intent intent = new Intent(this, ChildDashboardActivity.class);
        intent.putExtra("childId", child.getChildId());
        intent.putExtra("childName", EncryptionUtil.decrypt(child.getName()));
        startActivity(intent);
    }

    /** Add parent dashboard card at the end */
    private void addParentProfileCard(LayoutInflater inflater) {
        CardView parentCard = (CardView) inflater.inflate(R.layout.item_parent_profile_card, profilesContainer, false);
        parentCard.setOnClickListener(v -> {
            Log.d(TAG, "👤 Parent profile selected");
            startActivity(new Intent(this, ParentDashboardActivity.class));
            finish();
        });
        profilesContainer.addView(parentCard);
    }

    // Visual States
    private void showLoadingState() {
        View loading = LayoutInflater.from(this)
                .inflate(R.layout.item_loading_children, profilesContainer, false);
        profilesContainer.addView(loading);
    }

    private void showNoChildrenState() {
        View emptyView = LayoutInflater.from(this)
                .inflate(R.layout.item_empty_children, profilesContainer, false);
        profilesContainer.addView(emptyView);
        emptyView.findViewById(R.id.btnAddFirstChild)
                .setOnClickListener(v ->
                        startActivity(new Intent(this, ChildProfileActivity.class))
                );
    }

    private void showErrorState() {
        View errorView = LayoutInflater.from(this)
                .inflate(R.layout.item_error_children, profilesContainer, false);
        profilesContainer.addView(errorView);
        errorView.findViewById(R.id.btnRetry)
                .setOnClickListener(v -> {
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) loadChildrenProfiles(currentUser.getUid());
                });
    }

    private void showEmailVerificationRequired() {
        Toast.makeText(this, "Please verify your email before continuing", Toast.LENGTH_LONG).show();
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(this, LandingActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        moveTaskToBack(true);
    }
}
