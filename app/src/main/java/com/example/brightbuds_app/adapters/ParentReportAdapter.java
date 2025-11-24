package com.example.brightbuds_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.Progress;

import java.util.List;

public class ParentReportAdapter extends RecyclerView.Adapter<ParentReportAdapter.ReportViewHolder> {

    private final List<Progress> items;

    public ParentReportAdapter(List<Progress> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_parent_report_row, parent, false);
        return new ReportViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Progress p = items.get(position);

        String moduleName = p.getModuleId() != null ? p.getModuleId() : "Module";
        String scoreStr = Math.round(p.getScore()) + "%";
        String status = p.getStatus() != null ? p.getStatus() : "";

        long timeSpentMs = p.getTimeSpent();
        double minutes = timeSpentMs / 60000.0;
        String timeStr = String.format("%.1f", minutes);

        holder.tvModuleName.setText(moduleName);
        holder.tvModuleScore.setText(scoreStr);
        holder.tvModuleStatus.setText(status);
        holder.tvModuleTime.setText(timeStr);
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {

        TextView tvModuleName;
        TextView tvModuleScore;
        TextView tvModuleStatus;
        TextView tvModuleTime;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvModuleName = itemView.findViewById(R.id.tvModuleName);
            tvModuleScore = itemView.findViewById(R.id.tvModuleScore);
            tvModuleStatus = itemView.findViewById(R.id.tvModuleStatus);
            tvModuleTime = itemView.findViewById(R.id.tvModuleTime);
        }
    }
}
