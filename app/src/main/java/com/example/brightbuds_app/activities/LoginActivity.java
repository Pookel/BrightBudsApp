package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText;
    private TextInputEditText passwordEditText;
    private MaterialButton loginButton;
    private TextView registerRedirect;
    private ImageButton btnClose, btnHome;

    private FirebaseAuth auth;
    private FirebaseFirestore db;   // Firestore reference

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        initListeners();
    }

    private void initViews() {
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        registerRedirect = findViewById(R.id.registerRedirect);
        btnClose = findViewById(R.id.btnClose);
        btnHome = findViewById(R.id.btnHome);
    }

    private void initListeners() {

        // Sign in button
        loginButton.setOnClickListener(v -> loginParent());

        // "Create account" text goes to RegisterActivity
        registerRedirect.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // Close and Home both go back to Landing
        btnClose.setOnClickListener(v -> goToLanding());
        btnHome.setOnClickListener(v -> goToLanding());
    }

    private void loginParent() {
        String email = safeText(emailEditText);
        String password = safeText(passwordEditText);

        // Email validation
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Enter a valid email");
            emailEditText.requestFocus();
            return;
        }

        // Password validation
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Enter your password");
            passwordEditText.requestFocus();
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Signing in...");

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = auth.getCurrentUser();

                    if (user != null) {
                        if (!user.isEmailVerified()) {
                            // Email not verified
                            auth.signOut();
                            Toast.makeText(
                                    LoginActivity.this,
                                    "Please verify your email before signing in.",
                                    Toast.LENGTH_LONG
                            ).show();
                            resetLoginButton();
                            return;
                        }

                        // Email is verified, now check Firestore parents collection
                        checkParentDocumentAndProceed(user);
                    } else {
                        resetLoginButton();
                        Toast.makeText(
                                LoginActivity.this,
                                "Unexpected error. Please try again.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .addOnFailureListener(e -> {
                    resetLoginButton();

                    if (e instanceof FirebaseAuthInvalidUserException) {
                        // Email does not exist
                        emailEditText.setError("No account found for this email");
                        emailEditText.requestFocus();

                        Toast.makeText(
                                LoginActivity.this,
                                "No account found. Tap 'Create account' to register.",
                                Toast.LENGTH_LONG
                        ).show();

                    } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        // Wrong password
                        passwordEditText.setError("Incorrect password");
                        passwordEditText.requestFocus();

                        Toast.makeText(
                                LoginActivity.this,
                                "Incorrect password. Please try again.",
                                Toast.LENGTH_LONG
                        ).show();

                    } else {
                        // Other errors
                        Toast.makeText(
                                LoginActivity.this,
                                "Sign in failed: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    /**
     * After Firebase Auth success and email verified,
     * confirm that a parent profile exists in Firestore.
     * If it exists, go to ChildDashboardActivity.
     */
    private void checkParentDocumentAndProceed(FirebaseUser user) {
        String uid = user.getUid();

        db.collection("parents")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists()) {
                        // Parent profile exists, go to ChildSelection
                        Intent intent = new Intent(LoginActivity.this, ChildSelectionActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        // No parent document found, treat as invalid setup
                        auth.signOut();
                        resetLoginButton();

                        Toast.makeText(
                                LoginActivity.this,
                                "Account not fully set up. Please create an account first.",
                                Toast.LENGTH_LONG
                        ).show();

                        Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                        intent.putExtra("prefill_email", user.getEmail());
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(e -> {
                    resetLoginButton();
                    Toast.makeText(
                            LoginActivity.this,
                            "Could not validate account. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void resetLoginButton() {
        loginButton.setEnabled(true);
        loginButton.setText("Sign in");
    }

    private void goToLanding() {
        Intent intent = new Intent(LoginActivity.this, LandingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private String safeText(TextInputEditText editText) {
        return editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }
}
