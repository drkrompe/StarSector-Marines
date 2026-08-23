package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable armory template for one fire team. The template is not inventory;
 * every assignment requires the fleet to supply all four billet issues.
 */
public final class FireTeamTemplateCard implements Serializable {

    private String id;
    private String displayName;
    private List<FireTeamBillet> billets;

    public FireTeamTemplateCard(String id, String displayName,
                                List<FireTeamBillet> billets) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Template id is required");
        }
        if (billets == null || billets.size() != MarineSquad.TEAM_SIZE) {
            throw new IllegalArgumentException(
                    "A fire-team template requires exactly " + MarineSquad.TEAM_SIZE + " billets");
        }
        this.id = id.trim();
        this.displayName = displayName != null && !displayName.isBlank()
                ? displayName.trim() : this.id;
        this.billets = new ArrayList<>(billets);
        if (this.billets.contains(null)) {
            throw new IllegalArgumentException("Template billets cannot be null");
        }
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<FireTeamBillet> billets() {
        return Collections.unmodifiableList(billets);
    }
    public FireTeamBillet billet(int index) { return billets.get(index); }

    void rename(String value) {
        if (value != null && !value.isBlank()) displayName = value.trim();
    }

    private Object readResolve() {
        if (id == null || id.isBlank() || billets == null
                || billets.size() != MarineSquad.TEAM_SIZE || billets.contains(null)) {
            throw new IllegalStateException("Invalid persisted fire-team template");
        }
        if (displayName == null || displayName.isBlank()) displayName = id;
        billets = new ArrayList<>(billets);
        return this;
    }
}
