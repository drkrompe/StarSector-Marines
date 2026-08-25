package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reusable squad-wide definition for primary and special-equipment issue. */
public final class SquadWeaponDoctrine implements Serializable {

    private final String id;
    private final String displayName;
    private final String description;
    private final List<SquadWeaponIssue> issues;

    public SquadWeaponDoctrine(String id, String displayName, String description,
                               List<SquadWeaponIssue> issues) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Doctrine id is required");
        if (issues == null || issues.size() != MarineSquad.CAPACITY || issues.contains(null)) {
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
    public String displayName() { return displayName; }
    public String description() { return description; }
    public List<SquadWeaponIssue> issues() { return Collections.unmodifiableList(issues); }
    public SquadWeaponIssue issue(int billet) { return issues.get(billet); }
}
