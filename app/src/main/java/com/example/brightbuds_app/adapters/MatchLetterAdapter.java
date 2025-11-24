package com.example.brightbuds_app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.brightbuds_app.R;

import java.util.List;

/**
 * Adapter for the Match The Letter options.
 * Each item is a colored circular button with a letter.
 */
public class MatchLetterAdapter extends RecyclerView.Adapter<MatchLetterAdapter.OptionViewHolder> {

    public interface OnOptionClickListener {
        void onOptionClicked(int position);
    }

    private final List<String> letters;
    private final List<Integer> backgroundResIds;
    private final OnOptionClickListener listener;

    // Simple feedback states
    private int lastCorrectPos = -1;
    private int lastIncorrectPos = -1;

    public MatchLetterAdapter(List<String> letters,
                              List<Integer> backgroundResIds,
                              OnOptionClickListener listener) {
        this.letters = letters;
        this.backgroundResIds = backgroundResIds;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_match_letter_option, parent, false);

        return new OptionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OptionViewHolder holder, int position) {
        String letter = letters.get(position);
        holder.tvLetter.setText(letter);

        int bgRes = backgroundResIds.size() > position
                ? backgroundResIds.get(position)
                : R.drawable.circle_red;
        holder.tvLetter.setBackgroundResource(bgRes);

        // Reset any color state
        holder.tvLetter.setAlpha(1f);

        // A mild feedback state: correct gets slightly brighter, incorrect slightly faded
        if (position == lastCorrectPos) {
            holder.tvLetter.setAlpha(1f);
        } else if (position == lastIncorrectPos) {
            holder.tvLetter.setAlpha(0.6f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOptionClicked(holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return letters != null ? letters.size() : 0;
    }

    public void markCorrect(int position) {
        lastCorrectPos = position;
        lastIncorrectPos = -1;
        notifyItemChanged(position);
    }

    public void markIncorrect(int position) {
        lastIncorrectPos = position;
        lastCorrectPos = -1;
        notifyItemChanged(position);
    }

    static class OptionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLetter;

        OptionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLetter = itemView.findViewById(R.id.tvLetterOption);
        }
    }
}
