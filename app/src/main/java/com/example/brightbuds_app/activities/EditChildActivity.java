package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.adapters.AvatarSelectionAdapter;
import com.example.brightbuds_app.interfaces.DataCallbacks;
import com.example.brightbuds_app.models.ChildProfile;
import com.example.brightbuds_app.services.ChildProfileDAO;
import com.example.brightbuds_app.services.ChildProfileService;
import com.google.firebase.auth.FirebaseAuth;


public class EditChildActivity extends AppCompatActivity {

    private static final String TAG = "EditChildActivity";

    // Form fields
    private EditText etName;
    private EditText etAge;
    private AutoCompleteTextView spinnerGender;
    private AutoCompleteTextView spinnerLevel;
    private EditText etWord1;
    private EditText etWord2;
    private EditText etWord3;
    private EditText etWord4;
    private Button btnSave;

    // Avatar gallery
    private RecyclerView recyclerAvatars;
    private AvatarSelectionAdapter avatarAdapter;

    // Avatar drawables
    private final int[] avatarResIds = new int[]{
            R.drawable.avatar_1,
            R.drawable.avatar_2,
            R.drawable.avatar_3,
            R.drawable.avatar_4,
            R.drawable.avatar_5,
            R.drawable.avatar_6
    };

    // Selected avatar key saved in ChildProfile
    private String selectedAvatarKey = "ic_child_avatar_placeholder";

    // Data and services
    private ChildProfileDAO childDao;
    private ChildProfileService childService;
    private ChildProfile currentChild;

    // Firestore id for this child (one per profile)
    private String childId;

    // Optional: which card slot launched this screen (1 to 5)
    private int slotIndex = -1;

