package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.brightbuds_app.R;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.RadarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.RadarData;
import com.github.mikephil.charting.data.RadarDataSet;
import com.github.mikephil.charting.data.RadarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AllChildrenChartsPdfActivity
 *
 * Generates a PDF that contains, for each completed child profile:
 *   Child name
 *   Metrics summary
 *   Bar chart - Stars per module
 *   Bar chart - Time per module
 *   Radar chart - Avg score vs Accuracy vs Speed
 *
 * Parent can:
 *   Download the PDF
 *   Email the PDF
 */
public class AllChildrenChartsPdfActivity extends AppCompatActivity {

    private static final String TAG = "AllChildrenChartsPdf";

    private FirebaseFirestore db;
    private String parentId;

    private TextView tvPdfStatus;

    // New icon views for actions
    private ImageView iconDownloadPdf;
    private ImageView iconEmailPdf;
    private TextView tvDownloadLabel;
    private TextView tvEmailLabel;

    private ImageView btnHome;
    private ImageView btnClose;

    private final List<ChildPdfData> childDataList = new ArrayList<>();
    private File lastPdfFile;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_children_charts_pdf);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        parentId = user != null ? user.getUid() : null;

        tvPdfStatus = findViewById(R.id.tvPdfStatus);

        // Icons and labels from updated XML
        iconDownloadPdf = findViewById(R.id.iconDownloadPdf);
        iconEmailPdf = findViewById(R.id.iconEmailPdf);
        tvDownloadLabel = findViewById(R.id.tvDownloadPdf);
        tvEmailLabel = findViewById(R.id.tvEmailPdf);

        // Footer buttons (keep ids that exist in your XML)
        btnHome = findViewById(R.id.btnHomeChartsPdf);
        btnClose = findViewById(R.id.btnCloseChartsPdf);

        btnClose.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(AllChildrenChartsPdfActivity.this,
                    ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Shared click logic for Download
        View.OnClickListener downloadClickListener = v -> {
            if (childDataList.isEmpty()) {
                Toast.makeText(this,
                        "Data still loading or no completed profiles.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            lastPdfFile = generatePdfForAllChildren();
            if (lastPdfFile != null) {
                Toast.makeText(this,
                        "PDF saved: " + lastPdfFile.getName(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        // Shared click logic for Email
        View.OnClickListener emailClickListener = v -> {
            if (childDataList.isEmpty()) {
                Toast.makeText(this,
                        "Data still loading or no completed profiles.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (lastPdfFile == null || !lastPdfFile.exists()) {
                lastPdfFile = generatePdfForAllChildren();
            }
            if (lastPdfFile == null) {
                Toast.makeText(this,
                        "Unable to generate PDF.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            sharePdfByEmail(lastPdfFile);
        };

        // Attach listeners to both icons and labels
        iconDownloadPdf.setOnClickListener(downloadClickListener);
        tvDownloadLabel.setOnClickListener(downloadClickListener);

        iconEmailPdf.setOnClickListener(emailClickListener);
        tvEmailLabel.setOnClickListener(emailClickListener);

        loadAllChildProfiles();
    }

    // region Data loading

    private void loadAllChildProfiles() {
        if (parentId == null) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            return;
        }

        tvPdfStatus.setText("Loading children...");
        childDataList.clear();

        db.collection("child_profiles")
                .whereEqualTo("parentId", parentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot) {
                        String id = doc.getId();
                        String name = doc.getString("name");
                        if (name == null) {
                            name = "";
                        }
                        String trimmed = name.trim();

                        // Completed profile rule
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        if (trimmed.equals("Child 1")
                                || trimmed.equals("Child 2")
                                || trimmed.equals("Child 3")
                                || trimmed.equals("Child 4")
                                || trimmed.equals("Child 5")) {
                            continue;
                        }

                        ChildPdfData child = new ChildPdfData();
                        child.childId = id;
                        child.childName = trimmed;
                        childDataList.add(child);
                    }

                    if (childDataList.isEmpty()) {
                        tvPdfStatus.setText("No completed child profiles to include.");
                        return;
                    }

                    tvPdfStatus.setText("Loading analytics and progress...");
                    loadMetricsForChildIndex(0);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_profiles", e);
                    tvPdfStatus.setText("Failed to load children.");
                    Toast.makeText(this, "Failed to load children", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadMetricsForChildIndex(int index) {
        if (index >= childDataList.size()) {
            tvPdfStatus.setText("Data ready. Tap Download or Email PDF.");
            return;
        }

        ChildPdfData child = childDataList.get(index);

        // Load analytics first
        db.collection("child_analytics")
                .whereEqualTo("child_id", child.childId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    double totalScore = 0.0;
                    double totalAccuracy = 0.0;
                    double totalTimeMs = 0.0;
                    int count = 0;

                    for (DocumentSnapshot doc : snapshot) {
                        Double avgScore = doc.getDouble("avgScore");
                        Double accuracy = doc.getDouble("accuracy");
                        Double avgTimeMs = doc.getDouble("avgTimeMs");

                        if (avgScore != null) totalScore += avgScore;
                        if (accuracy != null) totalAccuracy += accuracy;
                        if (avgTimeMs != null) totalTimeMs += avgTimeMs;
                        count++;
                    }

                    if (count > 0) {
                        child.overallAvgScore = totalScore / count;
                        child.overallAccuracyFraction = totalAccuracy / count;
                        child.overallAvgTimeSeconds = (totalTimeMs / count) / 1000.0;
                    }

                    // Now load progress
                    loadProgressForChildIndex(index, child);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_analytics for " + child.childId, e);
                    loadProgressForChildIndex(index, child);
                });
    }

    private void loadProgressForChildIndex(int index, ChildPdfData child) {
        db.collection("child_progress")
                .whereEqualTo("child_id", child.childId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, ModuleMetrics> moduleMap = new HashMap<>();

                    for (DocumentSnapshot doc : snapshot) {
                        String moduleId = doc.getString("module_id");
                        if (moduleId == null) {
                            continue;
                        }

                        Long stars = doc.getLong("stars");
                        Long plays = doc.getLong("totalPlays");
                        Long timeMs = doc.getLong("totalTimeMs");
                        Long lastScore = doc.getLong("lastScore");

                        ModuleMetrics mm = moduleMap.get(moduleId);
                        if (mm == null) {
                            mm = new ModuleMetrics();
                            mm.moduleId = moduleId;
                            moduleMap.put(moduleId, mm);
                        }
                        if (stars != null) mm.totalStars += stars.intValue();
                        if (timeMs != null) mm.totalTimeMs += timeMs;
                        if (plays != null) mm.totalPlays += plays.intValue();
                        if (lastScore != null) {
                            mm.totalScore += lastScore.intValue();
                            mm.scoreCount += 1;
                        }
                    }

                    child.moduleMetricsMap = moduleMap;

                    // Next child
                    loadMetricsForChildIndex(index + 1);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_progress for " + child.childId, e);
                    loadMetricsForChildIndex(index + 1);
                });
    }

    // endregion

    // region PDF generation

    private File generatePdfForAllChildren() {
        if (childDataList.isEmpty()) {
            Toast.makeText(this, "No completed profiles to include.", Toast.LENGTH_SHORT).show();
            return null;
        }

        PdfDocument pdf = new PdfDocument();

        int pageWidth = 595;  // approx A4 at 72 dpi
        int pageHeight = 842;
        int margin = 40;

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(12f);

        Paint headingPaint = new Paint();
        headingPaint.setColor(Color.BLACK);
        headingPaint.setTextSize(16f);
        headingPaint.setFakeBoldText(true);

        Paint titlePaint = new Paint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(20f);
        titlePaint.setFakeBoldText(true);

        Paint linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(1f);

        // Start first page
        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        PdfDocument.Page page = pdf.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int y = margin;

        // Overall title
        canvas.drawText("BrightBuds - All Children Charts Report", margin, y, titlePaint);
        y += 30;

        for (int i = 0; i < childDataList.size(); i++) {
            ChildPdfData child = childDataList.get(i);

            // New section - check page space
            if (y > pageHeight - 350) {
                pdf.finishPage(page);
                pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight,
                        pdf.getPages().size() + 1).create();
                page = pdf.startPage(pageInfo);
                canvas = page.getCanvas();
                y = margin;
            }

            // Child name heading
            canvas.drawText("Child: " + child.childName, margin, y, headingPaint);
            y += 20;

            // Metrics summary
            String metricsLine1 = String.format(
                    Locale.US,
                    "Average score: %.1f%%   Accuracy: %.1f%%   Speed score: %.1f",
                    child.overallAvgScore,
                    child.overallAccuracyFraction * 100.0,
                    computeSpeedScore(child.overallAvgTimeSeconds)
            );
            canvas.drawText(metricsLine1, margin, y, textPaint);
            y += 18;

            int totalStarsAll = 0;
            int totalPlaysAll = 0;
            long totalTimeMsAll = 0;
            if (child.moduleMetricsMap != null) {
                for (ModuleMetrics mm : child.moduleMetricsMap.values()) {
                    totalStarsAll += mm.totalStars;
                    totalPlaysAll += mm.totalPlays;
                    totalTimeMsAll += mm.totalTimeMs;
                }
            }
            double totalMinutes = totalTimeMsAll / 60000.0;
            String metricsLine2 = String.format(
                    Locale.US,
                    "Total stars: %d   Total plays: %d   Time spent: %.1f min",
                    totalStarsAll,
                    totalPlaysAll,
                    totalMinutes
            );
            canvas.drawText(metricsLine2, margin, y, textPaint);
            y += 24;

            // Chart area width and height
            int chartWidth = pageWidth - 2 * margin;
            int chartHeight = 140;

            // Chart 1 - Stars per module
            Bitmap starsBitmap = buildStarsPerModuleBitmap(child, chartWidth, chartHeight);
            if (starsBitmap != null) {
                if (y + chartHeight > pageHeight - margin) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight,
                            pdf.getPages().size() + 1).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin;
                }
                canvas.drawBitmap(starsBitmap, margin, y, null);
                y += chartHeight + 14;
                canvas.drawText(
                        "This chart shows where your child is earning the most stars. Higher bars mean more consistent success in those modules.",
                        margin,
                        y,
                        textPaint
                );
                y += 32;
            }

            // Chart 2 - Time per module
            Bitmap timeBitmap = buildTimePerModuleBitmap(child, chartWidth, chartHeight);
            if (timeBitmap != null) {
                if (y + chartHeight > pageHeight - margin) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight,
                            pdf.getPages().size() + 1).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin;
                }
                canvas.drawBitmap(timeBitmap, margin, y, null);
                y += chartHeight + 14;
                canvas.drawText(
                        "This chart shows how your child’s time is spread across activities. Taller bars show modules where your child spends more time learning.",
                        margin,
                        y,
                        textPaint
                );
                y += 32;
            }

            // Chart 3 - Radar (score, accuracy, speed)
            Bitmap radarBitmap = buildRadarBitmap(child, chartWidth, chartHeight);
            if (radarBitmap != null) {
                if (y + chartHeight > pageHeight - margin) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight,
                            pdf.getPages().size() + 1).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = margin;
                }
                canvas.drawBitmap(radarBitmap, margin, y, null);
                y += chartHeight + 14;
                canvas.drawText(
                        "This chart combines accuracy, score and speed. A larger shape towards the outer edges indicates stronger performance across all three aspects.",
                        margin,
                        y,
                        textPaint
                );
                y += 34;
            }

            // Divider line between children
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);
            y += 20;
        }

        pdf.finishPage(page);

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = getFilesDir();
        }

        File file = new File(dir, "BrightBuds_AllChildrenChartsReport.pdf");
        try (FileOutputStream out = new FileOutputStream(file)) {
            pdf.writeTo(out);
            pdf.close();
            tvPdfStatus.setText("PDF generated.");
            return file;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save PDF", e);
            pdf.close();
            Toast.makeText(this, "Failed to save PDF", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // endregion

    // region Chart bitmaps

    private Bitmap buildStarsPerModuleBitmap(ChildPdfData child, int width, int height) {
        if (child.moduleMetricsMap == null || child.moduleMetricsMap.isEmpty()) {
            return null;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;
        for (ModuleMetrics mm : child.moduleMetricsMap.values()) {
            entries.add(new BarEntry(index, mm.totalStars));
            labels.add(moduleIdToLabel(mm.moduleId));
            index++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Stars per module");
        dataSet.setColor(Color.parseColor("#FFC107"));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(8f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        BarChart chart = new BarChart(this);
        configureBarChart(chart, labels);
        chart.setData(data);
        chart.animateY(1000, Easing.EaseInOutQuad);

        return renderChartToBitmap(chart, width, height);
    }

    private Bitmap buildTimePerModuleBitmap(ChildPdfData child, int width, int height) {
        if (child.moduleMetricsMap == null || child.moduleMetricsMap.isEmpty()) {
            return null;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;
        for (ModuleMetrics mm : child.moduleMetricsMap.values()) {
            double minutes = mm.totalTimeMs / 60000.0;
            entries.add(new BarEntry(index, (float) minutes));
            labels.add(moduleIdToLabel(mm.moduleId));
            index++;
        }

        BarDataSet dataSet = new BarDataSet(entries, "Time per module (min)");
        dataSet.setColor(Color.parseColor("#03A9F4"));
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(8f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        BarChart chart = new BarChart(this);
        configureBarChart(chart, labels);
        chart.setData(data);
        chart.animateX(1000, Easing.EaseInOutQuad);

        return renderChartToBitmap(chart, width, height);
    }

    private Bitmap buildRadarBitmap(ChildPdfData child, int width, int height) {
        List<RadarEntry> entries = new ArrayList<>();

        float score = (float) child.overallAvgScore;
        float accuracyPercent = (float) (child.overallAccuracyFraction * 100.0);
        float speedScore = computeSpeedScore(child.overallAvgTimeSeconds);

        entries.add(new RadarEntry(score));
        entries.add(new RadarEntry(accuracyPercent));
        entries.add(new RadarEntry(speedScore));

        RadarDataSet set = new RadarDataSet(entries, child.childName);
        set.setLineWidth(2f);
        set.setDrawFilled(true);
        set.setFillAlpha(120);
        set.setValueTextSize(8f);
        set.setValueTextColor(Color.WHITE);

        int color = pickColorForName(child.childName);
        set.setColor(color);
        set.setFillColor(color);

        RadarData radarData = new RadarData(set);

        RadarChart radar = new RadarChart(this);
        configureRadarChart(radar);
        radar.setData(radarData);
        String[] labels = new String[]{"Avg score", "Accuracy", "Speed"};
        radar.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        radar.animateXY(1200, 1200, Easing.EaseInOutQuad, Easing.EaseInOutQuad);

        return renderChartToBitmap(radar, width, height);
    }

    private Bitmap renderChartToBitmap(com.github.mikephil.charting.charts.Chart<?> chart,
                                       int width,
                                       int height) {
        chart.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        int widthSpec = ViewGroup.MeasureSpec.makeMeasureSpec(width, ViewGroup.MeasureSpec.EXACTLY);
        int heightSpec = ViewGroup.MeasureSpec.makeMeasureSpec(height, ViewGroup.MeasureSpec.EXACTLY);
        chart.measure(widthSpec, heightSpec);
        chart.layout(0, 0, width, height);
        return chart.getChartBitmap();
    }

    private void configureBarChart(BarChart chart, List<String> xLabels) {
        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(false);
        chart.setNoDataText("No data");
        chart.getAxisRight().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xLabels));

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.WHITE);
        left.setAxisMinimum(0f);

        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
    }

    private void configureRadarChart(RadarChart radar) {
        radar.getDescription().setEnabled(false);
        radar.setDrawWeb(true);
        radar.setWebLineWidth(0.5f);
        radar.setWebColor(Color.LTGRAY);
        radar.setWebLineWidthInner(0.4f);
        radar.setWebColorInner(Color.LTGRAY);
        radar.setWebAlpha(120);
        radar.setTouchEnabled(false);

        radar.getYAxis().setAxisMinimum(0f);
        radar.getYAxis().setAxisMaximum(100f);
        radar.getYAxis().setTextColor(Color.WHITE);
        radar.getXAxis().setTextColor(Color.WHITE);

        Legend legend = radar.getLegend();
        legend.setTextColor(Color.WHITE);
    }

    private float computeSpeedScore(double avgTimeSeconds) {
        if (avgTimeSeconds <= 0) return 0f;
        double t = avgTimeSeconds;
        double raw = 120.0 - t;
        raw = Math.max(0.0, Math.min(100.0, raw));
        return (float) raw;
    }

    private int pickColorForName(String name) {
        int hash = name.hashCode();
        int r = 80 + Math.abs(hash % 120);
        int g = 80 + Math.abs((hash / 31) % 120);
        int b = 80 + Math.abs((hash / 61) % 120);
        return Color.rgb(r, g, b);
    }

    // Convert module_id to friendly label
    private String moduleIdToLabel(String moduleId) {
        if (moduleId == null) {
            return "Module";
        }
        String id = moduleId.trim();

        if (id.equalsIgnoreCase("MODULE_FEED_THE_MONSTER")
                || id.equalsIgnoreCase("feed_the_monster")
                || id.equalsIgnoreCase("feed_monster")) {
            return "Feed the Monster";
        }
        if (id.equalsIgnoreCase("MODULE_MATCH_LETTER")
                || id.equalsIgnoreCase("match_letter")) {
            return "Match the Letter";
        }
        if (id.equalsIgnoreCase("MODULE_MEMORY_MATCH")
                || id.equalsIgnoreCase("memory_match")) {
            return "Memory Match";
        }
        if (id.equalsIgnoreCase("MODULE_WORD_BUILDER")
                || id.equalsIgnoreCase("word_builder")) {
            return "Word Builder";
        }
        if (id.equalsIgnoreCase("MODULE_FAMILY_GALLERY")
                || id.equalsIgnoreCase("family_gallery")) {
            return "Family Gallery";
        }
        if (id.equalsIgnoreCase("MODULE_ABC_SONG")
                || id.equalsIgnoreCase("abc_song")) {
            return "ABC Song";
        }
        if (id.equalsIgnoreCase("MODULE_123_SONG")
                || id.equalsIgnoreCase("numbers_song")
                || id.equalsIgnoreCase("one_two_three_song")) {
            return "123 Song";
        }
        if (id.equalsIgnoreCase("MODULE_SHAPES_SONG")
                || id.equalsIgnoreCase("shapes_song")) {
            return "Shapes Song";
        }

        String cleaned = id.replace("MODULE_", "")
                .replace("_", " ")
                .toLowerCase(Locale.US);

        String[] parts = cleaned.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.substring(1))
                    .append(" ");
        }
        String label = sb.toString().trim();
        return label.isEmpty() ? "Module" : label;
    }

    // endregion

    // region Email sharing

    private void sharePdfByEmail(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");

            // Auto fill parent email
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getEmail() != null) {
                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{user.getEmail()});
            }

            intent.putExtra(Intent.EXTRA_SUBJECT, "BrightBuds all children charts report");
            intent.putExtra(Intent.EXTRA_TEXT,
                    "Attached is the latest BrightBuds charts report for all completed child profiles.");

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

    // region Helper classes

    private static class ChildPdfData {
        String childId;
        String childName;

        double overallAvgScore;
        double overallAccuracyFraction;
        double overallAvgTimeSeconds;

        Map<String, ModuleMetrics> moduleMetricsMap = new HashMap<>();
    }

    private static class ModuleMetrics {
        String moduleId;
        int totalStars;
        int totalPlays;
        long totalTimeMs;
        int totalScore;
        int scoreCount;
    }

    // endregion
}
