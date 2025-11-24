package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.brightbuds_app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ParentReportActivity
 *
 * Screen for parents to:
 *  - select a child
 *  - view progress and analytics tables
 *  - generate a branded BrightBuds PDF
 *  - email the PDF
 *  - go home or back using footer buttons
 */
public class ParentReportActivity extends AppCompatActivity {

    private static final String TAG = "ParentReportActivity";

    private FirebaseFirestore db;
    private String parentId;

    // UI
    private Spinner spinnerChild;
    private Button btnViewTables;
    private ImageButton btnDownloadPdf;
    private Button btnEmailPdf;
    private ImageButton btnHome;
    private ImageButton btnClose;
    private LinearLayout layoutTablesContainer;
    private TextView tvTablesPlaceholder;
    private ProgressBar progressBar; // we will create it in code and attach to top of tables

    // Data
    private final List<ChildItem> childItems = new ArrayList<>();
    private final List<ProgressRow> progressList = new ArrayList<>();
    private final List<AnalyticsRow> analyticsList = new ArrayList<>();

    // Last generated PDF
    private File lastPdfFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_report);

        db = FirebaseFirestore.getInstance();
        parentId = FirebaseAuth.getInstance().getUid();

        spinnerChild = findViewById(R.id.spinnerChild);
        btnViewTables = findViewById(R.id.btnViewTables);
        btnDownloadPdf = findViewById(R.id.btnDownloadPdf);
        btnEmailPdf = findViewById(R.id.btnEmailPdf);
        btnHome = findViewById(R.id.btnHome);
        btnClose = findViewById(R.id.btnClose);
        layoutTablesContainer = findViewById(R.id.layoutTablesContainer);
        tvTablesPlaceholder = findViewById(R.id.tvTablesPlaceholder);

        // Small progress bar at top of tables container
        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        pbParams.setMargins(0, 8, 0, 8);
        progressBar.setLayoutParams(pbParams);
        progressBar.setVisibility(View.GONE);
        layoutTablesContainer.addView(progressBar, 0);

        // Add icon to email button (email_icon webp)
        btnEmailPdf.setCompoundDrawablesWithIntrinsicBounds(R.drawable.email_icon, 0, 0, 0);
        btnEmailPdf.setCompoundDrawablePadding(8);

        // Load children for spinner
        loadChildrenForSpinner();

        btnViewTables.setOnClickListener(v -> onViewTablesClicked());
        btnDownloadPdf.setOnClickListener(v -> onDownloadPdfClicked());
        btnEmailPdf.setOnClickListener(v -> onEmailPdfClicked());

        btnClose.setOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed()
        );

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(ParentReportActivity.this, ParentDashboardActivity.class);
            startActivity(intent);
            finish();
        });
    }

    // region Child loading

    private void loadChildrenForSpinner() {
        if (parentId == null) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            return;
        }

        childItems.clear();

        db.collection("child_profiles")
                .whereEqualTo("parentId", parentId)
                .orderBy("name")
                .get()
                .addOnSuccessListener(snapshot -> {
                    // default placeholder names to EXCLUDE from reports
                    List<String> defaultNames = Arrays.asList(
                            "Child 1", "Child 2", "Child 3", "Child 4", "Child 5"
                    );

                    for (DocumentSnapshot doc : snapshot) {
                        String id = doc.getId();
                        String name = doc.getString("name");
                        if (name == null) {
                            name = "";
                        }
                        name = name.trim();

                        // Skip blank or default placeholder profiles
                        if (name.isEmpty() || defaultNames.contains(name)) {
                            continue;
                        }

                        childItems.add(new ChildItem(id, name));
                    }

                    if (childItems.isEmpty()) {
                        Toast.makeText(this,
                                "No completed child profiles found for this parent",
                                Toast.LENGTH_SHORT).show();
                    }

                    List<String> names = new ArrayList<>();
                    for (ChildItem item : childItems) {
                        names.add(item.name);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            names
                    );
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerChild.setAdapter(adapter);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load children", e);
                    Toast.makeText(this, "Failed to load children", Toast.LENGTH_SHORT).show();
                });
    }

    private ChildItem getSelectedChild() {
        int index = spinnerChild.getSelectedItemPosition();
        if (index < 0 || index >= childItems.size()) {
            return null;
        }
        return childItems.get(index);
    }

    // endregion

    // region View tables

    private void onViewTablesClicked() {
        ChildItem child = getSelectedChild();
        if (child == null) {
            Toast.makeText(this, "Please select a child first", Toast.LENGTH_SHORT).show();
            return;
        }

        progressList.clear();
        analyticsList.clear();
        lastPdfFile = null;

        showLoading(true);
        layoutTablesContainer.removeViews(1, layoutTablesContainer.getChildCount() - 1);
        tvTablesPlaceholder.setText("Loading progress and analytics data...");

        loadProgressThenAnalytics(child.id);
    }

    private void loadProgressThenAnalytics(String childId) {
        db.collection("child_progress")
                .whereEqualTo("child_id", childId)
                .orderBy("module_id", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot) {
                        try {
                            ProgressRow row = new ProgressRow();
                            row.moduleId = safeString(doc.getString("module_id"));
                            Long score = doc.getLong("lastScore");
                            Long stars = doc.getLong("stars");
                            Long plays = doc.getLong("totalPlays");
                            Long timeMs = doc.getLong("totalTimeMs");

                            row.score = score != null ? score.intValue() : 0;
                            row.stars = stars != null ? stars.intValue() : 0;
                            row.plays = plays != null ? plays.intValue() : 0;
                            row.timeMs = timeMs != null ? timeMs : 0L;

                            progressList.add(row);
                        } catch (Exception e) {
                            Log.e(TAG, "Error mapping progress doc", e);
                        }
                    }

                    loadAnalytics(childId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_progress", e);
                    showLoading(false);
                    Toast.makeText(this, "Failed to load progress", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadAnalytics(String childId) {
        db.collection("child_analytics")
                .whereEqualTo("child_id", childId)
                .orderBy("module_id", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot) {
                        try {
                            AnalyticsRow row = new AnalyticsRow();
                            row.moduleId = safeString(doc.getString("module_id"));
                            Double avgScore = doc.getDouble("avgScore");
                            Double accuracy = doc.getDouble("accuracy");
                            Double avgTimeMs = doc.getDouble("avgTimeMs");

                            row.avgScore = avgScore != null ? avgScore : 0.0;
                            row.accuracyFraction = accuracy != null ? accuracy : 0.0;
                            row.avgTimeMs = avgTimeMs != null ? avgTimeMs : 0.0;

                            analyticsList.add(row);
                        } catch (Exception e) {
                            Log.e(TAG, "Error mapping analytics doc", e);
                        }
                    }

                    showLoading(false);
                    buildTablesUI();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_analytics", e);
                    showLoading(false);
                    Toast.makeText(this, "Failed to load analytics", Toast.LENGTH_SHORT).show();
                });
    }

    private void buildTablesUI() {
        layoutTablesContainer.removeViews(1, layoutTablesContainer.getChildCount() - 1);

        if (progressList.isEmpty() && analyticsList.isEmpty()) {
            tvTablesPlaceholder.setText("No progress or analytics data yet.");
            return;
        }

        tvTablesPlaceholder.setText("");

        // Progress section
        TextView headingProgress = new TextView(this);
        headingProgress.setText("Progress summary");
        headingProgress.setTextSize(16f);
        headingProgress.setTextColor(Color.BLACK);
        headingProgress.setPadding(0, 8, 0, 4);
        headingProgress.setTypeface(headingProgress.getTypeface(), android.graphics.Typeface.BOLD);
        layoutTablesContainer.addView(headingProgress);

        LinearLayout headerRow = createTableRow();
        headerRow.addView(createCell("Module", 1.5f, true));
        headerRow.addView(createCell("Score", 1f, true));
        headerRow.addView(createCell("Stars", 1f, true));
        headerRow.addView(createCell("Plays", 1f, true));
        headerRow.addView(createCell("Time (min)", 1.2f, true));
        layoutTablesContainer.addView(headerRow);

        for (ProgressRow row : progressList) {
            LinearLayout r = createTableRow();
            double minutes = row.timeMs / 60000.0;
            String timeStr = String.format(Locale.US, "%.1f", minutes);

            r.addView(createCell(row.moduleId, 1.5f, false));
            r.addView(createCell(row.score + "%", 1f, false));
            r.addView(createCell(String.valueOf(row.stars), 1f, false));
            r.addView(createCell(String.valueOf(row.plays), 1f, false));
            r.addView(createCell(timeStr, 1.2f, false));
            layoutTablesContainer.addView(r);
        }

        // Spacer
        addSpacer(12);

        // Analytics section
        TextView headingAnalytics = new TextView(this);
        headingAnalytics.setText("Analytics summary");
        headingAnalytics.setTextSize(16f);
        headingAnalytics.setTextColor(Color.BLACK);
        headingAnalytics.setPadding(0, 8, 0, 4);
        headingAnalytics.setTypeface(headingAnalytics.getTypeface(), android.graphics.Typeface.BOLD);
        layoutTablesContainer.addView(headingAnalytics);

        LinearLayout headerRow2 = createTableRow();
        headerRow2.addView(createCell("Module", 1.5f, true));
        headerRow2.addView(createCell("Avg score", 1f, true));
        headerRow2.addView(createCell("Accuracy", 1.2f, true));
        headerRow2.addView(createCell("Avg time (s)", 1.2f, true));
        layoutTablesContainer.addView(headerRow2);

        for (AnalyticsRow row : analyticsList) {
            LinearLayout r = createTableRow();

            double accuracyPercent = row.accuracyFraction * 100.0;
            double avgTimeSeconds = row.avgTimeMs / 1000.0;

            String avgScoreStr = String.format(Locale.US, "%.1f%%", row.avgScore);
            String accuracyStr = String.format(Locale.US, "%.1f%%", accuracyPercent);
            String timeStr = String.format(Locale.US, "%.1f", avgTimeSeconds);

            r.addView(createCell(row.moduleId, 1.5f, false));
            r.addView(createCell(avgScoreStr, 1f, false));
            r.addView(createCell(accuracyStr, 1.2f, false));
            r.addView(createCell(timeStr, 1.2f, false));
            layoutTablesContainer.addView(r);
        }
    }

    private LinearLayout createTableRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, 4, 0, 4);
        return row;
    }

    private TextView createCell(String text, float weight, boolean header) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(Color.BLACK);
        tv.setPadding(4, 2, 4, 2);
        if (header) {
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return tv;
    }

    private void addSpacer(int dp) {
        View spacer = new View(this);
        int px = Math.round(dp * getResources().getDisplayMetrics().density);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px));
        layoutTablesContainer.addView(spacer);
    }

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    // endregion

    // region PDF

    private void onDownloadPdfClicked() {
        ChildItem child = getSelectedChild();
        if (child == null) {
            Toast.makeText(this, "Please select a child first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (progressList.isEmpty() && analyticsList.isEmpty()) {
            Toast.makeText(this, "Load tables before generating PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        lastPdfFile = generatePdfReport(child);
        if (lastPdfFile != null) {
            Toast.makeText(this, "PDF saved: " + lastPdfFile.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void onEmailPdfClicked() {
        ChildItem child = getSelectedChild();
        if (child == null) {
            Toast.makeText(this, "Please select a child first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastPdfFile == null || !lastPdfFile.exists()) {
            lastPdfFile = generatePdfReport(child);
        }
        if (lastPdfFile == null) {
            Toast.makeText(this, "Unable to generate PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        sharePdfByEmail(lastPdfFile, child);
    }

    private File generatePdfReport(ChildItem child) {
        PdfDocument pdf = new PdfDocument();

        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 40;
        int headerHeight = 90;

        Paint paint = new Paint();
        Paint headerPaint = new Paint();
        Paint whitePanelPaint = new Paint();
        Paint linePaint = new Paint();

        headerPaint.setColor(Color.parseColor("#1976D2"));
        headerPaint.setStyle(Paint.Style.FILL);

        whitePanelPaint.setColor(Color.WHITE);
        whitePanelPaint.setStyle(Paint.Style.FILL);
        whitePanelPaint.setAlpha(235);

        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(1f);

        Bitmap bgBitmap = null;
        Bitmap pageBgScaled = null;
        Bitmap logoBitmap = null;
        Bitmap logoScaled = null;

        try {
            bgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bluestar_background);
            if (bgBitmap != null) {
                pageBgScaled = Bitmap.createScaledBitmap(bgBitmap, pageWidth, pageHeight, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading bluestar_background", e);
        }

        try {
            logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_brightbuds_logo);
            if (logoBitmap != null) {
                logoScaled = Bitmap.createScaledBitmap(logoBitmap, 70, 70, true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading ic_brightbuds_logo", e);
        }

        int[] yHolder = new int[1];

        PdfDocument.Page page = startNewPage(
                pdf,
                pageWidth,
                pageHeight,
                margin,
                headerHeight,
                paint,
                headerPaint,
                whitePanelPaint,
                pageBgScaled,
                logoScaled,
                yHolder
        );
        Canvas canvas = page.getCanvas();
        int y = yHolder[0];

        paint.setFakeBoldText(false);
        paint.setColor(Color.BLACK);
        paint.setTextSize(14f);

        String childLine = "Child: " + child.name;
        String dateLine = "Date: " + DateFormat.getDateInstance().format(new Date());

        canvas.drawText(childLine, margin + 10, y, paint);
        y += 20;
        canvas.drawText(dateLine, margin + 10, y, paint);
        y += 30;

        // Progress summary
        paint.setFakeBoldText(true);
        paint.setTextSize(16f);
        canvas.drawText("Progress summary", margin + 10, y, paint);
        y += 20;

        paint.setTextSize(12f);
        canvas.drawLine(margin + 10, y, pageWidth - margin - 10, y, linePaint);
        y += 18;

        canvas.drawText("Module", margin + 10, y, paint);
        canvas.drawText("Score", margin + 160, y, paint);
        canvas.drawText("Stars", margin + 240, y, paint);
        canvas.drawText("Plays", margin + 310, y, paint);
        canvas.drawText("Time (min)", margin + 400, y, paint);
        y += 16;
        canvas.drawLine(margin + 10, y, pageWidth - margin - 10, y, linePaint);
        y += 16;

        paint.setFakeBoldText(false);

        for (ProgressRow row : progressList) {
            if (y > pageHeight - margin - 80) {
                pdf.finishPage(page);
                page = startNewPage(
                        pdf,
                        pageWidth,
                        pageHeight,
                        margin,
                        headerHeight,
                        paint,
                        headerPaint,
                        whitePanelPaint,
                        pageBgScaled,
                        logoScaled,
                        yHolder
                );
                canvas = page.getCanvas();
                y = yHolder[0];
            }

            double minutes = row.timeMs / 60000.0;
            String timeStr = String.format(Locale.US, "%.1f", minutes);

            canvas.drawText(row.moduleId, margin + 10, y, paint);
            canvas.drawText(row.score + "%", margin + 160, y, paint);
            canvas.drawText(String.valueOf(row.stars), margin + 240, y, paint);
            canvas.drawText(String.valueOf(row.plays), margin + 310, y, paint);
            canvas.drawText(timeStr, margin + 400, y, paint);
            y += 18;
        }

        y += 30;

        // Analytics summary
        paint.setFakeBoldText(true);
        paint.setTextSize(16f);
        canvas.drawText("Analytics summary", margin + 10, y, paint);
        y += 20;

        paint.setTextSize(12f);
        canvas.drawLine(margin + 10, y, pageWidth - margin - 10, y, linePaint);
        y += 18;

        canvas.drawText("Module", margin + 10, y, paint);
        canvas.drawText("Avg score", margin + 150, y, paint);
        canvas.drawText("Accuracy", margin + 260, y, paint);
        canvas.drawText("Avg time (s)", margin + 380, y, paint);
        y += 16;
        canvas.drawLine(margin + 10, y, pageWidth - margin - 10, y, linePaint);
        y += 16;

        paint.setFakeBoldText(false);

        for (AnalyticsRow row : analyticsList) {
            if (y > pageHeight - margin - 80) {
                pdf.finishPage(page);
                page = startNewPage(
                        pdf,
                        pageWidth,
                        pageHeight,
                        margin,
                        headerHeight,
                        paint,
                        headerPaint,
                        whitePanelPaint,
                        pageBgScaled,
                        logoScaled,
                        yHolder
                );
                canvas = page.getCanvas();
                y = yHolder[0];
            }

            double accuracyPercent = row.accuracyFraction * 100.0;
            double avgTimeSeconds = row.avgTimeMs / 1000.0;

            String avgScoreStr = String.format(Locale.US, "%.1f%%", row.avgScore);
            String accuracyStr = String.format(Locale.US, "%.1f%%", accuracyPercent);
            String timeStr = String.format(Locale.US, "%.1f", avgTimeSeconds);

            canvas.drawText(row.moduleId, margin + 10, y, paint);
            canvas.drawText(avgScoreStr, margin + 150, y, paint);
            canvas.drawText(accuracyStr, margin + 260, y, paint);
            canvas.drawText(timeStr, margin + 380, y, paint);
            y += 18;
        }

        pdf.finishPage(page);

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = getFilesDir();
        }

        String safeName = child.name.replaceAll("\\s+", "_");
        String fileName = "BrightBuds_Report_" + safeName + ".pdf";
        File file = new File(dir, fileName);

        try (FileOutputStream out = new FileOutputStream(file)) {
            pdf.writeTo(out);
            pdf.close();
            return file;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save PDF", e);
            pdf.close();
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private PdfDocument.Page startNewPage(
            PdfDocument pdf,
            int pageWidth,
            int pageHeight,
            int margin,
            int headerHeight,
            Paint paint,
            Paint headerPaint,
            Paint whitePanelPaint,
            Bitmap pageBgScaled,
            Bitmap logoScaled,
            int[] yHolder
    ) {
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdf.getPages().size() + 1).create();
        PdfDocument.Page page = pdf.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        if (pageBgScaled != null) {
            canvas.drawBitmap(pageBgScaled, 0, 0, null);
        }

        Rect panelRect = new Rect(
                margin / 2,
                headerHeight,
                pageWidth - margin / 2,
                pageHeight - margin / 2
        );
        canvas.drawRoundRect(
                panelRect.left,
                panelRect.top,
                panelRect.right,
                panelRect.bottom,
                20,
                20,
                whitePanelPaint
        );

        Rect headerRect = new Rect(0, 0, pageWidth, headerHeight);
        canvas.drawRect(headerRect, headerPaint);

        if (logoScaled != null) {
            int logoX = margin;
            int logoY = headerRect.centerY() - (logoScaled.getHeight() / 2);
            canvas.drawBitmap(logoScaled, logoX, logoY, null);
        }

        paint.setColor(Color.WHITE);
        paint.setTextSize(20f);
        paint.setFakeBoldText(true);
        canvas.drawText("BrightBuds progress report", margin + 100, headerRect.centerY() + 5, paint);

        int y = headerHeight + 30;
        yHolder[0] = y;
        return page;
    }

    private void sharePdfByEmail(File file, ChildItem child) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");

            // Auto-fill parent email
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                String parentEmail = user.getEmail();
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ parentEmail });
            }

            intent.putExtra(Intent.EXTRA_SUBJECT, "BrightBuds report for " + child.name);
            intent.putExtra(Intent.EXTRA_TEXT, "Attached is the latest BrightBuds progress report.");

            intent.putExtra(
                    Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            file
                    )
            );
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, "Email BrightBuds report"));
        } catch (Exception e) {
            Log.e(TAG, "Error emailing PDF", e);
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    // endregion

    private String safeString(String value) {
        return value != null ? value : "Module";
    }

    // region inner classes

    private static class ChildItem {
        final String id;
        final String name;

        ChildItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class ProgressRow {
        String moduleId;
        int score;
        int stars;
        int plays;
        long timeMs;
    }

    private static class AnalyticsRow {
        String moduleId;
        double avgScore;
        double accuracyFraction;
        double avgTimeMs;
    }

    // endregion
}
