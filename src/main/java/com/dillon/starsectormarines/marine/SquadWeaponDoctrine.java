package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable squad-wide definition for primary and special-equipment issue.
 *
 * <p>Built-in definitions name catalog keys ({@code @key}), resolved when read. A
 * player-owned definition is saved with its name as typed, which is shown as is. Two
 * kinds of saved definition carry a generated English name instead (the first refit's
 * plans and a migrated legacy issue); while that name is still the generated one it is
 * translated when shown.
 */
public final class SquadWeaponDoctrine implements Serializable {

    /** What a player-authored definition is described as; saved as the key, read in the reader's language. */
    public static final String CUSTOM_DESCRIPTION = "@squadDoctrineCustomDescription";
    /** The English description saves written before {@link #CUSTOM_DESCRIPTION}. */
    private static final String LEGACY_CUSTOM_DESCRIPTION = "Player-authored squad weapon definition.";
    private static final String MIGRATED_ID_PREFIX = "migrated:weapons:";
    private static final String MIGRATED_SUFFIX = " Legacy Weapon Issue";

    private final String id;
    private final String displayName;
    private final String description;
    private final List<SquadWeaponIssue> issues;

    public SquadWeaponDoctrine(String id, String displayName, String description,
                               List<SquadWeaponIssue> issues) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Doctrine id is required");
        if (issues == null || issues.size() != MarineSquad.CAPACITY
                || issues.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "A squad weapon doctrine requires exactly " + MarineSquad.CAPACITY + " issues");
        }
        this.id = id.trim();
        this.displayName = displayName != null && !displayName.isBlank()
                ? displayName.trim() : this.id;
        this.description = description != null ? description.trim() : "";
        this.issues = new ArrayList<>(issues);
    }

    public String id() { return id; }
    public String displayName() {
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            if (path.weapons.equals(id) && displayName.equals(FirstRefit.savedDoctrineName(path))) {
                return SavedDefaultText.formatOr(displayName, "squadDoctrineWaylineName", path.label());
            }
        }
        if (id.startsWith(MIGRATED_ID_PREFIX) && displayName.endsWith(MIGRATED_SUFFIX)
                && displayName.length() > MIGRATED_SUFFIX.length()) {
            String squad = displayName.substring(0, displayName.length() - MIGRATED_SUFFIX.length());
            return SavedDefaultText.formatOr(displayName, "squadDoctrineMigratedName",
                    MarineSquad.displayName(squad));
        }
        return Strings.resolve(displayName);
    }

    public String description() {
        return Strings.resolve(LEGACY_CUSTOM_DESCRIPTION.equals(description) ? CUSTOM_DESCRIPTION : description);
    }

    /** The name as saved, for a rename or a copy that must not bake translated text into the save. */
    String savedDisplayName() { return displayName; }
    /** The description as saved, for the same reason. */
    String savedDescription() { return description; }

    /** Saved name of the definition migrated from a legacy squad's fire-team cards. */
    static String migratedName(String savedSquadName) {
        return savedSquadName + MIGRATED_SUFFIX;
    }
    public List<SquadWeaponIssue> issues() { return Collections.unmodifiableList(issues); }
    public SquadWeaponIssue issue(int billet) { return issues.get(billet); }
}
