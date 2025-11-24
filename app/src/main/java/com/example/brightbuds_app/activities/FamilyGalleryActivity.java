package com.example.brightbuds_app.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.ui.games.FamilyGalleryFragment;


/*
 * Hosts the FamilyGalleryFragment in a simple container.
 * No game logic lives here. The activity only sets up the screen.
 */
public class FamilyGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_gallery);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.familyGalleryFragmentContainer, new FamilyGalleryFragment())
                    .commit();
        }
    }
}
