package com.example.brightbuds_app.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.ui.games.WordBuilderFragment;
import com.example.brightbuds_app.utils.Constants;
import com.example.brightbuds_app.utils.ModuleIds;

public class WordBuilderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_builder);

        if (savedInstanceState == null) {
            String childId = getIntent().getStringExtra("child_id");
            String childName = getIntent().getStringExtra("child_name");


            String moduleId = ModuleIds.MODULE_WORD_BUILDER;

            WordBuilderFragment fragment = new WordBuilderFragment();
            Bundle args = new Bundle();
            args.putString("child_id", childId);
            args.putString("child_name", childName);
            args.putString("module_id", moduleId);
            fragment.setArguments(args);

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.wordBuilderFragmentContainer, fragment)
                    .commit();
        }
    }
}
