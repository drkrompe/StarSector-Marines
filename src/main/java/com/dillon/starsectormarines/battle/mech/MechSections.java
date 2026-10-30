package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.combat.ArmorArcs;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;

/**
 * The live armor and structure of each {@link MechSection} of one deployed mech, and what the
 * battle has cost it so far. The chassis's armor capacity and structure are divided across its
 * sections by its {@link MechBodyPlan}, so the totals are what the frame and plating set; the
 * armor rating stays the chassis's own.
 *
 * <p>Each hit is resolved on its section by {@link DurabilityModel}, exactly as a whole body is.
 * What a non-torso section cannot hold carries on into the torso, which meets it with its own
 * armor before its structure: a hit on a section already at zero goes through the hole to the
 * torso's plate. The torso at zero is the only death
 * ({@link #torsoDestroyed()}); legs at zero cripple the chassis, a mount at zero loses its weapon,
 * and the head at zero loses the sensors.
 *
 * <p>Written only on the serialized damage path. Read by the presentation (the section
 * paper-doll) and, after the battle, by campaign writeback ({@link #report()}), which turns a
 * lost mount into a lost assembly, other damaged sections into repairs, and a destroyed torso into
 * a lost chassis. See {@code mechs-nouns.md}.
 */
public final class MechSections {

    /** What a lost head leaves of the chassis's weapon accuracy. */
    public static final float SENSOR_LOSS_ACCURACY = 0.6f;
    /** What a lost head leaves of the chassis's vision range. */
    public static final float SENSOR_LOSS_VISION = 0.5f;

    private static final int TORSO = MechSection.TORSO.ordinal();

    /**
     * The torso's armor splits into a front plate and a rear one. The rear plate as a share of the torso's armor, held on top of it: thin, where the reactor and its cooling
     * sit. The front keeps the whole torso plate, so a mech shot head-on lasts as long as it did.
     */
    public static final float REAR_SHARE = .35f;

    private final MechBodyPlan plan;
    private final boolean[] fitted = new boolean[MechSection.VALUES.length];
    private final float[] armor = new float[MechSection.VALUES.length];
    private final float[] maxArmor = new float[MechSection.VALUES.length];
    private final float[] structure = new float[MechSection.VALUES.length];
    private final float[] maxStructure = new float[MechSection.VALUES.length];
    private final boolean[] destroyed = new boolean[MechSection.VALUES.length];
    private final MechWeaponComponent[] lostMounts = new MechWeaponComponent[MechMountSlot.values().length];
    private float rearArmor;
    private float maxRearArmor;

    /**
     * Divides the chassis pools as they stand across the sections. Current and maximum values
     * are divided by the same shares, so a chassis that enters battle with its plating already
     * worn has every section worn alike.
     *
     * @param mounts the deployed mounts by {@link MechMountSlot} ordinal; a null mount has no section
     */
    public MechSections(MechBodyPlan plan, MechWeaponMount[] mounts,
                        float structure, float maxStructure, float armor, float maxArmor) {
        this.plan = plan;
        float folded = 0f;
        for (MechSection section : MechSection.VALUES) {
            MechMountSlot slot = section.mount();
            fitted[section.ordinal()] = slot == null || mounts[slot.ordinal()] != null;
            if (!fitted[section.ordinal()]) folded += plan.poolShare(section);
        }
        for (MechSection section : MechSection.VALUES) {
            int i = section.ordinal();
            if (!fitted[i]) continue;
            float share = plan.poolShare(section) + (i == TORSO ? folded : 0f);
            this.structure[i] = structure * share;
            this.maxStructure[i] = maxStructure * share;
            this.armor[i] = armor * share;
            this.maxArmor[i] = maxArmor * share;
        }
        rearArmor = this.armor[TORSO] * REAR_SHARE;
        maxRearArmor = this.maxArmor[TORSO] * REAR_SHARE;
    }

    public MechBodyPlan plan() {
        return plan;
    }

    /** Whether the chassis was deployed with this section: false only for an empty mount. */
    public boolean fitted(MechSection section) {
        return fitted[section.ordinal()];
    }

    public float armor(MechSection section) {
        return armor[section.ordinal()];
    }

    public float maxArmor(MechSection section) {
        return maxArmor[section.ordinal()];
    }

