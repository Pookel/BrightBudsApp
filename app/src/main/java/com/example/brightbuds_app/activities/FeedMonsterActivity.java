package com.example.brightbuds_app.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.brightbuds_app.R;

/**
 * Hosts the FeedTheMonsterFragment inside a FragmentContainerView.
 * This activity only sets up the container and title.
 * All game behaviour lives inside the Fragment.
 */
public class FeedMonsterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed_monster);
        setTitle("Feed the Monster");
    }
}
