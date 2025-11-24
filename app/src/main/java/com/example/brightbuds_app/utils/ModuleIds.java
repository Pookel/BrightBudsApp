package com.example.brightbuds_app.utils;

/**
 * Central module identifier list for analytics and reports.
 * These values are stable keys used in local analytics and Firestore.
 */
public final class ModuleIds {

    // Prevent instantiation because this class only holds constants and helpers
    private ModuleIds() { }

    // Game modules
    public static final String MODULE_FEED_MONSTER   = "feed_monster";
    public static final String MODULE_MEMORY_MATCH   = "memory_match";
    public static final String MODULE_MATCH_LETTER   = "match_letter";
    public static final String MODULE_FAMILY_GALLERY = "family_gallery";

    // Word builder module
    public static final String MODULE_WORD_BUILDER   = "word_builder";

    // Song modules
    public static final String MODULE_ABC_SONG       = "abc_song";
    public static final String MODULE_123_SONG       = "numbers_song";
    public static final String MODULE_SHAPES_SONG    = "shapes_song";

    // Placeholder for future games
    public static final String MODULE_FUTURE_MINI_GAME = "future_mini_game";

    /**
     * Returns a user friendly name for a module id.
     * This is used on parent reports so that parents see readable labels
     * like "Feed the Monster" instead of internal ids.
     */
    public static String getModuleDisplayName(String moduleId) {
        if (moduleId == null) {
            return "Unknown module";
        }

        switch (moduleId) {
            case MODULE_FEED_MONSTER:
                return "Feed the Monster";

            case MODULE_MEMORY_MATCH:
                return "Memory Match";

            case MODULE_MATCH_LETTER:
                return "Match the Letter";

            case MODULE_FAMILY_GALLERY:
                return "Family Gallery";

            case MODULE_WORD_BUILDER:
                return "Word Builder";

            case MODULE_ABC_SONG:
                return "ABC Song";

            case MODULE_123_SONG:
                return "123 Song";

            case MODULE_SHAPES_SONG:
                return "Shapes Song";

            case MODULE_FUTURE_MINI_GAME:
                return "Future Mini Game";

            default:
                // Fallback returns the original id so that unknown modules
                // still display something rather than an empty label.
                return moduleId;
        }
    }
}
