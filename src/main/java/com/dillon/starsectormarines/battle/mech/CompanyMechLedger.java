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
    private final List<Landed> landed = new ArrayList<>();

    /** Marks a landed mech as the company's own {@code campaignId}; null ids are unowned. */
    public void tag(String campaignId, MechLoadoutComponent loadout) {
        if (campaignId != null && loadout != null) mechs.put(campaignId, loadout);
    }

    /**
     * Records a mech landed on this field under {@code displayName}, which may be null, and tags it
     * when it is the company's own. An unowned mech writes nothing back, but the debrief still shows it.
     */
    public void land(String campaignId, String displayName, MechLoadoutComponent loadout) {
        if (loadout == null) return;
        tag(campaignId, loadout);
        landed.add(new Landed(campaignId, displayName, loadout));
    }

    /** Every mech that landed, in landing order, with what the battle left of it: the debrief's force. */
    public List<Fielded> fielded() {
        if (landed.isEmpty()) return List.of();
        List<Fielded> out = new ArrayList<>(landed.size());
        for (Landed mech : landed) {
            MechSections sections = mech.loadout().sections();
            MechSections.Report report = sections != null ? sections.report() : whole(mech.loadout());
            out.add(new Fielded(mech.campaignId(), mech.displayName(), mech.loadout().variant, report,
                    mech.loadout().wreckSpawned || report.chassisLost()));
        }
        return Collections.unmodifiableList(out);
    }

    /** The record of a mech nobody hit: its sections are not divided until the first hit. */
    private static MechSections.Report whole(MechLoadoutComponent loadout) {
        int n = MechSection.values().length;
        boolean[] fitted = new boolean[n];
        float[] full = new float[n];
        for (MechSection section : MechSection.values()) {
            MechMountSlot slot = section.mount();
            fitted[section.ordinal()] = slot == null || loadout.mounts()[slot.ordinal()] != null;
            full[section.ordinal()] = 1f;
        }
        return new MechSections.Report(loadout.variant.bodyPlan().id(), fitted, full, full.clone(), new boolean[n],
                new MechWeaponComponent[MechMountSlot.values().length]);
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

    private record Landed(String campaignId, String displayName, MechLoadoutComponent loadout) {
    }

    /**
     * One mech that took the field, frozen for the debrief.
     *
     * @param campaignId the company mech it is, or null for one the company does not own
     * @param displayName its name, or null when it has none of its own
     * @param report its sections as the battle left them; whole when nothing hit it
     * @param lost the chassis died
     */
    public record Fielded(String campaignId, String displayName, MechVariant variant,
                          MechSections.Report report, boolean lost) {
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
