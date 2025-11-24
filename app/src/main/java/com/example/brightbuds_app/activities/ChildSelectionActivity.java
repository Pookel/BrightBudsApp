package com.example.brightbuds_app.activities;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.ChildProfile;
import com.example.brightbuds_app.services.DatabaseHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

public class ChildSelectionActivity extends AppCompatActivity {

    private RecyclerView rvChildren;
    private Button btnParentSettings;     // CHANGED FROM ImageView TO Button
    private ImageView imgBubbles;

    private MediaPlayer bgPlayer;
    private MediaPlayer flipPlayer;

    private final List<ChildProfile> childProfiles = new ArrayList<>();
    private ChildAdapter childAdapter;
    private DatabaseHelper databaseHelper;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_selection);

        auth = FirebaseAuth.getInstance();
        databaseHelper = new DatabaseHelper(this);

        initViews();
        setupRecycler();
        loadChildProfilesForCurrentParent();
        setupSettingsButton();
        startBackgroundMusic();
        startBubbleAnimation();
    }

    private void initViews() {
        rvChildren = findViewById(R.id.rvChildren);
        btnParentSettings = findViewById(R.id.btnParentSettings);  // updated type
        imgBubbles = findViewById(R.id.imgBubblesSelection);
    }

    private void setupRecycler() {
        rvChildren.setLayoutManager(new GridLayoutManager(this, 3));
        childAdapter = new ChildAdapter(childProfiles, this::onChildSelected);
        rvChildren.setAdapter(childAdapter);
    }

    private void loadChildProfilesForCurrentParent() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No logged in parent. Please sign in.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        String parentId = user.getUid();
        childProfiles.clear();

        List<ChildProfile> fromDb = databaseHelper.getChildProfilesForParent(parentId);
        if (fromDb != null) {
            for (ChildProfile c : fromDb) {
                if (!isDefaultProfile(c)) {
                    childProfiles.add(c);
                }
            }
        }

        if (childProfiles.isEmpty()) {
            Toast.makeText(this,
                    "No child profiles found. Please edit a profile first.",
                    Toast.LENGTH_LONG).show();
        }

        childAdapter.notifyDataSetChanged();
    }

    private boolean isDefaultProfile(ChildProfile child) {
        if (child == null) return true;

        String name = child.getName();
        int age = child.getAge();
        String level = child.getLearningLevel();
        String w1 = child.getWord1();
        String w2 = child.getWord2();
        String w3 = child.getWord3();
        String w4 = child.getWord4();

        boolean nameLooksDefault =
                TextUtils.isEmpty(name)
                        || name.trim().isEmpty()
                        || name.trim().startsWith("Child ");

        boolean ageDefault = age <= 0;

        boolean levelDefault =
                TextUtils.isEmpty(level)
                        || "Beginner".equalsIgnoreCase(level.trim());

        boolean noWords =
                TextUtils.isEmpty(w1)
                        && TextUtils.isEmpty(w2)
                        && TextUtils.isEmpty(w3)
                        && TextUtils.isEmpty(w4);

        return nameLooksDefault && ageDefault && levelDefault && noWords;
    }

    private void setupSettingsButton() {
        btnParentSettings.setOnClickListener(v -> {
            Intent intent = new Intent(
                    ChildSelectionActivity.this,
                    ParentDashboardActivity.class
            );
            startActivity(intent);
        });
    }

    private void onChildSelected(ChildProfile child) {
        if (flipPlayer != null) {
            flipPlayer.release();
            flipPlayer = null;
        }

        flipPlayer = MediaPlayer.create(this, R.raw.card_flip);
        if (flipPlayer != null) {
            flipPlayer.setOnCompletionListener(mp -> {
                mp.release();
                flipPlayer = null;
                openChildDashboard(child);
            });
            flipPlayer.start();
        } else {
            openChildDashboard(child);
        }
    }

    private void openChildDashboard(ChildProfile child) {
        Intent intent = new Intent(ChildSelectionActivity.this, ChildDashboardActivity.class);
        intent.putExtra("child_id", child.getChildId());
        intent.putExtra("child_name", child.getName());
        intent.putExtra("child_avatar_key", child.getAvatarKey());
        startActivity(intent);
    }


    private void startBackgroundMusic() {
        if (bgPlayer == null) {
            bgPlayer = MediaPlayer.create(this, R.raw.happy_children);
            if (bgPlayer != null) {
                bgPlayer.setLooping(true);
                bgPlayer.start();
            }
        } else if (!bgPlayer.isPlaying()) {
            bgPlayer.start();
        }
    }

    private void startBubbleAnimation() {
        if (imgBubbles == null) return;

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

    @Override
    protected void onPause() {
        super.onPause();
        if (bgPlayer != null && bgPlayer.isPlaying()) {
            bgPlayer.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bgPlayer != null && !bgPlayer.isPlaying()) {
            bgPlayer.start();
        }
        loadChildProfilesForCurrentParent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bgPlayer != null) {
            bgPlayer.stop();
            bgPlayer.release();
            bgPlayer = null;
        }
        if (flipPlayer != null) {
            flipPlayer.release();
            flipPlayer = null;
        }
    }

    // RecyclerView adapter
    private static class ChildAdapter extends RecyclerView.Adapter<ChildAdapter.ChildViewHolder> {

        interface OnChildClickListener {
            void onChildClick(ChildProfile child);
        }

        private final List<ChildProfile> children;
        private final OnChildClickListener clickListener;

        ChildAdapter(List<ChildProfile> children, OnChildClickListener clickListener) {
            this.children = children;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public ChildViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_child_avatar, parent, false);
            return new ChildViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
            holder.bind(children.get(position), clickListener);
        }

        @Override
        public int getItemCount() {
            return children.size();
        }

        static class ChildViewHolder extends RecyclerView.ViewHolder {

            private final ImageView imgAvatar;
            private final android.widget.TextView tvName;

            ChildViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                imgAvatar = itemView.findViewById(R.id.imgChildAvatar);
                tvName = itemView.findViewById(R.id.tvChildName);
            }

            void bind(ChildProfile child, OnChildClickListener listener) {
                tvName.setText(child.getName());
                int avatarResId = child.resolveAvatarResId(itemView.getContext());
                imgAvatar.setImageResource(avatarResId);

                itemView.setOnClickListener(v -> listener.onChildClick(child));
            }
        }
    }
}
