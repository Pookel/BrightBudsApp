package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;

/**
 * ReportsDashboardActivity
 *
 * Entry point for parent reports and analytics.
 *
 * Tiles:
 *  - btnChildProfileReport  => single child report screen
 *  - btnLevelReport         => all children report screen
 *  - btnCharts              => charts for all children
 *  - btnPdfReport           => PDF report for all children
 *
 * Bottom bar:
 *  - btnHomeReports         => back to ParentDashboardActivity
 *  - btnCloseReports        => back to ParentDashboardActivity
 *
 * All report actions first check that the device is online.
 */
public class ReportsDashboardActivity extends AppCompatActivity {

    private MediaPlayer bgMusic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports_dashboard);

        // Grid tiles
        LinearLayout btnChildProfileReport = findViewById(R.id.btnChildProfileReport);
        LinearLayout btnLevelReport        = findViewById(R.id.btnLevelReport);
        LinearLayout btnCharts             = findViewById(R.id.btnCharts);
        LinearLayout btnPdfReport          = findViewById(R.id.btnPdfReport);

        // Bottom bar icons
        ImageView btnHomeReports  = findViewById(R.id.btnHomeReports);
        ImageView btnCloseReports = findViewById(R.id.btnCloseReports);

        startBackgroundMusic();

        // Home goes back to ParentDashboardActivity
        btnHomeReports.setOnClickListener(v -> {
            Intent intent = new Intent(ReportsDashboardActivity.this, ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Close also goes back to ParentDashboardActivity
        btnCloseReports.setOnClickListener(v -> {
            Intent intent = new Intent(ReportsDashboardActivity.this, ParentDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Child report for a single child
        btnChildProfileReport.setOnClickListener(v -> {
            if (!isOnline()) {
                Toast.makeText(this, "Turn on WiFi to view reports", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(ReportsDashboardActivity.this, ParentReportActivity.class);
            startActivity(intent);
        });

        // Reports for all children
        btnLevelReport.setOnClickListener(v -> {
            if (!isOnline()) {
                Toast.makeText(this, "Turn on WiFi to view reports", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(ReportsDashboardActivity.this, AllChildrenReportActivity.class);
            startActivity(intent);
        });

        // Charts for all children (re-use AllChildrenReportActivity for now)
        btnCharts.setOnClickListener(v -> {
            if (!isOnline()) {
                Toast.makeText(this, "Turn on WiFi to view charts", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(ReportsDashboardActivity.this, ChartsAndInsightsActivity.class);
            // You could add extras here later to tell that screen to default to "charts" view
            startActivity(intent);
        });

        // PDF report for all children
        btnPdfReport.setOnClickListener(v -> {
            if (!isOnline()) {
                Toast.makeText(this, "Turn on WiFi to generate PDF", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(ReportsDashboardActivity.this, AllChildrenChartsPdfActivity.class);
            Toast.makeText(
                    this,
                    "On the next screen, tap Download PDF or Email PDF",
                    Toast.LENGTH_SHORT
            ).show();
            startActivity(intent);
        });
    }

    private void startBackgroundMusic() {
        try {
            bgMusic = MediaPlayer.create(this, R.raw.classical_music);
            if (bgMusic != null) {
                bgMusic.setLooping(true);
                bgMusic.setVolume(0.2f, 0.2f);
                bgMusic.start();
            }
        } catch (Exception ignored) { }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bgMusic != null && bgMusic.isPlaying()) {
            bgMusic.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bgMusic != null && !bgMusic.isPlaying()) {
            bgMusic.start();
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
            } catch (Exception ignored) { }
            bgMusic.release();
            bgMusic = null;
        }
    }
}
