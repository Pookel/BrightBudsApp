package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText fullNameEditText, emailEditText, passwordEditText,
            confirmPasswordEditText, pinEditText, confirmPinEditText;

    private MaterialButton registerButton, btnViewTerms;
    private TextView loginRedirect;
    private CheckBox checkboxAnalyticsConsent, checkboxAcceptTerms;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        initListeners();
    }

    private void initViews() {
        fullNameEditText = findViewById(R.id.fullNameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        pinEditText = findViewById(R.id.pinEditText);
        confirmPinEditText = findViewById(R.id.confirmPinEditText);

        registerButton = findViewById(R.id.registerButton);
        loginRedirect = findViewById(R.id.loginRedirect);
        checkboxAnalyticsConsent = findViewById(R.id.checkboxAnalyticsConsent);
        checkboxAcceptTerms = findViewById(R.id.checkboxAcceptTerms);
        btnViewTerms = findViewById(R.id.btnViewTerms);
    }

    private void initListeners() {

        registerButton.setOnClickListener(v -> registerParent());

        // "Sign in" sends user to LoginActivity
        loginRedirect.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        btnViewTerms.setOnClickListener(v ->
                startActivity(new Intent(RegisterActivity.this, TermsConditionsActivity.class)));
    }

    private void registerParent() {

        String name = text(fullNameEditText);
        String email = text(emailEditText);
        String password = text(passwordEditText);
        String confirmPassword = text(confirmPasswordEditText);
        String pin = text(pinEditText);
        String confirmPin = text(confirmPinEditText);

        boolean consentAnalytics = checkboxAnalyticsConsent.isChecked();
        boolean acceptedTerms = checkboxAcceptTerms.isChecked();

        // Validations
        if (TextUtils.isEmpty(name)) {
            fullNameEditText.setError("Enter your full name");
            fullNameEditText.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email");
            emailEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError("Minimum 6 characters");
            passwordEditText.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            confirmPasswordEditText.requestFocus();
            return;
        }

        if (pin.length() != 4 || !pin.matches("\\d{4}")) {
            pinEditText.setError("PIN must be 4 digits");
            pinEditText.requestFocus();
            return;
        }

        if (!pin.equals(confirmPin)) {
            confirmPinEditText.setError("PINs do not match");
            confirmPinEditText.requestFocus();
            return;
        }

        if (!consentAnalytics || !acceptedTerms) {
            Toast.makeText(this,
                    "Please accept required options first",
                    Toast.LENGTH_LONG).show();
            return;
        }

        registerButton.setEnabled(false);
        registerButton.setText("Creating account...");

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {

                    FirebaseUser user = auth.getCurrentUser();

                    if (user != null) {

                        user.updateProfile(
                                new UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build()
                        );

                        saveParentProfile(user.getUid(), name, email, pin, consentAnalytics);
                    }

                })
                .addOnFailureListener(e -> onRegisterFailed(e.getMessage()));
    }

    private void saveParentProfile(String uid,
                                   String name,
                                   String email,
                                   String pin,
                                   boolean consentAnalytics) {

        Map<String, Object> parent = new HashMap<>();
        parent.put("fullName", name);
        parent.put("email", email);
        parent.put("dashboardPin", pin);
        parent.put("analyticsConsent", consentAnalytics);
        parent.put("createdAt", FieldValue.serverTimestamp());
        parent.put("lastUpdated", FieldValue.serverTimestamp());

        db.collection("parents")
                .document(uid)
                .set(parent)
                .addOnSuccessListener(unused -> {

                    // SUCCESS → Go DIRECTLY to ParentDashboard
                    Intent i = new Intent(RegisterActivity.this, ParentDashboardActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> onRegisterFailed(e.getMessage()));
    }

    private void onRegisterFailed(String message) {
        registerButton.setEnabled(true);
        registerButton.setText("Create account");
        Toast.makeText(this, "Registration failed: " + message, Toast.LENGTH_LONG).show();
    }

    private String text(TextInputEditText t) {
        return t.getText() == null ? "" : t.getText().toString().trim();
    }
}