    /**
     * Sets every section's armor and the rear plate to {@code armorShare} of their maximum and its structure to
     * {@code structureShare}, outside the damage path and without touching the world's columns: for staging a look
     * at a worn mech, never for play.
     */
    void stageWear(float armorShare, float structureShare) {
        float a = Math.max(0f, Math.min(1f, armorShare));
        float s = Math.max(0.01f, Math.min(1f, structureShare));
        for (int i = 0; i < armor.length; i++) {
            armor[i] = maxArmor[i] * a;
            structure[i] = maxStructure[i] * s;
        }
        rearArmor = maxRearArmor * a;
    }

    /**
     * Lays the wear a mech carries out of an unfinished repair on its sections, before any hit.
     * No section enters battle destroyed: what is left of its structure never reads below
     * {@link #CARRIED_STRUCTURE_FLOOR}, the gantry crew's patch on a section the last battle took off.
     */
    public void carryWear(MechCarriedWear wear) {
        if (wear == null || wear.isEmpty()) return;
        for (MechSection section : MechSection.VALUES) {
            int i = section.ordinal();
            if (!fitted[i]) continue;
            structure[i] = maxStructure[i] * Math.max(CARRIED_STRUCTURE_FLOOR, 1f - wear.structureLost(section));
            armor[i] = maxArmor[i] * (1f - wear.armorLost(section));
        }
        rearArmor = maxRearArmor * (1f - wear.armorLost(MechSection.TORSO));
    }

    /** The least of a section's structure a carried mech drops with. */
    public static final float CARRIED_STRUCTURE_FLOOR = 0.1f;

    /** The torso's rear plate: what a hit from behind meets. */
    public float rearArmor() {
        return rearArmor;
    }

    public float maxRearArmor() {
        return maxRearArmor;
    }

    public float structure(MechSection section) {
        return structure[section.ordinal()];
    }

    public float maxStructure(MechSection section) {
        return maxStructure[section.ordinal()];
    }

    /** Whether this section's structure has gone to zero in this battle. */
    public boolean destroyed(MechSection section) {
        return destroyed[section.ordinal()];
    }

    /** Legs at zero: the chassis walks on at a crawl ({@code Crippled}). */
    public boolean crippled() {
        return destroyed[MechSection.LEGS.ordinal()];
    }

    /** Head at zero: vision and accuracy are cut. */
    public boolean sensorsLost() {
        return destroyed[MechSection.HEAD.ordinal()];
    }

    /** Torso at zero: the mech is destroyed. */
    public boolean torsoDestroyed() {
        return destroyed[TORSO];
    }

    /** Structure left across every section; what the chassis health bar reads while it stands. */
    public float totalStructure() {
        float sum = 0f;
        for (float s : structure) sum += s;
        return sum;
    }

    /** Armor at full across every section and the rear plate: the chassis ARMOR column's maximum. */
    public float totalMaxArmor() {
        float sum = maxRearArmor;
        for (float a : maxArmor) sum += a;
        return sum;
    }

    /** Armor left across every section. */
    public float totalArmor() {
        float sum = rearArmor;
        for (float a : armor) sum += a;
        return sum;
    }

    /** The section a round in {@code band} lands on; see {@link MechBodyPlan#section}. */
    public MechSection sectionFor(BodyPart band,
                                  boolean fromLeft, float armRoll) {
        return plan.section(band, fromLeft, armRoll, fitted);
    }

    /** {@code -Dbattle.mech.armorArcs=false} is the control: the torso's plate is the same on every arc. */
    public static final String ARMOR_ARCS_PROPERTY = "battle.mech.armorArcs";

    private static final boolean ARMOR_ARCS =
            Boolean.parseBoolean(System.getProperty(ARMOR_ARCS_PROPERTY, "true"));

    /**
     * The torso's arcs, every chassis alike, read against the upper torso's facing: a front as the chassis rating
     * stands, slightly thinner flanks, and a thin back where the reactor and its cooling sit. Only the torso has
     * arcs; legs, arms and head meet the chassis rating from every side.
     */
    public static final ArmorArcs.Profile TORSO_ARCS = new ArmorArcs.Profile(1f, 1f, .95f, .85f, .75f, .6f);

    /** The torso's arcs, or {@link ArmorArcs.Profile#FLAT} under the control. */
    public static ArmorArcs.Profile torsoArcs() {
        return ARMOR_ARCS ? TORSO_ARCS : ArmorArcs.Profile.FLAT;
    }

