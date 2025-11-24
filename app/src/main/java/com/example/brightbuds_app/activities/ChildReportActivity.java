package com.example.brightbuds_app.activities;

import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.adapters.ReportsAdapter;
import com.example.brightbuds_app.models.ChildProfile;
import com.example.brightbuds_app.models.ModuleSummary;
import com.example.brightbuds_app.services.DatabaseHelper;
import com.example.brightbuds_app.utils.ModuleIds;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_BEST_SCORE;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_LAST_ACCURACY;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_LAST_PLAYED_MS;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_LAST_SCORE;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_LAST_SESSION_TIME_MS;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_MODULE_ID;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_SESSION_COUNT;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_TOTAL_ATTEMPTS;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_TOTAL_CORRECT;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_TOTAL_SCORE;
import static com.example.brightbuds_app.services.DatabaseHelper.COLUMN_ANALYTICS_TOTAL_TIME_MS;

/**
 * Shows analytics for a single child using local SQLite child_analytics table.
 * Used by the parent reports section.
 */
public class ChildReportActivity extends AppCompatActivity {

    private static final String TAG = "ChildReportActivity";

    private TextView txtChildName;
    private RecyclerView recyclerReports;

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_report);

        txtChildName = findViewById(R.id.txtChildName);
        recyclerReports = findViewById(R.id.recyclerReports);
        recyclerReports.setLayoutManager(new LinearLayoutManager(this));

        dbHelper = new DatabaseHelper(this);

        // Try to read child information from the intent extras
        String childId = getIntent().getStringExtra("child_id");
        String childName = getIntent().getStringExtra("child_name");

        // If no child id was provided then choose the first active child for this parent
        if (childId == null || childId.trim().isEmpty()) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Not signed in", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            // Load local child profiles for this parent uid
            List<ChildProfile> children = dbHelper.getChildProfilesForParent(user.getUid());
            if (children.isEmpty()) {
                Toast.makeText(this, "No child profiles found for this parent.", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            ChildProfile first = children.get(0);
            childId = first.getChildId();
            childName = first.getName();
        }

        // Set title text to include child name for context
        if (childName != null) {
            txtChildName.setText("Reports for " + childName);
        }

        // Load analytics rows from SQLite and display them
        loadAnalyticsForChild(childId);
    }

    /**
     * Reads the child_analytics summary for this child from SQLite,
     * converts cursor rows into ModuleSummary objects and displays them
     * in the RecyclerView.
     */
    private void loadAnalyticsForChild(String childId) {
        Cursor c = dbHelper.getChildAnalyticsSummary(childId);
        List<ModuleSummary> list = new ArrayList<>();

        try {
            while (c != null && c.moveToNext()) {
                // Read module id and map to a friendly name
                String moduleId = c.getString(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_MODULE_ID)
                );
                String moduleName = ModuleIds.getModuleDisplayName(moduleId);

                ModuleSummary s = new ModuleSummary(moduleId, moduleName);

                // Copy raw fields from the analytics row
                s.setSessionCount(c.getInt(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_SESSION_COUNT)
                ));
                s.setTotalScore(c.getInt(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_TOTAL_SCORE)
                ));
                s.setTotalCorrect(c.getInt(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_TOTAL_CORRECT)
                ));
                s.setTotalAttempts(c.getInt(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_TOTAL_ATTEMPTS)
                ));
                s.setTotalTimeMs(c.getLong(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_TOTAL_TIME_MS)
                ));
                s.setLastPlayedMs(c.getLong(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_LAST_PLAYED_MS)
                ));
                s.setLastScore(c.getInt(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_LAST_SCORE)
                ));
                s.setLastAccuracy(c.getDouble(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_LAST_ACCURACY)
                ));
                s.setLastSessionTimeMs(c.getLong(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_LAST_SESSION_TIME_MS)
                ));
                s.setBestScore(c.getInt(
                        c.getColumnIndexOrThrow(COLUMN_ANALYTICS_BEST_SCORE)
                ));

                list.add(s);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading analytics cursor", e);
        } finally {
            if (c != null) {
                c.close();
            }
        }

        // Attach adapter so that rows appear on screen
        recyclerReports.setAdapter(new ReportsAdapter(list));
    }
}
