package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reusable squad-wide definition for mixed armour issue. */
public final class SquadArmorDoctrine implements Serializable {

    private static final Logger LOG = Global.getLogger(SquadArmorDoctrine.class);

    private final String id;
    private final String displayName;
    private final String description;
    /** Legacy save input and built-in compatibility handles. */
    private List<MarineArmorPattern> issues;
    /** Authoritative persisted catalog identities. */
    private List<String> issueIds;

    public SquadArmorDoctrine(String id, String displayName, String description,
                              List<MarineArmorPattern> issues) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Doctrine id is required");
        if (issues == null || issues.size() != MarineSquad.CAPACITY
                || issues.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "A squad armour doctrine requires exactly " + MarineSquad.CAPACITY + " issues");
        }
        this.id = id.trim();
        this.displayName = displayName != null && !displayName.isBlank()
                ? displayName.trim() : this.id;
        this.description = description != null ? description.trim() : "";
        this.issues = null;
        this.issueIds = new ArrayList<>(issues.stream().map(pattern -> pattern.id).toList());
    }

    private SquadArmorDoctrine(String id, String displayName, String description,
                               List<String> issueIds, boolean stableIds) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Doctrine id is required");
        if (issueIds == null || issueIds.size() != MarineSquad.CAPACITY
                || issueIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "A squad armour doctrine requires exactly " + MarineSquad.CAPACITY + " issues");
        }
        for (String armorId : issueIds) MarineArmorCatalogRegistry.require(armorId);
        this.id = id.trim();
        this.displayName = displayName != null && !displayName.isBlank()
                ? displayName.trim() : this.id;
        this.description = description != null ? description.trim() : "";
        this.issueIds = new ArrayList<>(issueIds);
        this.issues = null;
    }

    public static SquadArmorDoctrine fromIds(
            String id, String displayName, String description, List<String> issueIds) {
        return new SquadArmorDoctrine(id, displayName, description, issueIds, true);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }
    public List<MarineArmorPattern> issues() {
        return Collections.unmodifiableList(issueIds.stream()
                .map(SquadArmorDoctrine::compatibilityHandle).toList());
    }
    public MarineArmorPattern issue(int billet) { return compatibilityHandle(issueId(billet)); }
    public List<String> issueIds() { return Collections.unmodifiableList(issueIds); }
    public String issueId(int billet) { return issueIds.get(billet); }
    public MarineArmorCatalogDef issueDef(int billet) {
        return MarineArmorCatalogRegistry.require(issueId(billet));
    }

    private Object readResolve() {
        if (issueIds == null) {
            issueIds = new ArrayList<>();
            if (issues != null) {
                for (MarineArmorPattern pattern : issues) {
                    issueIds.add(pattern != null ? pattern.id : MarineArmorPattern.ARMORLESS.id);
                }
            }
        }
        List<String> repaired = new ArrayList<>();
        for (int index = 0; index < MarineSquad.CAPACITY; index++) {
            String armorId = index < issueIds.size() ? issueIds.get(index) : null;
            if (MarineArmorCatalogRegistry.installed() == null
                    || MarineArmorCatalogRegistry.installed().get(armorId) == null) {
                LOG.warn("Repairing armor doctrine '" + id + "' billet " + index
                        + " unresolved armor '" + armorId + "' to starter armor '"
                        + MarineArmorPattern.ARMORLESS.id + "'");
                armorId = MarineArmorPattern.ARMORLESS.id;
            }
            repaired.add(armorId);
        }
        issueIds = repaired;
        issues = null;
        return this;
    }

    private static MarineArmorPattern compatibilityHandle(String id) {
        try {
            return MarineArmorPattern.fromId(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