    /**
     * Whether a round from {@code (dx, dy)} away, relative to the mech, arrives on its left, read
     * against the upper torso's facing: the frame the shoulder mounts are drawn in
     * ({@link MechHardpointGeometry}, where local -x is left and +y forward).
     */
    public static boolean fromLeft(float torsoFacingDegrees, float dx, float dy) {
        double radians = Math.toRadians(torsoFacingDegrees);
        return dx * Math.cos(radians) + dy * Math.sin(radians) < 0.0;
    }

    /** Records the weapon a destroyed mount lost, for the campaign to strike from stores. */
    public void recordLostMount(MechMountSlot slot, MechWeaponComponent component) {
        lostMounts[slot.ordinal()] = component;
    }

    /** The weapon this battle destroyed on {@code slot}, or null. */
    public MechWeaponComponent lostMount(MechMountSlot slot) {
        return lostMounts[slot.ordinal()];
    }

    /**
     * Resolves one hit on {@code section}: the section's own armor, then its structure, then what
     * it cannot hold into the torso's armor and structure. Allocation-free; {@code scratch} and {@code out}
     * are the caller's.
     */
    public void resolve(MechSection section, float damage, float penetration, float mitigationSoak,
                        float armorRating, DurabilityModel.Resolution scratch, Hit out) {
        resolve(section, damage, penetration, mitigationSoak, armorRating, armorRating, 1f, scratch, out);
    }

    /**
     * {@link #resolve(MechSection, float, float, float, float, DurabilityModel.Resolution, Hit)} with the torso's
     * plate on the arc the hit arrives on ({@link #TORSO_ARCS}): {@code torsoRating} and {@code torsoPlate} are what
     * the torso's armor is worth on that arc, whether the hit lands on the torso or spills into it. Every other
     * section meets {@code armorRating} on every arc.
     */
    public void resolve(MechSection section, float damage, float penetration, float mitigationSoak,
                        float armorRating, float torsoRating, float torsoPlate,
                        DurabilityModel.Resolution scratch, Hit out) {
        resolve(section, damage, penetration, mitigationSoak, armorRating, torsoRating, torsoPlate, false,
                scratch, out);
    }

    /**
     * As above, with {@code fromBehind} choosing the torso's plate: a hit the torso's arcs read as
     * rear meets the rear plate ({@link #rearArmor()}), every other the front. The torso's structure
     * is one pool behind both.
     */
    public void resolve(MechSection section, float damage, float penetration, float mitigationSoak,
                        float armorRating, float torsoRating, float torsoPlate, boolean fromBehind,
                        DurabilityModel.Resolution scratch, Hit out) {
        int i = section.ordinal();
        float torsoBefore = structure[TORSO];
        boolean rear = fromBehind;
        if (i == TORSO) {
            float plateBefore = rear ? rearArmor : armor[TORSO];
            DurabilityModel.resolvePlatedInto(damage, penetration, mitigationSoak,
                    plateBefore, torsoRating, torsoPlate, torsoBefore, scratch);
            setTorsoArmor(rear, Math.max(0f, plateBefore - scratch.armorDamage()));
            structure[TORSO] = Math.max(0f, torsoBefore - scratch.structureDamage());
            boolean torsoNowDestroyed = torsoBefore > 0f && structure[TORSO] <= 0f;
            if (torsoNowDestroyed) destroyed[TORSO] = true;
            out.set(section, scratch.armorDamage(), 0f, scratch.structureDamage(), scratch.mitigatedDamage(),
                    false, torsoNowDestroyed);
            return;
        }
        // The struck section first, against its own armor and structure; whatever of the payload
        // it cannot hold goes on into the torso, which meets it with its own armor before its
        // structure. A screen is spent once, on the section.
        float sectionArmorBefore = armor[i];
        float sectionBefore = structure[i];
        float mitigated = Math.min(damage, mitigationSoak);
        float afterSoak = damage - mitigated;
        float sectionArmorDamage = 0f;
        float onSection = 0f;
        float overflow;
        if (sectionBefore <= 0f) {
            overflow = afterSoak;   // a hole: straight through to the torso
        } else {
            DurabilityModel.resolveInto(damage, penetration, mitigationSoak,
                    sectionArmorBefore, armorRating, sectionBefore, scratch);
            sectionArmorDamage = scratch.armorDamage();
            onSection = scratch.structureDamage();
            overflow = 0f;
            if (onSection >= sectionBefore) {
                float toBreak = sectionArmorBefore > 0f
                        ? sectionArmorBefore / DurabilityModel.armorEfficiency(penetration, armorRating) : 0f;
                overflow = Math.max(0f, afterSoak - toBreak - sectionBefore);
            }
        }
        armor[i] = Math.max(0f, sectionArmorBefore - sectionArmorDamage);
        structure[i] = sectionBefore - onSection;
        float torsoArmorDamage = 0f;
        float spill = 0f;
        if (overflow > 0f && torsoBefore > 0f) {
            float plateBefore = rear ? rearArmor : armor[TORSO];
            DurabilityModel.resolvePlatedInto(overflow, penetration, 0f, plateBefore, torsoRating, torsoPlate,
                    torsoBefore, scratch);
            torsoArmorDamage = scratch.armorDamage();
            spill = scratch.structureDamage();
            setTorsoArmor(rear, Math.max(0f, plateBefore - torsoArmorDamage));
            structure[TORSO] = Math.max(0f, torsoBefore - spill);
        }
        boolean sectionNowDestroyed = sectionBefore > 0f && structure[i] <= 0f;
        if (sectionNowDestroyed) destroyed[i] = true;
        boolean torsoNowDestroyed = torsoBefore > 0f && structure[TORSO] <= 0f;
        if (torsoNowDestroyed) destroyed[TORSO] = true;
        out.set(section, sectionArmorDamage + torsoArmorDamage, onSection, spill, mitigated,
                sectionNowDestroyed, torsoNowDestroyed);
    }

