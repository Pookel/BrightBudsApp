package com.example.brightbuds_app.activities;

import android.os.Bundle;
import android.text.Html;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;

public class TermsConditionsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private ImageButton btnClose;   // NEW
    private TextView txtTermsContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_conditions);

        initializeViews();
        setupListeners();
        loadTermsContent();
    }

    /** Initialize UI components */
    private void initializeViews() {
        btnBack = findViewById(R.id.btnBack);
        btnClose = findViewById(R.id.btnClose);     // NEW
        txtTermsContent = findViewById(R.id.txtTermsContent);
    }

    /** Handle button click actions */
    private void setupListeners() {

        // Both buttons just close this screen and return to RegisterActivity
        btnBack.setOnClickListener(v -> finish());
        btnClose.setOnClickListener(v -> finish());   // NEW
    }

    /** Load and render HTML Terms & Conditions text */
    private void loadTermsContent() {
        String termsHtml = getString(R.string.terms_and_conditions_content);
        txtTermsContent.setText(Html.fromHtml(termsHtml, Html.FROM_HTML_MODE_LEGACY));
    }
}
