package com.example.brightbuds_app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brightbuds_app.R;
import com.example.brightbuds_app.models.Module;
import com.example.brightbuds_app.services.ModuleService;
import com.example.brightbuds_app.services.ProgressService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

/**
 * GameModulesActivity
 *
 * Reads all learning modules from Firestore and shows them in a vertical list.
 * When the parent taps Play on a module, this activity routes to the correct
 * screen using the module document ID from Firestore.
 *
 * It passes:
 *   - childId
 *   - moduleId
 *   - moduleTitle
 *   - storagePath (for video modules only)
 */
public class GameModulesActivity extends AppCompatActivity {

    private LinearLayout container;
    private final ModuleService moduleService = new ModuleService();
    private ProgressService progressService;
    private String childId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_modules);

        container = findViewById(R.id.modulesContainer);
        progressService = new ProgressService(this);

        // Child that is currently using these modules
        childId = getIntent().getStringExtra("childId");

        loadModules();
    }

    // -------------------------------------------------
    // LOAD MODULES FROM FIRESTORE
    // -------------------------------------------------
    private void loadModules() {
        container.removeAllViews();

        // Show simple loading row while we query Firestore
        View loading = getLayoutInflater()
                .inflate(R.layout.item_module_loading, container, false);
        container.addView(loading);

        moduleService.getAllModules(new ModuleService.ModulesCallback() {
            @Override
            public void onSuccess(List<Module> modules) {
                container.removeAllViews();

                if (modules == null || modules.isEmpty()) {
                    addEmpty("No modules available yet.");
                    return;
                }

                for (Module m : modules) {
                    if (m != null && m.isActive()) {
                        addModuleRow(m);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                container.removeAllViews();
                String message = "Failed to load modules";
                if (e != null && e.getMessage() != null) {
                    message += ": " + e.getMessage();
                }
                addEmpty(message);
            }
        });
    }

    // -------------------------------------------------
    // BUILD ONE MODULE ROW
    // -------------------------------------------------
    private void addModuleRow(Module module) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_module_row, container, false);

        MaterialTextView title = row.findViewById(R.id.txtModuleTitle);
        MaterialTextView subtitle = row.findViewById(R.id.txtModuleSubtitle);
        ImageView icon = row.findViewById(R.id.imgModuleIcon);
        MaterialButton btnPlay = row.findViewById(R.id.btnPlay);

        // Title
        String displayTitle = module.getTitle() != null
                ? module.getTitle()
                : "Untitled Module";
        if ("Family Module".equalsIgnoreCase(displayTitle)) {
            displayTitle = "My Family";
        }
        title.setText(displayTitle);

        // Subtitle (lightweight defaults based on title)
        String t = displayTitle.toLowerCase();
        if (t.contains("family")) {
            subtitle.setText("Learn about family members.");
        } else if (t.contains("monster")) {
            subtitle.setText("Feed the Monster and learn words.");
        } else if (t.contains("memory")) {
            subtitle.setText("Match and remember fun images.");
        } else if (t.contains("match")) {
            subtitle.setText("Interactive matching game.");
        } else if (t.contains("word")) {
            subtitle.setText("Build and learn new words.");
        } else if (t.contains("abc")) {
            subtitle.setText("Sing along to the ABC Song.");
        } else if (t.contains("123")) {
            subtitle.setText("Count and learn with numbers.");
        } else if (t.contains("shapes")) {
            subtitle.setText("Sing and learn different shapes.");
        } else {
            subtitle.setText("Interactive learning module.");
        }

        // Icon mapping
        icon.setImageResource(getModuleIcon(displayTitle));

        // Play button opens the correct module Activity
        btnPlay.setOnClickListener(v -> launchModule(module));

        container.addView(row);
    }

    // -------------------------------------------------
    // ICON PICKER
    // -------------------------------------------------
    private int getModuleIcon(String title) {
        if (title == null) {
            return R.drawable.ic_brightbuds_logo;
        }

        String t = title.toLowerCase();

        if (t.contains("family")) return R.drawable.ic_my_family;
        if (t.contains("monster")) return R.drawable.ic_monster;
        if (t.contains("memory")) return R.drawable.ic_memory;
        if (t.contains("match")) return R.drawable.ic_match;
        if (t.contains("word")) return R.drawable.ic_word_builder;
        if (t.contains("abc")) return R.drawable.ic_abc_music;
        if (t.contains("123") || t.contains("number")) return R.drawable.ic_numbers;
        if (t.contains("shapes")) return R.drawable.ic_shapes;

        return R.drawable.ic_brightbuds_logo;
    }

    // -------------------------------------------------
    // NAVIGATION: ROUTE TO CORRECT ACTIVITY
    // -------------------------------------------------
    private void launchModule(Module m) {
        String id = m.getId() != null ? m.getId().trim().toLowerCase() : "";
        String title = m.getTitle() != null ? m.getTitle().trim().toLowerCase() : "";
        String type = m.getType() != null ? m.getType().trim().toLowerCase() : "";

        // Helpful debug prints while you test
        System.out.println("DEBUG Module Clicked - ID: '" + id
                + "', Title: '" + title
                + "', Type: '" + type + "'");

        Intent i;

        // 1) Prefer stable document IDs from Firestore
        switch (id) {
            case "module_family":
            case "family_module":
                // Family gallery module
                i = new Intent(this, FamilyGalleryActivity.class);
                System.out.println("DEBUG: Going to FamilyGalleryActivity via ID match");
                break;

            case "game_feed_monster":
                i = new Intent(this, FeedMonsterActivity.class);
                System.out.println("DEBUG: Going to FeedMonsterActivity via ID match");
                break;

            case "game_match_letter":
                i = new Intent(this, MatchLetterActivity.class);
                System.out.println("DEBUG: Going to MatchLetterActivity via ID match");
                break;

            case "game_memory_match":
                i = new Intent(this, MemoryMatchActivity.class);
                System.out.println("DEBUG: Going to MemoryMatchActivity via ID match");
                break;

            case "game_word_builder":
                i = new Intent(this, WordBuilderActivity.class);
                System.out.println("DEBUG: Going to WordBuilderActivity via ID match");
                break;

            // Video based songs
            case "module_abc_song":
            case "module_123_song":
            case "module_shapes_song":
                i = new Intent(this, VideoModuleActivity.class);
                i.putExtra("storagePath", m.getStoragePath());
                System.out.println("DEBUG: Going to VideoModuleActivity via ID match");
                break;

            case "game_shapes_match":
                // Placeholder, in case you later add a Shapes game
                i = new Intent(this, ComingSoonActivity.class);
                i.putExtra("message", "Shapes Match game coming soon.");
                System.out.println("DEBUG: Going to ComingSoonActivity for Shapes Match via ID match");
                break;

            default:
                System.out.println("DEBUG: No ID match, falling back to title/type detection");

                // 2) Fallback to title or type if ID is missing or unexpected
                if (title.contains("family")) {
                    i = new Intent(this, FamilyGalleryActivity.class);
                    System.out.println("DEBUG: Going to FamilyGalleryActivity via title detection");

                } else if (title.contains("monster")) {
                    i = new Intent(this, FeedMonsterActivity.class);
                    System.out.println("DEBUG: Going to FeedMonsterActivity via title detection");

                } else if (title.contains("match") && title.contains("letter")) {
                    i = new Intent(this, MatchLetterActivity.class);
                    System.out.println("DEBUG: Going to MatchLetterActivity via title detection");

                } else if (title.contains("memory") || title.contains("match")) {
                    i = new Intent(this, MemoryMatchActivity.class);
                    System.out.println("DEBUG: Going to MemoryMatchActivity via title detection");

                } else if (title.contains("word") || title.contains("builder")) {
                    i = new Intent(this, WordBuilderActivity.class);
                    System.out.println("DEBUG: Going to WordBuilderActivity via title detection");

                } else if ("video".equals(type)
                        || title.contains("abc")
                        || title.contains("123")
                        || title.contains("shapes")
                        || title.contains("song")) {
                    i = new Intent(this, VideoModuleActivity.class);
                    i.putExtra("storagePath", m.getStoragePath());
                    System.out.println("DEBUG: Going to VideoModuleActivity via type or title detection");

                } else {
                    i = new Intent(this, ComingSoonActivity.class);
                    i.putExtra("message", "New learning module coming soon.");
                    System.out.println("DEBUG: Going to ComingSoonActivity no matches found");
                }
                break;
        }

        // Pass through child and module information to the next screen
        i.putExtra("childId", childId);
        i.putExtra("moduleId", m.getId());
        i.putExtra("moduleTitle", m.getTitle());

        startActivity(i);
    }

    // -------------------------------------------------
    // EMPTY STATE
    // -------------------------------------------------
    private void addEmpty(String message) {
        View empty = LayoutInflater.from(this)
                .inflate(R.layout.item_module_empty, container, false);
        MaterialTextView txt = empty.findViewById(R.id.txtEmpty);
        txt.setText(message);
        container.addView(empty);
    }
}
