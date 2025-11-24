package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.ChildProfile;
import com.example.brightbuds_app.services.ChildProfileDAO;
import com.example.brightbuds_app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageProfilesActivity extends AppCompatActivity {

    /**
     * Represents one of the five fixed slots in the UI.
     */
    private static class ChildSlot {
        int slotIndex;
        CardView container;
        ImageView avatar;
        TextView name;
        TextView info;
        ImageView editIcon;
        ImageView deleteIcon;

        String childId;
        String avatarKey;
        int age;
        String level;
        String wordsDisplay;
    }

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ChildProfileDAO childDao;

    private final List<ChildSlot> slots = new ArrayList<>(5);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_profiles);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        childDao = new ChildProfileDAO(this);

        initSlots();
        loadChildrenForParent();

        ImageView btnCloseIcon = findViewById(R.id.btnCloseIcon);
        ImageView btnHomeIcon = findViewById(R.id.btnHomeIcon);

// Close -> back to child selection screen
        btnCloseIcon.setOnClickListener(v -> {
            Intent intent = new Intent(ManageProfilesActivity.this, ChildSelectionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

// Home -> parent dashboard
        btnHomeIcon.setOnClickListener(v -> {
            Intent intent = new Intent(ManageProfilesActivity.this, ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChildrenForParent();
    }

    /**
     * Wire up the five fixed UI slots and their click listeners.
     * Tapping any card or its edit icon always opens EditChildActivity.
     * Empty slots pass a null childId so EditChildActivity will create a new profile.
     */
    private void initSlots() {

        ChildSlot s1 = createSlot(1,
                R.id.cardChild1, R.id.imgChild1, R.id.txtChildName1,
                R.id.txtChildInfo1, R.id.imgEdit1, R.id.imgDelete1);

        ChildSlot s2 = createSlot(2,
                R.id.cardChild2, R.id.imgChild2, R.id.txtChildName2,
                R.id.txtChildInfo2, R.id.imgEdit2, R.id.imgDelete2);

        ChildSlot s3 = createSlot(3,
                R.id.cardChild3, R.id.imgChild3, R.id.txtChildName3,
                R.id.txtChildInfo3, R.id.imgEdit3, R.id.imgDelete3);

        ChildSlot s4 = createSlot(4,
                R.id.cardChild4, R.id.imgChild4, R.id.txtChildName4,
                R.id.txtChildInfo4, R.id.imgEdit4, R.id.imgDelete4);

        ChildSlot s5 = createSlot(5,
                R.id.cardChild5, R.id.imgChild5, R.id.txtChildName5,
                R.id.txtChildInfo5, R.id.imgEdit5, R.id.imgDelete5);

        slots.clear();
        slots.add(s1);
        slots.add(s2);
        slots.add(s3);
        slots.add(s4);
        slots.add(s5);

        for (ChildSlot slot : slots) {

            // Card tap -> always open EditChildActivity.
            slot.container.setOnClickListener(v ->
                    openEditChildActivity(slot.childId, slot.slotIndex));

            // Edit icon tap -> same as card tap.
            slot.editIcon.setOnClickListener(v ->
                    openEditChildActivity(slot.childId, slot.slotIndex));

            // Delete/reset icon
            slot.deleteIcon.setOnClickListener(v -> {
                if (slot.childId == null) {
                    // No Firestore profile yet, just reset visuals.
                    resetSlotUiToDefaults(slot);
                    Toast.makeText(this,
                            "Slot reset to defaults.",
                            Toast.LENGTH_SHORT).show();
                } else {
                    confirmResetChild(slot);
                }
            });
        }
    }

    private ChildSlot createSlot(int index, int containerId, int avatarId,
                                 int nameId, int infoId, int editId, int deleteId) {

        ChildSlot slot = new ChildSlot();
        slot.slotIndex = index;

        slot.container = findViewById(containerId);
        slot.avatar = findViewById(avatarId);
        slot.name = findViewById(nameId);
        slot.info = findViewById(infoId);
        slot.editIcon = findViewById(editId);
        slot.deleteIcon = findViewById(deleteId);

        resetSlotUiToDefaults(slot);

        return slot;
    }

    /**
     * Default UI for an empty slot: generic name, age 0, beginner, placeholder avatar.
     */
    private void resetSlotUiToDefaults(ChildSlot slot) {
        slot.childId = null;
        slot.age = 0;
        slot.level = "Beginner";
        slot.wordsDisplay = "";
        slot.avatarKey = "ic_child_avatar_placeholder";

        slot.name.setText("Child " + slot.slotIndex);
        slot.info.setText("Age 0 • Beginner • Words: ");
        slot.avatar.setImageResource(R.drawable.ic_child_placeholder);
    }

    private void confirmResetChild(ChildSlot slot) {
        new AlertDialog.Builder(this)
                .setTitle("Reset profile")
                .setMessage("Reset this profile back to default values on this device? Game progress will still be linked to this child.")
                .setPositiveButton("Reset", (dialog, which) -> resetChildInFirestore(slot))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Clears the non-personal fields in Firestore and resets UI.
     * Local personalised details can still be changed again from EditChildActivity.
     */
    private void resetChildInFirestore(ChildSlot slot) {

        if (slot.childId == null) {
            resetSlotUiToDefaults(slot);
            return;
        }

        Map<String, Object> resetMap = new HashMap<>();
        resetMap.put("avatarKey", "ic_child_avatar_placeholder");
        resetMap.put("active", true);

        db.collection(Constants.COLLECTION_CHILD_PROFILES)
                .document(slot.childId)
                .set(resetMap, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this,
                            "Profile reset to defaults.",
                            Toast.LENGTH_SHORT).show();
                    resetSlotUiToDefaults(slot);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to reset profile: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    /**
     * Open EditChildActivity.
     * If childId is null -> EditChild will create a brand new profile (with new Firestore id).
     * We also pass slotIndex so hints can show "Child 1", "Child 2", etc.
     */
    private void openEditChildActivity(@Nullable String childId, int slotIndex) {
        Intent intent = new Intent(this, EditChildActivity.class);
        if (!TextUtils.isEmpty(childId)) {
            intent.putExtra("childId", childId);
        }
        intent.putExtra("slotIndex", slotIndex);
        startActivity(intent);
    }

    /**
     * Load all child profile ids for this parent from Firestore.
     * Personalised info comes from local SQLite per childId.
     */
    private void loadChildrenForParent() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this,
                    "Session expired, please log in again.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        db.collection(Constants.COLLECTION_CHILD_PROFILES)
                .whereEqualTo("parentId", user.getUid())
                .limit(5)
                .get()
                .addOnSuccessListener(this::bindChildrenToSlots)
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Could not load profiles: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    /**
     * For each Firestore child document:
     *  - put its id into a slot
     *  - look up personalised info from local SQLite
     *  - update name, age, words and avatar from the local ChildProfile data
     */
    private void bindChildrenToSlots(QuerySnapshot snapshot) {

        // Reset all to default first
        for (ChildSlot slot : slots) {
            slot.childId = null;
            resetSlotUiToDefaults(slot);
        }

        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }

        int index = 0;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            if (index >= slots.size()) break;

            ChildSlot slot = slots.get(index);
            slot.childId = doc.getId();

            // Load personalised data from local SQLite
            ChildProfile localProfile = childDao.getChildById(slot.childId);

            String displayName = "Child " + slot.slotIndex;
            int displayAge = 0;
            String displayLevel = "Beginner";
            String avatarKey = "ic_child_avatar_placeholder";
            String words = "";

            if (localProfile != null) {
                if (!TextUtils.isEmpty(localProfile.getName())) {
                    displayName = localProfile.getName();
                }
                if (localProfile.getAge() > 0) {
                    displayAge = localProfile.getAge();
                }
                if (!TextUtils.isEmpty(localProfile.getLearningLevel())) {
                    displayLevel = localProfile.getLearningLevel();
                }
                if (!TextUtils.isEmpty(localProfile.getAvatarKey())) {
                    avatarKey = localProfile.getAvatarKey();
                }

                List<String> wordList = new ArrayList<>();
                if (!TextUtils.isEmpty(localProfile.getWord1())) wordList.add(localProfile.getWord1());
                if (!TextUtils.isEmpty(localProfile.getWord2())) wordList.add(localProfile.getWord2());
                if (!TextUtils.isEmpty(localProfile.getWord3())) wordList.add(localProfile.getWord3());
                if (!TextUtils.isEmpty(localProfile.getWord4())) wordList.add(localProfile.getWord4());
                words = TextUtils.join(", ", wordList);
            }

            slot.age = displayAge;
            slot.level = displayLevel;
            slot.wordsDisplay = words;
            slot.avatarKey = avatarKey;

            slot.name.setText(displayName);
            slot.info.setText("Age " + displayAge + " • " + displayLevel + " • Words: " + words);

            slot.avatar.setImageResource(resolveAvatarResId(avatarKey));

            index++;
        }
    }

    private int resolveAvatarResId(String avatarKey) {
        if (TextUtils.isEmpty(avatarKey)) {
            return R.drawable.ic_child_placeholder;
        }
        int resId = getResources().getIdentifier(avatarKey, "drawable", getPackageName());
        return resId == 0 ? R.drawable.ic_child_placeholder : resId;
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, ParentDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
