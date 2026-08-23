package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reusable Alpha/Bravo/Charlie template composition for one line squad. */
public final class SquadArrangement implements Serializable {

    private String id;
    private String displayName;
    private List<String> templateIds;

    public SquadArrangement(String id, String displayName,
                            List<String> templateIds) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Arrangement id is required");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Arrangement name is required");
        }
        validateTemplates(templateIds);
        this.id = id.trim();
        this.displayName = displayName.trim();
        this.templateIds = new ArrayList<>(templateIds);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<String> templateIds() {
        return Collections.unmodifiableList(templateIds);
    }
    public String templateId(int teamIndex) {
        return templateIds.get(teamIndex);
    }

    public boolean referencesTemplate(String templateId) {
        return templateId != null && templateIds.contains(templateId);
    }

    void rename(String value) {
        if (value != null && !value.isBlank()) displayName = value.trim();
    }

    private static void validateTemplates(List<String> templateIds) {
        if (templateIds == null || templateIds.size() != MarineSquad.TEAMS_PER_SQUAD
                || templateIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException(
                    "A squad arrangement requires exactly three fire-team templates");
        }
    }

    private Object readResolve() {
        if (id == null || id.isBlank() || displayName == null || displayName.isBlank()) {
            throw new IllegalStateException("Invalid persisted squad arrangement");
        }
        try {
            validateTemplates(templateIds);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Invalid persisted squad arrangement", invalid);
        }
        templateIds = new ArrayList<>(templateIds);
        return this;
    }
}
