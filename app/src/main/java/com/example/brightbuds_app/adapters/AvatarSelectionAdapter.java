package com.example.brightbuds_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;

/**
 * Simple grid adapter that shows 6 avatar drawables and lets the user
 * select one. The selected avatar is visually highlighted by scaling it.
 *
 * The adapter works with a resource key string so that it matches
 * ChildProfile.avatarKey values like "avatar_1" or "ic_child_avatar_placeholder".
 */
public class AvatarSelectionAdapter
        extends RecyclerView.Adapter<AvatarSelectionAdapter.AvatarViewHolder> {

    public interface OnAvatarSelectedListener {
        void onAvatarSelected(int resId, String key);
    }

    private final int[] avatarResIds;
    private final OnAvatarSelectedListener listener;
    private String selectedKey;

    public AvatarSelectionAdapter(int[] avatarResIds,
                                  String initiallySelectedKey,
                                  OnAvatarSelectedListener listener) {
        this.avatarResIds = avatarResIds;
        this.listener = listener;
        this.selectedKey = initiallySelectedKey;
    }

    public void setSelectedKey(String key) {
        this.selectedKey = key;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AvatarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_avatar_choice, parent, false);
        return new AvatarViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AvatarViewHolder holder, int position) {
        int resId = avatarResIds[position];
        holder.imgAvatar.setImageResource(resId);

        String resKey = getResourceNameSafe(holder.itemView, resId);

        boolean isSelected =
                selectedKey != null && selectedKey.equals(resKey);

        // Very gentle visual highlight: scale up slightly when selected.
        holder.itemView.setScaleX(isSelected ? 1.15f : 1.0f);
        holder.itemView.setScaleY(isSelected ? 1.15f : 1.0f);

        holder.itemView.setOnClickListener(v -> {
            selectedKey = resKey;
            notifyDataSetChanged();
            if (listener != null) {
                listener.onAvatarSelected(resId, resKey);
            }
        });
    }

    @Override
    public int getItemCount() {
        return avatarResIds.length;
    }

    static class AvatarViewHolder extends RecyclerView.ViewHolder {
        ImageView imgAvatar;

        AvatarViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgAvatarChoice);
        }
    }

    /**
     * Safely resolves the resource entry name used as avatarKey.
     */
    private String getResourceNameSafe(View anyView, int resId) {
        try {
            return anyView.getContext()
                    .getResources()
                    .getResourceEntryName(resId);
        } catch (Exception e) {
            return "ic_child_avatar_placeholder";
        }
    }
}
