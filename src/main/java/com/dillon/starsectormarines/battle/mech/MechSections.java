package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;

/**
 * The live armor and structure of each {@link MechSection} of one deployed mech, and what the
 * battle has cost it so far. The chassis's armor capacity and structure are divided across its
 * sections by its {@link MechBodyPlan}, so the totals are what the frame and plating set; the
 * armor rating stays the chassis's own.
 *
 * <p>Each hit is resolved on its section by {@link DurabilityModel}, exactly as a whole body is.
 * Structure a non-torso section cannot hold spills into the torso: a hit on a section already at
 * zero goes through the hole to the torso's structure. The torso at zero is the only death
 * ({@link #torsoDestroyed()}); legs at zero cripple the chassis, a mount at zero loses its weapon,
 * and the head at zero loses the sensors.
 *
 * <p>Written only on the serialized damage path. Read by the presentation (the section
 * paper-doll) and, after the battle, by campaign writeback ({@link #report()}), which turns a
 * lost mount into a lost assembly, other damaged sections into repairs, and a destroyed torso into
 * a lost chassis. See {@code mech-locational-damage.md}.
 */
public final class MechSections {

    /**
     * Whether mechs are hit by section. {@code -Dbattle.mech.locationalDamage=false} is the
     * control: a mech is one whole body again, as before sections existed.
     */
    public static final boolean ENABLED =
            Boolean.parseBoolean(System.getProperty("battle.mech.locationalDamage", "true"));

    /** What a lost head leaves of the chassis's weapon accuracy. */
    public static final float SENSOR_LOSS_ACCURACY = 0.6f;
    /** What a lost head leaves of the chassis's vision range. */
    public static final float SENSOR_LOSS_VISION = 0.5f;

    private static final int TORSO = MechSection.TORSO.ordinal();

    private final MechBodyPlan plan;
    private final boolean[] fitted = new boolean[MechSection.VALUES.length];
    private final float[] armor = new float[MechSection.VALUES.length];
    private final float[] maxArmor = new float[MechSection.VALUES.length];
    private final float[] structure = new float[MechSection.VALUES.length];
    private final float[] maxStructure = new float[MechSection.VALUES.length];
    private final boolean[] destroyed = new boolean[MechSection.VALUES.length];
    private final MechWeaponComponent[] lostMounts = new MechWeaponComponent[MechMountSlot.values().length];

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

    /** Legs at zero: the chassis cannot walk. */
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

    /** Armor left across every section. */
    public float totalArmor() {
        float sum = 0f;
        for (float a : armor) sum += a;
        return sum;
    }

    /** The section a round in {@code band} lands on; see {@link MechBodyPlan#section}. */
    public MechSection sectionFor(BodyPart band,
                                  boolean fromLeft, float armRoll) {
        return plan.section(band, fromLeft, armRoll, fitted);
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
     * Resolves one hit on {@code section}: the section's own armor, then its structure, then any
     * structure it cannot hold into the torso. Allocation-free; {@code scratch} and {@code out}
     * are the caller's.
     */
    public void resolve(MechSection section, float damage, float penetration, float mitigationSoak,
                        float armorRating, DurabilityModel.Resolution scratch, Hit out) {
        int i = section.ordinal();
        float torsoBefore = structure[TORSO];
        float sectionBefore = i == TORSO ? 0f : structure[i];
        // A non-torso section resolves against its own structure with the torso's behind it, so
        // the model's own clamp is what limits the spill: nothing passes a destroyed torso.
        float reservoir = sectionBefore + torsoBefore;
        DurabilityModel.resolveInto(damage, penetration, mitigationSoak,
                armor[i], armorRating, reservoir, scratch);
        float structureDamage = scratch.structureDamage();
        float onSection = i == TORSO ? 0f : Math.min(structureDamage, sectionBefore);
        float spill = structureDamage - onSection;
        armor[i] = Math.max(0f, armor[i] - scratch.armorDamage());
        if (i != TORSO) structure[i] = sectionBefore - onSection;
        structure[TORSO] = Math.max(0f, torsoBefore - spill);
        boolean sectionNowDestroyed = i != TORSO && sectionBefore > 0f && structure[i] <= 0f;
        if (sectionNowDestroyed) destroyed[i] = true;
        boolean torsoNowDestroyed = torsoBefore > 0f && structure[TORSO] <= 0f;
        if (torsoNowDestroyed) destroyed[TORSO] = true;
        out.set(section, scratch.armorDamage(), onSection, spill, scratch.mitigatedDamage(),
                sectionNowDestroyed, torsoNowDestroyed);
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
