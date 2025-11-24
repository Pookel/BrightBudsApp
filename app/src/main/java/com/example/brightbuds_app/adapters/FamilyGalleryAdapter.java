package com.example.brightbuds_app.adapters;

import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.FamilyMember;

import java.io.File;
import java.util.List;

/*
 * ViewPager2 adapter that displays one family photo per page.
 * The text (relationship & name) is handled in FamilyGalleryFragment.
 */
public class FamilyGalleryAdapter extends RecyclerView.Adapter<FamilyGalleryAdapter.FamilyViewHolder> {

    private static final String TAG = "FamilyGalleryAdapter";

    private final List<FamilyMember> familyMembers;

    public FamilyGalleryAdapter(List<FamilyMember> familyMembers) {
        this.familyMembers = familyMembers;
    }

    @NonNull
    @Override
    public FamilyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_family_gallery_page, parent, false);
        return new FamilyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FamilyViewHolder holder, int position) {
        FamilyMember member = familyMembers.get(position);
        String path = member.getImagePath();

        if (path != null && !path.trim().isEmpty()) {
            try {
                Object source;

                // Handle both content URIs and plain file paths
                if (path.startsWith("content://") || path.startsWith("file://")) {
                    source = Uri.parse(path);
                } else {
                    source = new File(path);
                }

                Glide.with(holder.itemView.getContext())
                        .load(source)
                        .placeholder(R.drawable.ic_family_placeholder)
                        .error(R.drawable.ic_family_placeholder)
                        .centerCrop()
                        .into(holder.imageView);

            } catch (Exception e) {
                Log.e(TAG, "Error loading family photo at path: " + path, e);
                holder.imageView.setImageResource(R.drawable.ic_family_placeholder);
            }

        } else {
            Log.w(TAG, "Empty or null imagePath for family member at position " + position);
            holder.imageView.setImageResource(R.drawable.ic_family_placeholder);
        }
    }

    @Override
    public int getItemCount() {
        return familyMembers.size();
    }

    static class FamilyViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        FamilyViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imgFamilyPage);
        }
    }
}
