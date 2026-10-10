package de.kreiscraft.modules.settingsgui;

/**
 * A page of the Server Configuration GUI: either the category list ({@link #root()}) or one
 * category's field editor.
 *
 * @param category the category id whose fields are shown, or {@code null} for the root category list
 */
public record ServerConfigPage(String category) {

    /** The page listing every editable category. */
    public static ServerConfigPage root() {
        return new ServerConfigPage(null);
    }

    /** The page editing one category's fields. */
    public static ServerConfigPage of(String category) {
        return new ServerConfigPage(category);
    }

    public boolean isRoot() {
        return category == null;
    }
}
