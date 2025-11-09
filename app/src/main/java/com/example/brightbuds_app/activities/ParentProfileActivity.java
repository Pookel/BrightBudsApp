package com.example.brightbuds_app.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.brightbuds_app.R;
import com.example.brightbuds_app.utils.EncryptionUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ParentProfileActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String TAG = "ParentProfileActivity";

    private TextView txtUserName, txtUserEmail, txtUserRole;
    private ImageView imgUserAvatar;
    private MaterialButton btnEditProfile, btnChangePassword, btnTermsConditions, btnLogout;
    private MaterialCardView cardPersonalInfo, cardAccountSettings;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private FirebaseUser currentUser;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_profile);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        loadUserData();
        setupClickListeners();
    }

    private void initializeViews() {
        txtUserName = findViewById(R.id.txtUserName);
        txtUserEmail = findViewById(R.id.txtUserEmail);
        txtUserRole = findViewById(R.id.txtUserRole);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnTermsConditions = findViewById(R.id.btnTermsConditions);
        btnLogout = findViewById(R.id.btnLogout);
        cardPersonalInfo = findViewById(R.id.cardPersonalInfo);
        cardAccountSettings = findViewById(R.id.cardAccountSettings);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
    }

    /** Load decrypted user data */
    private void loadUserData() {
        db.collection("users").document(currentUser.getUid())
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String encryptedName = document.getString("fullName");
                        String decryptedName = EncryptionUtil.decrypt(encryptedName);
                        String displayName = (decryptedName != null && !decryptedName.isEmpty())
                                ? decryptedName
                                : currentUser.getDisplayName();

                        String encryptedEmail = document.getString("email");
                        String decryptedEmail = EncryptionUtil.decrypt(encryptedEmail);
                        String email = (decryptedEmail != null && !decryptedEmail.isEmpty())
                                ? decryptedEmail
                                : currentUser.getEmail();

                        txtUserName.setText(displayName != null ? displayName : "Parent");
                        txtUserEmail.setText(email != null ? email : "—");

                        String userType = document.getString("type");
                        txtUserRole.setText(userType != null && userType.equalsIgnoreCase("admin")
                                ? "Administrator" : "Parent");

                        String avatarUrl = document.getString("avatarUrl");
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(avatarUrl)
                                    .placeholder(R.drawable.ic_child_placeholder)
                                    .circleCrop()
                                    .into(imgUserAvatar);
                        }
                    } else {
                        txtUserName.setText("Parent");
                        txtUserRole.setText("Parent");
                        txtUserEmail.setText(currentUser.getEmail());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to load user data", e);
                    txtUserName.setText("Parent");
                    txtUserEmail.setText(currentUser.getEmail());
                    txtUserRole.setText("Parent");
                });

        if (currentUser.getPhotoUrl() != null) {
            Glide.with(this)
                    .load(currentUser.getPhotoUrl())
                    .placeholder(R.drawable.ic_child_placeholder)
                    .circleCrop()
                    .into(imgUserAvatar);
        }
    }

    private void setupClickListeners() {
        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnTermsConditions.setOnClickListener(v ->
                startActivity(new Intent(this, TermsConditionsActivity.class)));
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Intent i = new Intent(this, LandingActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
        imgUserAvatar.setOnClickListener(v -> changeProfilePicture());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    /** Save encrypted updates */
    private void updateProfile(String name, String email) {
        progressDialog.setMessage("Updating profile...");
        progressDialog.show();

        String encName = EncryptionUtil.encrypt(name);
        String encEmail = EncryptionUtil.encrypt(email);

        UserProfileChangeRequest updates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name).build();

        currentUser.updateProfile(updates)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentUser.updateEmail(email)
                                .addOnCompleteListener(eTask -> {
                                    Map<String, Object> updateData = new HashMap<>();
                                    updateData.put("fullName", encName);
                                    updateData.put("email", encEmail);
                                    updateData.put("name", encName);

                                    db.collection("users").document(currentUser.getUid())
                                            .update(updateData)
                                            .addOnSuccessListener(unused -> {
                                                progressDialog.dismiss();
                                                loadUserData();
                                                Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show();
                                            })
                                            .addOnFailureListener(e -> {
                                                progressDialog.dismiss();
                                                Toast.makeText(this, "Firestore update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                            });
                                });
                    } else {
                        progressDialog.dismiss();
                        Toast.makeText(this, "Failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showEditProfileDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Edit Profile");
        View v = getLayoutInflater().inflate(R.layout.dialog_edit_profile, null);
        TextInputEditText etName = v.findViewById(R.id.etDisplayName);
        TextInputEditText etEmail = v.findViewById(R.id.etEmail);

        etName.setText(txtUserName.getText());
        etEmail.setText(txtUserEmail.getText());
        dialog.setView(v);

        dialog.setPositiveButton("Save", (d, w) -> {
            String newName = etName.getText().toString().trim();
            String newEmail = etEmail.getText().toString().trim();
            if (TextUtils.isEmpty(newName) || TextUtils.isEmpty(newEmail)) {
                Toast.makeText(this, "All fields required", Toast.LENGTH_SHORT).show();
            } else {
                updateProfile(newName, newEmail);
            }
        });
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void showChangePasswordDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Change Password");
        View v = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        TextInputEditText current = v.findViewById(R.id.etCurrentPassword);
        TextInputEditText newPass = v.findViewById(R.id.etNewPassword);
        TextInputEditText confirm = v.findViewById(R.id.etConfirmPassword);
        dialog.setView(v);

        dialog.setPositiveButton("Change", (d, w) -> {
            String cur = current.getText().toString().trim();
            String np = newPass.getText().toString().trim();
            String cf = confirm.getText().toString().trim();

            if (TextUtils.isEmpty(cur) || TextUtils.isEmpty(np) || TextUtils.isEmpty(cf)) {
                Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!np.equals(cf)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            if (np.length() < 6) {
                Toast.makeText(this, "Password too short", Toast.LENGTH_SHORT).show();
                return;
            }
            changePassword(cur, np);
        });
        dialog.setNegativeButton("Cancel", null);
        dialog.show();
    }

    private void changePassword(String currentPassword, String newPassword) {
        progressDialog.setMessage("Changing password...");
        progressDialog.show();

        AuthCredential cred = EmailAuthProvider.getCredential(currentUser.getEmail(), currentPassword);
        currentUser.reauthenticate(cred)
                .addOnSuccessListener(aVoid -> currentUser.updatePassword(newPassword)
                        .addOnCompleteListener(task -> {
                            progressDialog.dismiss();
                            if (task.isSuccessful())
                                Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show();
                            else
                                Toast.makeText(this, "Password update failed", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Incorrect current password", Toast.LENGTH_SHORT).show();
                });
    }

    private void changeProfilePicture() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Select Profile Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_IMAGE_REQUEST && res == RESULT_OK && data != null && data.getData() != null) {
            uploadProfilePicture(data.getData());
        }
    }

    private void uploadProfilePicture(Uri uri) {
        progressDialog.setMessage("Uploading...");
        progressDialog.show();
        StorageReference ref = storage.getReference().child("profile_pictures/" + currentUser.getUid() + "_" + UUID.randomUUID());
        ref.putFile(uri)
                .addOnSuccessListener(t -> ref.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    currentUser.updateProfile(new UserProfileChangeRequest.Builder().setPhotoUri(downloadUri).build());
                    db.collection("users").document(currentUser.getUid()).update("avatarUrl", downloadUri.toString());
                    Glide.with(this).load(downloadUri).circleCrop().into(imgUserAvatar);
                    progressDialog.dismiss();
                    Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show();
                }))
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog.isShowing()) progressDialog.dismiss();
    }
}
