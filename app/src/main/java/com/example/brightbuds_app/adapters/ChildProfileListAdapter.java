package com.example.brightbuds_app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.ChildProfile;

import java.util.List;

/**
 * Adapter that shows each child profile as a large avatar with text underneath.
 */
public class ChildProfileListAdapter
        extends RecyclerView.Adapter<ChildProfileListAdapter.ChildViewHolder> {

    /**
     * Listener for when a child card is tapped.
     */
    public interface OnChildClickListener {
        void onChildClicked(ChildProfile child);
    }

    private final List<ChildProfile> children;
    private final OnChildClickListener listener;

    public ChildProfileListAdapter(List<ChildProfile> children,
                                   OnChildClickListener listener) {
        this.children = children;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ChildViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_child_profile, parent, false);
        return new ChildViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
        ChildProfile child = children.get(position);
        Context context = holder.itemView.getContext();

        // Name (local only, never sent to Firestore)
        String name = child.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "Child " + (position + 1);
        }
        holder.tvChildName.setText(name);

        // Age and learning level display
        String ageText = "-";
        if (child.getAge() > 0) {
            ageText = String.valueOf(child.getAge());
        }

        String level = child.getLearningLevel();
        if (level == null || level.trim().isEmpty()) {
            level = "-";
        }

        String details = "Age: " + ageText + "  •  Level: " + level;
        holder.tvChildDetails.setText(details);

        // Anonymous child code, for example BB1, BB2, etc.
        String code = child.getChildCode();
        if (code == null || code.trim().isEmpty()) {
            // Fallback if model code is missing
            code = "BB" + (position + 1);
        }
        holder.tvChildCode.setText(code);

        // Avatar
        int avatarResId = child.resolveAvatarResId(context);
        holder.imgChildAvatar.setImageResource(avatarResId);

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChildClicked(child);
            }
        });
    }

    @Override
    public int getItemCount() {
        return children != null ? children.size() : 0;
    }

    static class ChildViewHolder extends RecyclerView.ViewHolder {

        ImageView imgChildAvatar;
        TextView tvChildName;
        TextView tvChildDetails;
        TextView tvChildCode;

        ChildViewHolder(@NonNull View itemView) {
            super(itemView);
            imgChildAvatar = itemView.findViewById(R.id.imgChildAvatar);
            tvChildName = itemView.findViewById(R.id.tvChildName);
            tvChildDetails = itemView.findViewById(R.id.tvChildDetails);
            tvChildCode = itemView.findViewById(R.id.tvChildCode);
        }
    }
}
