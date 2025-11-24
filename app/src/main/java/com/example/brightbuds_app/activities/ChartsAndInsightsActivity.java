package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * ChartsAndInsightsActivity
 *
 * Shows:
 *  - Bar chart: total stars per completed child
 *  - Bar chart: overall progress per child (avg score)
 *  - Bar chart: most played module per child
 *  - Radar chart: avg score vs accuracy vs avg time for each child
 *
 * Only completed profiles are included:
 *   name not blank and not exactly "Child 1" .. "Child 5".
 */
public class ChartsAndInsightsActivity extends AppCompatActivity {

    private static final String TAG = "ChartsAndInsights";

    private FirebaseFirestore db;
    private String parentId;

    // Charts
    private BarChart chartStarsPerChild;
    private BarChart chartProgressPerChild;
    private BarChart chartMostPlayedPerChild;
    private RadarChart chartRadarPerChild;

    private TextView tvPlaceholder;

    private final List<ChildChartData> childDataList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charts_insights);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        parentId = user != null ? user.getUid() : null;

        chartStarsPerChild = findViewById(R.id.chartStarsPerChild);
        chartProgressPerChild = findViewById(R.id.chartProgressPerChild);
        chartMostPlayedPerChild = findViewById(R.id.chartMostPlayedPerChild);
        chartRadarPerChild = findViewById(R.id.chartRadarPerChild);
        tvPlaceholder = findViewById(R.id.tvChartsPlaceholder);

        ImageView btnClose = findViewById(R.id.btnCloseCharts);
        ImageView btnHome = findViewById(R.id.btnHomeCharts);

        // Footer navigation
        btnClose.setOnClickListener(v -> finish());

        btnHome.setOnClickListener(v -> {
            Intent intent = new Intent(ChartsAndInsightsActivity.this,
                    ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        configureChartBasics(chartStarsPerChild);
        configureChartBasics(chartProgressPerChild);
        configureChartBasics(chartMostPlayedPerChild);
        configureRadarBasics(chartRadarPerChild);

        loadAllChildData();
    }

    private void configureChartBasics(BarChart chart) {
        chart.setNoDataText("No chart data available.");
        chart.setDrawGridBackground(false);
        chart.getDescription().setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.WHITE);

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.WHITE);
        left.setAxisMinimum(0f);

        chart.getAxisRight().setEnabled(false);

        Legend legend = chart.getLegend();
        legend.setTextColor(Color.WHITE);
    }

    private void configureRadarBasics(RadarChart radar) {
        radar.setNoDataText("No chart data available.");
        radar.getDescription().setEnabled(false);
        radar.setDrawWeb(true);
        radar.setWebLineWidth(0.5f);
        radar.setWebColor(Color.LTGRAY);
        radar.setWebLineWidthInner(0.4f);
        radar.setWebColorInner(Color.LTGRAY);
        radar.setWebAlpha(120);

        Legend legend = radar.getLegend();
        legend.setTextColor(Color.WHITE);
        legend.setWordWrapEnabled(true);

        radar.getXAxis().setTextColor(Color.WHITE);
        radar.getYAxis().setTextColor(Color.WHITE);
        radar.getYAxis().setAxisMinimum(0f);
        radar.getYAxis().setAxisMaximum(100f); // all metrics scaled roughly 0-100
    }

    private void loadAllChildData() {
        if (parentId == null) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_SHORT).show();
            return;
        }

        tvPlaceholder.setText("Loading charts...");
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

                        // Skip blank and default names
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

                        ChildChartData data = new ChildChartData();
                        data.childId = id;
                        data.childName = trimmed;
                        childDataList.add(data);
                    }

                    if (childDataList.isEmpty()) {
                        tvPlaceholder.setText("No completed child profiles to show charts.");
                        chartStarsPerChild.clear();
                        chartProgressPerChild.clear();
                        chartMostPlayedPerChild.clear();
                        chartRadarPerChild.clear();
                        return;
                    }

                    // Load analytics and progress for each child one by one
                    loadMetricsForChildIndex(0);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_profiles", e);
                    tvPlaceholder.setText("Failed to load charts.");
                    Toast.makeText(this, "Failed to load child profiles", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadMetricsForChildIndex(int index) {
        if (index >= childDataList.size()) {
            // All children loaded
            tvPlaceholder.setText("");
            buildAllCharts();
            return;
        }

        ChildChartData child = childDataList.get(index);

        // Load analytics for this child
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
                        child.overallAvgScore = totalScore / count;           // already 0-100
                        child.overallAccuracyFraction = totalAccuracy / count; // 0-1 fraction
                        child.overallAvgTimeSeconds = (totalTimeMs / count) / 1000.0;
                    }

                    // After analytics, load progress
                    loadProgressForChildIndex(index, child);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_analytics for " + child.childId, e);
                    // Continue with next child even if this fails
                    loadProgressForChildIndex(index, child);
                });
    }

    private void loadProgressForChildIndex(int index, ChildChartData child) {
        db.collection("child_progress")
                .whereEqualTo("child_id", child.childId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int totalStars = 0;
                    String mostModuleId = null;
                    int mostPlays = 0;

                    for (DocumentSnapshot doc : snapshot) {
                        Long stars = doc.getLong("stars");
                        Long plays = doc.getLong("totalPlays");
                        String moduleId = doc.getString("module_id");

                        if (stars != null) {
                            totalStars += stars.intValue();
                        }

                        int p = plays != null ? plays.intValue() : 0;
                        if (p > mostPlays && moduleId != null) {
                            mostPlays = p;
                            mostModuleId = moduleId;
                        }
                    }

                    child.totalStars = totalStars;
                    child.mostPlayedModuleId = mostModuleId;
                    child.mostPlayedPlays = mostPlays;

                    // Next child
                    loadMetricsForChildIndex(index + 1);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load child_progress for " + child.childId, e);
                    // Still continue to next child
                    loadMetricsForChildIndex(index + 1);
                });
    }

    private void buildAllCharts() {
        buildStarsChart();
        buildProgressChart();
        buildMostPlayedChart();
        buildRadarChart();
    }

    // Stars per child
    private void buildStarsChart() {
        List<BarEntry> entries = new ArrayList<>();
        List<String> childNames = new ArrayList<>();

        for (int i = 0; i < childDataList.size(); i++) {
            ChildChartData c = childDataList.get(i);
            entries.add(new BarEntry(i, c.totalStars));
            childNames.add(c.childName);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Total stars");
        dataSet.setColor(Color.parseColor("#FFC107")); // Amber
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        chartStarsPerChild.setData(data);
        chartStarsPerChild.getXAxis().setValueFormatter(new IndexAxisValueFormatter(childNames));

        // Smooth vertical animation when data loads
        chartStarsPerChild.animateY(
                1200,
                Easing.EaseInOutQuad
        );
        chartStarsPerChild.invalidate();
    }

    // Overall progress (avg score) per child
    private void buildProgressChart() {
        List<BarEntry> entries = new ArrayList<>();
        List<String> childNames = new ArrayList<>();

        for (int i = 0; i < childDataList.size(); i++) {
            ChildChartData c = childDataList.get(i);
            float progress = (float) c.overallAvgScore; // already in percent units
            entries.add(new BarEntry(i, progress));
            childNames.add(c.childName);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Average score");
        dataSet.setColor(Color.parseColor("#03A9F4")); // Light blue
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        chartProgressPerChild.setData(data);
        chartProgressPerChild.getXAxis().setValueFormatter(new IndexAxisValueFormatter(childNames));

        // Horizontal animation shows bars sliding in from left
        chartProgressPerChild.animateX(
                1200,
                Easing.EaseInOutQuad
        );
        chartProgressPerChild.invalidate();
    }

    // Most-played module per child
    private void buildMostPlayedChart() {
        List<BarEntry> entries = new ArrayList<>();
        List<String> childNames = new ArrayList<>();

        for (int i = 0; i < childDataList.size(); i++) {
            ChildChartData c = childDataList.get(i);
            int plays = c.mostPlayedPlays;
            entries.add(new BarEntry(i, plays));
            childNames.add(c.childName);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Most played module (plays)");
        dataSet.setColor(Color.parseColor("#8BC34A")); // Green
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(9f);

        // Show friendly module name and play count on each bar
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                int index = (int) barEntry.getX();
                if (index < 0 || index >= childDataList.size()) {
                    return String.valueOf((int) barEntry.getY());
                }
                ChildChartData c = childDataList.get(index);
                String moduleLabel = moduleIdToLabel(c.mostPlayedModuleId);
                return moduleLabel + " (" + (int) barEntry.getY() + ")";
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        chartMostPlayedPerChild.setData(data);
        chartMostPlayedPerChild.getXAxis().setValueFormatter(new IndexAxisValueFormatter(childNames));

        // Combined X and Y animation gives a smooth scale-in effect
        chartMostPlayedPerChild.animateXY(
                1200,
                1200,
                Easing.EaseInOutQuad,
                Easing.EaseInOutQuad
        );
        chartMostPlayedPerChild.invalidate();
    }

    // Radar chart for analytics (avg score, accuracy, avg time)
    private void buildRadarChart() {
        List<RadarDataSet> dataSets = new ArrayList<>();

        // Axis labels for radar: 3 metrics
        String[] metricsLabels = new String[]{
                "Avg score",
                "Accuracy",
                "Avg time"
        };
        chartRadarPerChild.getXAxis()
                .setValueFormatter(new IndexAxisValueFormatter(metricsLabels));

        for (ChildChartData c : childDataList) {
            List<RadarEntry> entries = new ArrayList<>();

            float score = (float) c.overallAvgScore;                 // 0-100
            float accuracyPercent = (float) (c.overallAccuracyFraction * 100.0); // 0-100
            // Convert time to a "speed" score: faster -> higher number, clamp 0-100
            float timeScore;
            if (c.overallAvgTimeSeconds <= 0) {
                timeScore = 0f;
            } else {
                // Very rough mapping: 0 sec -> 100, 60 sec -> 40, 120 sec -> 10
                double t = c.overallAvgTimeSeconds;
                double raw = 120.0 - t; // smaller t gives bigger score
                raw = Math.max(0.0, Math.min(100.0, raw));
                timeScore = (float) raw;
            }

            entries.add(new RadarEntry(score));
            entries.add(new RadarEntry(accuracyPercent));
            entries.add(new RadarEntry(timeScore));

            RadarDataSet set = new RadarDataSet(entries, c.childName);
            set.setLineWidth(2f);
            set.setDrawFilled(true);
            set.setFillAlpha(120);
            set.setValueTextSize(8f);
            set.setValueTextColor(Color.WHITE);

            // Simple color cycling using hash of name
            int color = pickColorForName(c.childName);
            set.setColor(color);
            set.setFillColor(color);

            dataSets.add(set);
        }

        if (dataSets.isEmpty()) {
            chartRadarPerChild.clear();
            return;
        }

        RadarData radarData = new RadarData();
        for (RadarDataSet set : dataSets) {
            radarData.addDataSet(set);
        }

        chartRadarPerChild.setData(radarData);

        // Radar animation rotates and grows out from the center
        chartRadarPerChild.animateXY(
                1400,
                1400,
                Easing.EaseInOutQuad,
                Easing.EaseInOutQuad
        );
        chartRadarPerChild.invalidate();
    }

    private int pickColorForName(String name) {
        // Very simple hash to derive a stable color per child
        int hash = name.hashCode();
        int r = 80 + Math.abs(hash % 120);
        int g = 80 + Math.abs((hash / 31) % 120);
        int b = 80 + Math.abs((hash / 61) % 120);
        return Color.rgb(r, g, b);
    }

    // Convert module_id to a friendly label
    private String moduleIdToLabel(String moduleId) {
        if (moduleId == null) {
            return "Module";
        }
        String id = moduleId.trim();

        // Handle common constants and ids
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

        // Fallback: prettify generic ids, for example "feed_the_monster" -> "Feed The Monster"
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

    // Data holder for charts
    private static class ChildChartData {
        String childId;
        String childName;

        int totalStars;

        double overallAvgScore;
        double overallAccuracyFraction;
        double overallAvgTimeSeconds;

        String mostPlayedModuleId;
        int mostPlayedPlays;
    }
}
