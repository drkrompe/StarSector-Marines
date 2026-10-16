package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The company's own mechs on this field, by campaign id, so what the battle did to their
 * sections writes back after it. The counterpart of the convoy's company-vehicle tags.
 *
 * <p>Holds each mech's loadout rather than its entity: a dead chassis's loadout leaves the
 * world when its wreck spawns, but the object, its {@link MechSections} and its
 * {@code wreckSpawned} mark stay readable here. A mech still aboard its carrier was never
 * tagged and has nothing to report.
 */
public final class CompanyMechLedger {

    private final Map<String, MechLoadoutComponent> mechs = new LinkedHashMap<>();

    /** Marks a landed mech as the company's own {@code campaignId}; null ids are unowned. */
    public void tag(String campaignId, MechLoadoutComponent loadout) {
        if (campaignId != null && loadout != null) mechs.put(campaignId, loadout);
    }

    /**
     * What the battle left of each company mech that was hit, in landing order. A mech nobody
     * hit is intact and absent. A chassis that died without sections (the whole-body control)
     * reports a lost chassis with no section record.
     */
    public List<Damage> damage() {
        if (mechs.isEmpty()) return List.of();
        List<Damage> out = new ArrayList<>();
        for (Map.Entry<String, MechLoadoutComponent> entry : mechs.entrySet()) {
            MechLoadoutComponent loadout = entry.getValue();
            MechSections sections = loadout.sections();
            boolean dead = loadout.wreckSpawned;
            if (sections == null && !dead) continue;
            MechSections.Report report = sections != null ? sections.report() : null;
            boolean chassisLost = dead || (report != null && report.chassisLost());
            out.add(new Damage(entry.getKey(), report, chassisLost));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * One company mech's frozen battle damage.
     *
     * @param report its sections as the battle left them, or null when it died as one body
     * @param chassisLost the torso was destroyed (or the chassis died): the mech is lost
     */
    public record Damage(String campaignId, MechSections.Report report, boolean chassisLost) {
    }
}
