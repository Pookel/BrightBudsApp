package com.example.brightbuds_app.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.Progress;
import com.example.brightbuds_app.services.PDFReportService;
import com.example.brightbuds_app.utils.EncryptionUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("FieldCanBeLocal")
public class ReportGenerationActivity extends AppCompatActivity {

    private TextView txtReportStatus;
    private ProgressDialog progressDialog;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String filterChildId;
    private String parentName = "Parent User";
    private String userRole = "parent";
    private long startTimestamp = 0;
    private long endTimestamp = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_generation);

        txtReportStatus = findViewById(R.id.txtReportStatus);
        filterChildId = getIntent().getStringExtra("childId");

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Session expired. Please sign in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        loadUserProfileAndGenerateReport(user);
    }

    private void loadUserProfileAndGenerateReport(FirebaseUser user) {
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) processUserProfile(doc, user);
                    else handleMissingUserProfile(user);
                })
                .addOnFailureListener(e -> handleUserProfileLoadError(e, user));
    }

    private void processUserProfile(DocumentSnapshot doc, FirebaseUser user) {
        String roleField = doc.getString("role");
        String typeField = doc.getString("type");

        setDisplayName(doc, user);

        if ("admin".equalsIgnoreCase(roleField) || "admin".equalsIgnoreCase(typeField)) {
            userRole = "admin";
            generateAdminReport();
        } else {
            userRole = "parent";
            loadAndBuildParentReport(user.getUid(), user.getEmail());
        }
    }

    /** Decrypt parent name */
    private void setDisplayName(DocumentSnapshot doc, FirebaseUser user) {
        String encrypted = doc.getString("fullName");
        String decrypted = EncryptionUtil.decrypt(encrypted);

        if (decrypted != null && !decrypted.trim().isEmpty()) {
            parentName = decrypted.trim();
        } else if (doc.getString("name") != null) {
            parentName = doc.getString("name").trim();
        } else if (user.getEmail() != null) {
            parentName = user.getEmail().split("@")[0];
        } else {
            parentName = "Parent User";
        }
    }

    private void handleMissingUserProfile(FirebaseUser user) {
        parentName = user.getEmail() != null ? user.getEmail().split("@")[0] : "Parent User";
        loadAndBuildParentReport(user.getUid(), user.getEmail());
    }

    private void handleUserProfileLoadError(Exception e, FirebaseUser user) {
        parentName = user.getEmail() != null ? user.getEmail().split("@")[0] : "Parent User";
        loadAndBuildParentReport(user.getUid(), user.getEmail());
    }

    /** ADMIN REPORT  */
    private void generateAdminReport() {
        showProgressDialog("Generating system-wide admin report...");
        Query progressQuery = buildProgressQueryWithDateRange(db.collection("child_progress"));

        progressQuery.get().addOnSuccessListener(progressSnap -> {
            List<Progress> progressList = extractProgressRecords(progressSnap);
            if (progressList.isEmpty()) {
                handleEmptyProgressData();
                return;
            }
            loadChildAndParentDataForAdminReport(progressList);
        }).addOnFailureListener(this::handleProgressDataLoadError);
    }

    private void loadChildAndParentDataForAdminReport(List<Progress> progressList) {
        db.collection("child_profiles").get().addOnSuccessListener(childrenSnap -> {
            Map<String, String> childNames = extractChildNames(childrenSnap);
            db.collection("users").get().addOnSuccessListener(usersSnap -> {
                Map<String, String> parentNames = extractParentNames(usersSnap);
                generateAdminPDFReport(progressList, childNames, parentNames);
            }).addOnFailureListener(this::handleParentDataLoadError);
        }).addOnFailureListener(this::handleChildDataLoadError);
    }

    private void generateAdminPDFReport(List<Progress> progressList,
                                        Map<String, String> childNames,
                                        Map<String, String> parentNames) {
        for (Progress p : progressList) {
            if (p.getModuleId() != null) p.setModuleId(getModuleTitle(p.getModuleId()));
        }

        PDFReportService pdfService = new PDFReportService(this);
        pdfService.setDateRange(startTimestamp, endTimestamp);

        pdfService.generateAdminProgressReport(
                progressList,
                computeAverageScore(progressList),
                computeTotalPlays(progressList),
                parentName + " (Admin)",
                childNames,
                parentNames,
                new PDFReportService.PDFCallback() {
                    @Override
                    public void onSuccess(String filePath) {
                        handleReportSuccess("✅ Admin report saved:\n" + filePath,
                                "Admin report generated successfully!");
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleReportFailure("❌ Failed: " + e.getMessage(), e);
                    }
                });
    }

    /** PARENT REPORT  */
    private void loadAndBuildParentReport(String userId, String email) {
        showProgressDialog("Generating family progress report...");

        db.collection("child_profiles")
                .whereEqualTo("parentId", userId)
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(childrenSnap -> {
                    if (childrenSnap.isEmpty()) {
                        handleNoChildrenFound();
                        return;
                    }
                    Map<String, String> childNames = extractChildNames(childrenSnap);
                    loadProgressDataForParentReport(userId, email, childNames);
                })
                .addOnFailureListener(this::handleChildrenLoadError);
    }

    private void loadProgressDataForParentReport(String userId, String email, Map<String, String> childNames) {
        Query progressQuery = buildParentProgressQuery(userId, email);

        progressQuery.get().addOnSuccessListener(progressSnap -> {
            List<Progress> progressList = extractProgressRecords(progressSnap);
            if (progressList.isEmpty()) {
                handleNoProgressDataFound();
                return;
            }

            for (Progress p : progressList) {
                if (p.getModuleId() != null) p.setModuleId(getModuleTitle(p.getModuleId()));
            }

            generateParentPDFReport(progressList, childNames);
        }).addOnFailureListener(this::handleProgressLoadError);
    }

    private void generateParentPDFReport(List<Progress> progressList, Map<String, String> childNames) {
        PDFReportService pdfService = new PDFReportService(this);
        pdfService.setDateRange(startTimestamp, endTimestamp);

        pdfService.generateProgressReport(
                progressList,
                computeAverageScore(progressList),
                computeTotalPlays(progressList),
                parentName,
                childNames,
                new PDFReportService.PDFCallback() {
                    @Override
                    public void onSuccess(String filePath) {
                        handleReportSuccess("✅ Family report saved:\n" + filePath,
                                "Family report generated successfully!");
                    }

                    @Override
                    public void onFailure(Exception e) {
                        handleReportFailure("❌ Parent report generation failed: " + e.getMessage(), e);
                    }
                });
    }

    /**  UTILITIES  */

    private Query buildProgressQueryWithDateRange(Query baseQuery) {
        if (startTimestamp > 0 && endTimestamp > 0) {
            return baseQuery.whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                    .whereLessThanOrEqualTo("timestamp", endTimestamp);
        }
        return baseQuery;
    }

    private Query buildParentProgressQuery(String userId, String email) {
        if (email != null && !email.isEmpty()) {
            return db.collection("child_progress")
                    .whereIn("parentId", Arrays.asList(userId, email));
        }
        return db.collection("child_progress").whereEqualTo("parentId", userId);
    }

    private List<Progress> extractProgressRecords(QuerySnapshot snapshot) {
        return snapshot.getDocuments().stream()
                .map(doc -> doc.toObject(Progress.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, String> extractChildNames(QuerySnapshot snapshot) {
        Map<String, String> names = new HashMap<>();
        for (var doc : snapshot.getDocuments()) {
            String decrypted = EncryptionUtil.decrypt(doc.getString("name"));
            names.put(doc.getId(), (decrypted != null && !decrypted.isEmpty()) ? decrypted : "Child");
        }
        return names;
    }

    private Map<String, String> extractParentNames(QuerySnapshot snapshot) {
        Map<String, String> names = new HashMap<>();
        for (var doc : snapshot.getDocuments()) {
            String decrypted = EncryptionUtil.decrypt(doc.getString("fullName"));
            names.put(doc.getId(), (decrypted != null && !decrypted.isEmpty()) ? decrypted : "Parent");
        }
        return names;
    }

    private String getModuleTitle(String id) {
        if (id == null) return "Unknown Module";
        switch (id) {
            case "module_abc_song": return "ABC Song";
            case "module_123_song": return "123 Song";
            case "module_feed_the_monster": return "Feed the Monster";
            case "module_match_the_letter": return "Match the Letter";
            case "module_memory_match": return "Memory Match";
            case "module_word_builder": return "Word Builder";
            case "module_my_family": return "My Family Album";
            default: return id;
        }
    }

    private int computeTotalPlays(List<Progress> list) {
        return (list == null) ? 0 : list.size();
    }

    private double computeAverageScore(List<Progress> list) {
        if (list == null || list.isEmpty()) return 0.0;
        return list.stream().mapToDouble(Progress::getScore).average().orElse(0.0);
    }

    private void showProgressDialog(String msg) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(msg);
        progressDialog.setCancelable(false);
        progressDialog.show();
    }

    private void handleReportSuccess(String text, String toast) {
        runOnUiThread(() -> {
            progressDialog.dismiss();
            txtReportStatus.setText(text);
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
        });
    }

    private void handleReportFailure(String msg, Exception e) {
        runOnUiThread(() -> {
            progressDialog.dismiss();
            txtReportStatus.setText(msg);
            Log.e("ReportGen", "PDF generation error", e);
        });
    }

    private void handleEmptyProgressData() { progressDialog.dismiss(); txtReportStatus.setText("⚠️ No progress data found."); }
    private void handleNoChildrenFound() { progressDialog.dismiss(); txtReportStatus.setText("⚠️ No child profiles found."); }
    private void handleNoProgressDataFound() { progressDialog.dismiss(); txtReportStatus.setText("⚠️ No learning progress found."); }
    private void handleProgressDataLoadError(Exception e) { progressDialog.dismiss(); txtReportStatus.setText("⚠️ Failed to load progress: " + e.getMessage()); }
    private void handleChildrenLoadError(Exception e) { progressDialog.dismiss(); txtReportStatus.setText("⚠️ Failed to load children: " + e.getMessage()); }
    private void handleChildDataLoadError(Exception e) { progressDialog.dismiss(); txtReportStatus.setText("⚠️ Failed to load child data: " + e.getMessage()); }
    private void handleParentDataLoadError(Exception e) { progressDialog.dismiss(); txtReportStatus.setText("⚠️ Failed to load parent data: " + e.getMessage()); }
    private void handleProgressLoadError(Exception e) { progressDialog.dismiss(); txtReportStatus.setText("⚠️ Failed to load progress data: " + e.getMessage()); }
}