    // Background music
    private MediaPlayer bgMusic;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_child);

        childDao = new ChildProfileDAO(this);
        childService = new ChildProfileService();

        // Read extras from ManageProfilesActivity
        Intent startIntent = getIntent();
        childId = startIntent.getStringExtra("childId");
        slotIndex = startIntent.getIntExtra("slotIndex", -1);

        initViews();
        setupDropdowns();
        setupAvatarGrid();

        // Load from local DB if this profile already exists
        currentChild = null;
        if (!TextUtils.isEmpty(childId)) {
            currentChild = childDao.getChildById(childId);
        }

        if (currentChild == null) {
            // New local profile
            currentChild = new ChildProfile();
            currentChild.setChildId(childId);   // may be null for brand new child
            currentChild.setAvatarKey(selectedAvatarKey);

            // Hint like "Child 1" when opened from slot 1
            if (slotIndex > 0) {
                etName.setHint("Child " + slotIndex);
            }
        } else {
            // Existing profile: populate fields from local model
            populateFormFromModel(currentChild);
        }

        setupSaveButton();
        startBackgroundMusic();
    }

    private void initViews() {
        etName = findViewById(R.id.editTextChildName);
        etAge = findViewById(R.id.editTextChildAge);
        spinnerGender = findViewById(R.id.spinnerGender);
        spinnerLevel = findViewById(R.id.spinnerLearningLevel);
        etWord1 = findViewById(R.id.editTextWord1);
        etWord2 = findViewById(R.id.editTextWord2);
        etWord3 = findViewById(R.id.editTextWord3);
        etWord4 = findViewById(R.id.editTextWord4);
        btnSave = findViewById(R.id.buttonSaveChild);

        recyclerAvatars = findViewById(R.id.recyclerAvatars);

        ImageView btnHome = findViewById(R.id.btnHomeIcon);
        ImageView btnClose = findViewById(R.id.btnCloseIcon);

        // Home icon -> Parent dashboard
        if (btnHome != null) {
            btnHome.setOnClickListener(v -> {
                Intent intent = new Intent(EditChildActivity.this, ParentDashboardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }

        // Close icon -> Manage profiles
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                Intent intent = new Intent(EditChildActivity.this, ManageProfilesActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        }
    }

    /**
     * Configure gender and level dropdowns.
     * Uses arrays.xml: gender_options, learning_levels.
     * Forces the dropdown to open when the field is tapped or focused.
     */
    private void setupDropdowns() {
        // Gender
        ArrayAdapter<CharSequence> genderAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.gender_options,
                android.R.layout.simple_dropdown_item_1line
        );
        spinnerGender.setAdapter(genderAdapter);
        spinnerGender.setOnClickListener(v -> spinnerGender.showDropDown());
        spinnerGender.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) spinnerGender.showDropDown();
        });

        // Learning level
        ArrayAdapter<CharSequence> levelAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.learning_levels,
                android.R.layout.simple_dropdown_item_1line
        );
        spinnerLevel.setAdapter(levelAdapter);
        spinnerLevel.setOnClickListener(v -> spinnerLevel.showDropDown());
        spinnerLevel.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) spinnerLevel.showDropDown();
        });
    }

    /**
     * Avatar gallery as a 3 column grid.
     * Six avatars show as two rows of three.
     */
    private void setupAvatarGrid() {
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        recyclerAvatars.setLayoutManager(gridLayoutManager);
        recyclerAvatars.setNestedScrollingEnabled(false);
        recyclerAvatars.setHasFixedSize(false);

        avatarAdapter = new AvatarSelectionAdapter(
                avatarResIds,
                selectedAvatarKey,
                (resId, key) -> {
                    // key is something like "avatar_1"
                    selectedAvatarKey = key;
                }
        );

        recyclerAvatars.setAdapter(avatarAdapter);
    }

    /**
     * Fill form inputs from an existing ChildProfile.
     */
    private void populateFormFromModel(ChildProfile child) {
        if (child == null) return;

        if (!TextUtils.isEmpty(child.getName())) {
            etName.setText(child.getName());
        }
        if (child.getAge() > 0) {
            etAge.setText(String.valueOf(child.getAge()));
        }
        if (!TextUtils.isEmpty(child.getGender())) {
            spinnerGender.setText(child.getGender(), false);
        }
        if (!TextUtils.isEmpty(child.getLearningLevel())) {
            spinnerLevel.setText(child.getLearningLevel(), false);
        }

        if (!TextUtils.isEmpty(child.getWord1())) etWord1.setText(child.getWord1());
        if (!TextUtils.isEmpty(child.getWord2())) etWord2.setText(child.getWord2());
        if (!TextUtils.isEmpty(child.getWord3())) etWord3.setText(child.getWord3());
        if (!TextUtils.isEmpty(child.getWord4())) etWord4.setText(child.getWord4());

        if (!TextUtils.isEmpty(child.getAvatarKey())) {
            selectedAvatarKey = child.getAvatarKey();
        } else {
            selectedAvatarKey = "ic_child_avatar_placeholder";
        }

        if (avatarAdapter != null) {
            avatarAdapter.setSelectedKey(selectedAvatarKey);
        }
    }

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> saveChild());
    }

    /**
     * Main save logic:
     * validate, update model, save locally, sync via ChildProfileService.
     */
    private void saveChild() {
        String name = etName.getText().toString().trim();
        String ageStr = etAge.getText().toString().trim();
        String gender = spinnerGender.getText().toString().trim();
        String level = spinnerLevel.getText().toString().trim();
        String word1 = etWord1.getText().toString().trim();
        String word2 = etWord2.getText().toString().trim();
        String word3 = etWord3.getText().toString().trim();
        String word4 = etWord4.getText().toString().trim();

        if (!validateInputs(name, ageStr, gender, level, word1, word2, word3, word4)) {
            return;
        }

        int age = Integer.parseInt(ageStr);

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        if (currentChild == null) {
            currentChild = new ChildProfile();
        }

        // Keep existing Firestore id if known from the intent
        if (!TextUtils.isEmpty(childId)) {
            currentChild.setChildId(childId);
        }

        // Copy form fields into the model
        currentChild.setName(name);
        currentChild.setAge(age);
        currentChild.setGender(gender);
        currentChild.setLearningLevel(level);
        currentChild.setWord1(word1);
        currentChild.setWord2(word2);
        currentChild.setWord3(word3);
        currentChild.setWord4(word4);
        currentChild.setActive(true);

        if (TextUtils.isEmpty(selectedAvatarKey)) {
            selectedAvatarKey = "ic_child_avatar_placeholder";
        }
        currentChild.setAvatarKey(selectedAvatarKey);

        // Ensure parentId and childId are set before saving
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this,
                    "Session expired, please log in again.",
                    Toast.LENGTH_LONG).show();
            btnSave.setEnabled(true);
            btnSave.setText("Save Child Profile");
            return;
        }

        String parentId = auth.getCurrentUser().getUid();
        currentChild.setParentId(parentId);


        // If this is a brand new child, generate a stable id per slot
        if (TextUtils.isEmpty(currentChild.getChildId())) {
            int safeSlot = (slotIndex > 0 && slotIndex <= 5) ? slotIndex : 1;
            String newChildId = parentId + "_child_" + safeSlot;
            currentChild.setChildId(newChildId);
            // Also update the field used earlier when reopening
            childId = newChildId;
        }

        // Now childId is never null and matches the pattern parentId_child_slot
        childDao.upsertChild(currentChild);

        // Sync to Firestore
        childService.updateChildProfile(currentChild, new DataCallbacks.GenericCallback() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    Toast.makeText(EditChildActivity.this,
                            "Child profile updated",
                            Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Child Profile");
                    goBackToManageProfiles();
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to update child in Firestore", e);
                runOnUiThread(() -> {
                    Toast.makeText(EditChildActivity.this,
                            "Saved locally but could not sync online now.",
                            Toast.LENGTH_LONG).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Child Profile");
                    goBackToManageProfiles();
                });
            }
        });
    }


    private void goBackToManageProfiles() {
        Intent intent = new Intent(EditChildActivity.this, ManageProfilesActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private boolean validateInputs(String name,
                                   String ageStr,
                                   String gender,
                                   String level,
                                   String word1,
                                   String word2,
                                   String word3,
                                   String word4) {

        if (TextUtils.isEmpty(name)) {
            etName.setError("Enter your child's name");
            return false;
        }
        if (name.length() < 2) {
            etName.setError("Name must be at least 2 characters");
            return false;
        }

        if (TextUtils.isEmpty(ageStr)) {
            etAge.setError("Enter age");
            return false;
        }
        int age;
        try {
            age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            etAge.setError("Age must be a number");
            return false;
        }
        if (age < 1 || age > 10) {
            etAge.setError("Age must be between 1 and 10");
            return false;
        }

        if (TextUtils.isEmpty(gender) || gender.equals("Select gender") || gender.equals("Select Gender")) {
            spinnerGender.setError("Select gender");
            return false;
        }

        if (TextUtils.isEmpty(level) || level.equals("Select level") || level.equals("Select Level")) {
            spinnerLevel.setError("Select learning level");
            return false;
        }

        EditText[] wordFields = new EditText[]{etWord1, etWord2, etWord3, etWord4};
        String[] words = new String[]{word1, word2, word3, word4};

        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (!TextUtils.isEmpty(w) && w.length() > 8) {
                wordFields[i].setError("Word must be 8 letters or less");
                return false;
            }
        }

        return true;
    }

    // Background music helpers

    private void startBackgroundMusic() {
        bgMusic = MediaPlayer.create(this, R.raw.classical_music);
        if (bgMusic != null) {
            bgMusic.setLooping(true);
            bgMusic.setVolume(0.2f, 0.2f);
            bgMusic.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bgMusic != null && bgMusic.isPlaying()) {
            try {
                bgMusic.pause();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bgMusic != null && !bgMusic.isPlaying()) {
            try {
                bgMusic.start();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