    private void setTorsoArmor(boolean rear, float value) {
        if (rear) rearArmor = value;
        else armor[TORSO] = value;
    }

    /** A frozen copy of the sections, for campaign writeback after the battle. */
    public Report report() {
        int n = MechSection.VALUES.length;
        float[] structureFraction = new float[n];
        float[] armorFraction = new float[n];
        for (int i = 0; i < n; i++) {
            structureFraction[i] = maxStructure[i] > 0f ? structure[i] / maxStructure[i] : 0f;
            armorFraction[i] = maxArmor[i] > 0f ? armor[i] / maxArmor[i] : 0f;
        }
        // The campaign keeps one torso plate: the front and rear together.
        float torsoMax = maxArmor[TORSO] + maxRearArmor;
        armorFraction[TORSO] = torsoMax > 0f ? (armor[TORSO] + rearArmor) / torsoMax : 0f;
        return new Report(plan.id(), fitted.clone(), structureFraction, armorFraction,
                destroyed.clone(), lostMounts.clone());
    }

    /**
     * What a battle left of a mech's sections. Fractions are of each section's own pool; arrays
     * are indexed by {@link MechSection} ordinal, and {@code lostMounts} by {@link MechMountSlot}.
     */
    public record Report(String plan, boolean[] fitted, float[] structureFraction, float[] armorFraction,
                         boolean[] destroyed, MechWeaponComponent[] lostMounts) {

        public boolean chassisLost() {
            return destroyed[TORSO];
        }
    }

    /** Mutable result of one {@link #resolve}; reused by the damage path. */
    public static final class Hit {
        private MechSection section;
        private float armorDamage;
        private float sectionStructureDamage;
        private float spill;
        private float mitigatedDamage;
        private boolean sectionDestroyed;
        private boolean torsoDestroyed;

        void set(MechSection section, float armorDamage, float sectionStructureDamage, float spill,
                 float mitigatedDamage, boolean sectionDestroyed, boolean torsoDestroyed) {
            this.section = section;
            this.armorDamage = armorDamage;
            this.sectionStructureDamage = sectionStructureDamage;
            this.spill = spill;
            this.mitigatedDamage = mitigatedDamage;
            this.sectionDestroyed = sectionDestroyed;
            this.torsoDestroyed = torsoDestroyed;
        }

        public MechSection section() { return section; }

        public float armorDamage() { return armorDamage; }

        /** Structure the struck section itself lost; zero for a torso hit. */
        public float sectionStructureDamage() { return sectionStructureDamage; }

        /** Structure the torso lost: the whole of a torso hit, or what another section spilled. */
        public float spill() { return spill; }

        /** All structure this hit removed, section and torso together. */
        public float structureDamage() { return sectionStructureDamage + spill; }

        public float mitigatedDamage() { return mitigatedDamage; }

        /** Whether this hit took the struck (non-torso) section to zero. */
        public boolean sectionDestroyed() { return sectionDestroyed; }

        /** Whether this hit took the torso to zero: the mech is destroyed. */
        public boolean torsoDestroyed() { return torsoDestroyed; }
    }
}
