package com.example.brightbuds_app.adapters;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.FamilyMember;

import java.io.File;
import java.util.List;

public class FamilyMemberAdapter extends RecyclerView.Adapter<FamilyMemberAdapter.FamilyViewHolder> {

    public interface OnFamilyMemberClickListener {
        void onEditClicked(FamilyMember member);
        void onDeleteClicked(FamilyMember member);
    }

    private final List<FamilyMember> items;
    private final OnFamilyMemberClickListener listener;

    public FamilyMemberAdapter(List<FamilyMember> items,
                               OnFamilyMemberClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FamilyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_family_member, parent, false);
        return new FamilyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FamilyViewHolder holder, int position) {
        FamilyMember member = items.get(position);

        holder.txtName.setText(member.getFirstName());
        holder.txtRelationship.setText(member.getRelationship());

        String path = member.getImagePath();
        if (!TextUtils.isEmpty(path)) {
            Glide.with(holder.itemView.getContext())
                    .load(new File(path))
                    .centerCrop()
                    .into(holder.imgPhoto);
        } else {
            holder.imgPhoto.setImageResource(R.drawable.ic_child_avatar_placeholder);
        }

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditClicked(member);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDeleteClicked(member);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FamilyViewHolder extends RecyclerView.ViewHolder {
        ImageView imgPhoto;
        TextView txtName;
        TextView txtRelationship;
        ImageView btnEdit;
        ImageView btnDelete;

        FamilyViewHolder(@NonNull View itemView) {
            super(itemView);
            imgPhoto = itemView.findViewById(R.id.imgPhoto);
            txtName = itemView.findViewById(R.id.txtName);
            txtRelationship = itemView.findViewById(R.id.txtRelationship);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
