package com.example.brightbuds_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.ModuleSummary;

import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter that shows analytics rows for one child.
 * Each row represents one module such as Feed the Monster.
 */
public class ReportsAdapter extends RecyclerView.Adapter<ReportsAdapter.ReportViewHolder> {

    // All module summaries to display
    private final List<ModuleSummary> summaries;

    public ReportsAdapter(List<ModuleSummary> summaries) {
        this.summaries = summaries;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate one row for the table from XML
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_row, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        // Get the summary object for this row
        ModuleSummary s = summaries.get(position);

        // Set friendly module name
        holder.txtModuleName.setText(s.getModuleName());

        // Session count is a raw integer
        holder.txtSessions.setText("Sessions: " + s.getSessionCount());

        // Average score formatted to one decimal place
        holder.txtAvgScore.setText(String.format(
                Locale.getDefault(),
                "Average score: %.1f",
                s.getAverageScore()
        ));

        // Accuracy converted from ratio to percentage and rounded
        holder.txtAccuracy.setText(String.format(
                Locale.getDefault(),
                "Accuracy: %.0f%%",
                s.getAccuracyRatio() * 100
        ));

        // Total time spent displayed as minute:second
        holder.txtTime.setText("Time: " + formatTime(s.getTotalTimeMs()));

        // Highest score seen across sessions
        holder.txtBestScore.setText("Best score: " + s.getBestScore());
    }

    @Override
    public int getItemCount() {
        return summaries.size();
    }

    // Holds references to the views for one row
    static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView txtModuleName;
        TextView txtSessions;
        TextView txtAvgScore;
        TextView txtAccuracy;
        TextView txtTime;
        TextView txtBestScore;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            txtModuleName = itemView.findViewById(R.id.txtModuleName);
            txtSessions = itemView.findViewById(R.id.txtSessions);
            txtAvgScore = itemView.findViewById(R.id.txtAvgScore);
            txtAccuracy = itemView.findViewById(R.id.txtAccuracy);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtBestScore = itemView.findViewById(R.id.txtBestScore);
        }
    }

    /**
     * Utility to convert milliseconds to mm:ss text.
     */
    private static String formatTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
