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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AllChildrenReportActivity
 *
 * Shows a simple summary table for all children linked to the logged in parent.
 * Allows:
 *   - viewing table on screen
 *   - generating a PDF for all children
 *   - emailing that PDF
 */
public class AllChildrenReportActivity extends AppCompatActivity {

    private static final String TAG = "AllChildrenReportAct";

    private FirebaseFirestore db;
    private String parentId;

    private LinearLayout layoutTablesContainer;
    private TextView tvPlaceholder;
    private ImageButton btnHome;
    private ImageButton btnClose;
    private ImageButton btnDownloadAllPdf;
    private Button btnEmailAllPdf;

    private final List<ChildSummary> childSummaries = new ArrayList<>();

    private File lastAllChildrenPdf;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_children_report);

        db = FirebaseFirestore.getInstance();
        parentId = FirebaseAuth.getInstance().getUid();

        layoutTablesContainer = findViewById(R.id.layoutTablesContainerAll);
        tvPlaceholder = findViewById(R.id.tvTablesPlaceholderAll);
        btnHome = findViewById(R.id.btnHome);
        btnClose = findViewById(R.id.btnClose);
        btnDownloadAllPdf = findViewById(R.id.btnDownloadAllPdf);
        btnEmailAllPdf = findViewById(R.id.btnEmailAllPdf);

        // Add email icon to email button
        btnEmailAllPdf.setCompoundDrawablesWithIntrinsicBounds(R.drawable.email_icon, 0, 0, 0);
        btnEmailAllPdf.setCompoundDrawablePadding(8);

        btnClose.setOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed()
        );

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(AllChildrenReportActivity.this, ParentDashboardActivity.class);
            startActivity(intent);
            finish();
        });

        btnDownloadAllPdf.setOnClickListener(v -> onDownloadAllPdfClicked());
        btnEmailAllPdf.setOnClickListener(v -> onEmailAllPdfClicked());

        loadAllChildrenSummary();
    }

    private void loadAllChildrenSummary() {
        if (parentId == null) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            return;
        }

        tvPlaceholder.setText("Loading children...");
        childSummaries.clear();

        db.collection("child_profiles")
                .whereEqualTo("parentId", parentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Default placeholder names to exclude from reports
                    List<String> defaultNames = Arrays.asList(
                            "Child 1", "Child 2", "Child 3", "Child 4", "Child 5"
                    );

                    for (DocumentSnapshot doc : snapshot) {
                        try {
                            String id = doc.getId();
                            String name = doc.getString("name");
                            if (name == null) {
                                name = "";
                            }
                            String trimmedName = name.trim();

                            // Skip blank or default placeholder profiles
                            if (trimmedName.isEmpty() || defaultNames.contains(trimmedName)) {
                                continue;
                            }

                            Long progress = doc.getLong("progress");
                            Long stars = doc.getLong("stars");

                            ChildSummary cs = new ChildSummary();
                            cs.childId = id;
                            cs.childName = trimmedName;
                            cs.progress = progress != null ? progress.intValue() : 0;
                            cs.stars = stars != null ? stars.intValue() : 0;

                            childSummaries.add(cs);
                        } catch (Exception e) {
                            Log.e(TAG, "Error mapping child profile", e);
                        }
                    }

                    buildTable();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_profiles", e);
                    tvPlaceholder.setText("Failed to load children.");
                    Toast.makeText(this, "Failed to load children", Toast.LENGTH_SHORT).show();
                });
    }

    private void buildTable() {
        layoutTablesContainer.removeAllViews();

        if (childSummaries.isEmpty()) {
            tvPlaceholder.setText("No completed child profiles found for this parent.");
            layoutTablesContainer.addView(tvPlaceholder);
            return;
        }

        tvPlaceholder.setText("");

        // Title
        TextView heading = new TextView(this);
        heading.setText("All children summary");
        heading.setTextSize(18f);
        heading.setTextColor(Color.BLACK);
        heading.setPadding(0, 8, 0, 8);
        heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
        layoutTablesContainer.addView(heading);

        // Header row
        LinearLayout header = createRow();
        header.addView(createCell("Child", 1.5f, true));
        header.addView(createCell("Progress", 1f, true));
        header.addView(createCell("Stars", 1f, true));
        layoutTablesContainer.addView(header);

        // Data rows
        for (ChildSummary cs : childSummaries) {
            LinearLayout row = createRow();
            row.addView(createCell(cs.childName, 1.5f, false));
            row.addView(createCell(cs.progress + "%", 1f, false));
            row.addView(createCell(String.valueOf(cs.stars), 1f, false));
            layoutTablesContainer.addView(row);
        }
    }

    private LinearLayout createRow() {
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

    // PDF for all children

    private void onDownloadAllPdfClicked() {
        if (childSummaries.isEmpty()) {
            Toast.makeText(this, "No children to include in the report", Toast.LENGTH_SHORT).show();
            return;
        }

        lastAllChildrenPdf = generateAllChildrenPdf();
        if (lastAllChildrenPdf != null) {
            Toast.makeText(this,
                    "PDF saved: " + lastAllChildrenPdf.getName(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void onEmailAllPdfClicked() {
        if (childSummaries.isEmpty()) {
            Toast.makeText(this, "No children to include in the report", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lastAllChildrenPdf == null || !lastAllChildrenPdf.exists()) {
            lastAllChildrenPdf = generateAllChildrenPdf();
        }
        if (lastAllChildrenPdf == null) {
            Toast.makeText(this, "Unable to generate PDF", Toast.LENGTH_SHORT).show();
            return;
        }

        shareAllChildrenPdfByEmail(lastAllChildrenPdf);
    }

    private File generateAllChildrenPdf() {
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

        String subtitle = "Summary for all completed child profiles";
        canvas.drawText(subtitle, margin + 10, y, paint);
        y += 30;

        paint.setFakeBoldText(true);
        paint.setTextSize(16f);
        canvas.drawText("All children summary", margin + 10, y, paint);
        y += 20;

        paint.setTextSize(12f);
        canvas.drawLine(margin + 10, y, pageWidth - margin - 10, y, linePaint);
        y += 18;

        canvas.drawText("Child", margin + 10, y, paint);
        canvas.drawText("Progress", margin + 210, y, paint);
        canvas.drawText("Stars", margin + 350, y, paint);
        y += 16;
        canvas.drawLine(margin + 10, y, pageWidth - margin - 10, y, linePaint);
        y += 16;

        paint.setFakeBoldText(false);

        for (ChildSummary cs : childSummaries) {
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

            canvas.drawText(cs.childName, margin + 10, y, paint);
            canvas.drawText(cs.progress + "%", margin + 210, y, paint);
            canvas.drawText(String.valueOf(cs.stars), margin + 350, y, paint);
            y += 18;
        }

        pdf.finishPage(page);

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = getFilesDir();
        }

        File file = new File(dir, "BrightBuds_All_Children_Report.pdf");

        try (FileOutputStream out = new FileOutputStream(file)) {
            pdf.writeTo(out);
            pdf.close();
            return file;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save all children PDF", e);
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
        canvas.drawText("BrightBuds all children report",
                margin + 100, headerRect.centerY() + 5, paint);

        int y = headerHeight + 40;
        yHolder[0] = y;
        return page;
    }

    private void shareAllChildrenPdfByEmail(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");

            // Auto fill parent email
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                String parentEmail = user.getEmail();
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ parentEmail });
            }

            intent.putExtra(Intent.EXTRA_SUBJECT, "BrightBuds all children report");
            intent.putExtra(Intent.EXTRA_TEXT, "Attached is the latest BrightBuds report for all children.");

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
            Log.e(TAG, "Error emailing all children PDF", e);
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }

    private static class ChildSummary {
        String childId;
        String childName;
        int progress;
        int stars;
    }
}
