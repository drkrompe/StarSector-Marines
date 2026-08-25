package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reusable squad-wide definition for mixed armour issue. */
public final class SquadArmorDoctrine implements Serializable {

    private final String id;
    private final String displayName;
    private final String description;
    private final List<MarineArmorPattern> issues;

    public SquadArmorDoctrine(String id, String displayName, String description,
                              List<MarineArmorPattern> issues) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Doctrine id is required");
        if (issues == null || issues.size() != MarineSquad.CAPACITY || issues.contains(null)) {
            throw new IllegalArgumentException(
                    "A squad armour doctrine requires exactly " + MarineSquad.CAPACITY + " issues");
        }
        this.id = id.trim();
        this.displayName = displayName != null && !displayName.isBlank()
                ? displayName.trim() : this.id;
        this.description = description != null ? description.trim() : "";
        this.issues = new ArrayList<>(issues);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public List<MarineArmorPattern> issues() { return Collections.unmodifiableList(issues); }
    public MarineArmorPattern issue(int billet) { return issues.get(billet); }
}
