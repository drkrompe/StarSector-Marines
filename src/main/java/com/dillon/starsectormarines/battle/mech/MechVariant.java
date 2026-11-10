package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.unit.EntitySpec;

import java.util.ArrayList;
import java.util.List;

/** Stable chassis profiles built from swappable {@link MechWeaponComponent}s. */
public enum MechVariant {

    BULWARK("bulwark", "@gearMechVariantBULWARK", 550f, 710f, 18f, 1.05f, 0.40f, 55f,
            1.60f, 1.50f, 0.60f, 0.80f, 1.8f,
            LayeredMechAppearance.CHASSIS_CLEAN,
            // Two arms, a chaingun on each: no centreline mount.
            null, MechWeaponComponent.CHAINGUN, MechWeaponComponent.CHAINGUN,
            MechWeaponComponent.SRM_15,
            MechWeaponComponent.SRM_15,
            MechRole.ARMORED_SUPPORT),

    HOUND("hound", "@gearMechVariantHOUND", 350f, 375f, 14f, 1.55f, 0.42f, 50f,
            1.35f, 1.20f, 0.50f, 0.67f, 0.8f,
            LayeredMechAppearance.CHASSIS_HOUND,
            MechWeaponComponent.CHAINGUN, null, null,
            MechWeaponComponent.SRM_5,
            null,
            MechRole.ASSAULT),

    WARDEN("warden", "@gearMechVariantWARDEN", 250f, 300f, 10f, 1.32f, 0.45f, 55f,
            1.35f, 1.20f, 0.48f, 0.65f, 1.2f,
            LayeredMechAppearance.CHASSIS_WARDEN,
            MechWeaponComponent.HC_20, null, null,
            // Artillery against men; the heavy cannon is the Warden's answer to armor.
            MechWeaponComponent.LRM_5_FRAG,
            MechWeaponComponent.LRM_5_FRAG,
            MechRole.LR_SUPPORT),

    /**
     * The pirate Hauler (`mech-lines.md`, low-tech variants): a captured Bulwark a pirate crew rebuilt as a loot
     * carrier, a variant on the Bulwark frame (law 2) with every one of its mounts, drawn as a body of its own: a cargo
     * cage on its back, a folding salvage crane along its left shoulder and scrap slabs hung on chains off its flanks.
     * The cage and slabs make it the most durable heavy a pirate fields and the slowest; its left rack place ships
     * empty beside the crane. The loot it carries has no mechanic yet. All numbers UNTUNED.
     */
    HAULER("hauler", "@gearMechVariantHAULER", 600f, 800f, 18f, 0.95f, 0.38f, 55f,
            1.60f, 1.50f, 0.60f, 0.80f, 2.0f,
            LayeredMechAppearance.CHASSIS_CLEAN,
            null, MechWeaponComponent.CHAINGUN, MechWeaponComponent.CHAINGUN,
            null,
            MechWeaponComponent.SRM_15_FRAG,
            MechRole.ARMORED_SUPPORT),

    /**
     * The Luddic Path's Foundry Breaker (`mech-lines.md`, low-tech variants): an industrial demolition walker on the
     * Hound frame (law 2), drawn as a body of its own with a foundry-cast breaching collar round the nose and five
     * shell canisters across its back. Its stock fit is the Path's Hound issue, the DC-5 on the nose and the Hound's
     * rack; the collar costs it a little pace for a little plate. All numbers UNTUNED.
     */
    FOUNDRY_BREAKER("foundry_breaker", "@gearMechVariantFOUNDRY_BREAKER", 370f, 420f, 15f, 1.45f, 0.40f, 50f,
            1.35f, 1.20f, 0.50f, 0.67f, 0.9f,
            LayeredMechAppearance.CHASSIS_HOUND,
            MechWeaponComponent.DC_5, null, null,
            MechWeaponComponent.SRM_5,
            null,
            MechRole.ASSAULT),

    /**
     * The Luddic Path's Pilgrim (`mech-lines.md`, low-tech variants): an armoured mining hauler on the Bulwark frame
     * (law 2), drawn as a body of its own with a dozer blade across its front, ore-hopper ribs down its back and
     * prayer cloths. The heaviest and slowest walker in the row, and the only one that crushes an ordinary wall it
     * walks into, by its weight as a heavy hull does ({@link #crushesWalls()}). All numbers UNTUNED.
     */
    PILGRIM("pilgrim", "@gearMechVariantPILGRIM", 620f, 840f, 20f, 0.85f, 0.38f, 52f,
            1.60f, 1.60f, 0.60f, 0.80f, 2.2f,
            LayeredMechAppearance.CHASSIS_CLEAN,
            null, MechWeaponComponent.CHAINGUN, MechWeaponComponent.CHAINGUN,
            MechWeaponComponent.SRM_5_FRAG,
            MechWeaponComponent.SRM_5_FRAG,
            MechRole.ASSAULT),

    /**
     * The pirate Torch (`mech-lines.md`, low-tech variants): a captured Hound a pirate crew rebuilt as a fuel rig, on the
     * Hound frame (law 2), drawn as a body of its own with mismatched fuel drums and a pressure bottle in a welded
     * cradle across its back. Its fuel load is its body; its stock nose gun is the FL-1 burner, a machine flamer and a
     * weapon layer of its own (law 4; `mechs-nouns.md`, Machine flamers). The cradle takes the dorsal rack's place, so
     * that ships empty. Its cut-away side plate makes it a little lighter-armoured than the Hound. All numbers UNTUNED.
     */
    TORCH("torch", "@gearMechVariantTORCH", 340f, 330f, 12f, 1.50f, 0.40f, 50f,
            1.35f, 1.20f, 0.50f, 0.67f, 0.9f,
            LayeredMechAppearance.CHASSIS_HOUND,
            MechWeaponComponent.FL_1, null, null,
            null,
            null,
            MechRole.ASSAULT),

    /**
     * The Luddic Path's Censer (`mech-lines.md`, low-tech variants): an industrial Warden a Pather cell converted to
     * burn out what it calls corruption, on the Warden frame (law 2), drawn as a body of its own with a promethium
     * tank across its rear, an armoured hose up its right flank and prayer cloths. Its fuel load is its body; its right
     * shoulder carries the FL-2 lance, the larger machine flamer (`mechs-nouns.md`, Machine flamers), its nose a
     * chaingun, and its left rack place ships an empty plate. The tank makes it heavier and slower than the Warden.
     * All numbers UNTUNED.
     */
    CENSER("censer", "@gearMechVariantCENSER", 300f, 340f, 11f, 1.22f, 0.42f, 55f,
            1.35f, 1.20f, 0.48f, 0.65f, 1.4f,
            LayeredMechAppearance.CHASSIS_WARDEN,
            MechWeaponComponent.CHAINGUN, null, null,
            null,
            MechWeaponComponent.FL_2,
            MechRole.ASSAULT),

    /**
     * The derelict security walker (`derelict-lore-guide.md`, Units): an unpiloted Mbaye-Gogol heavy built where it
     * stands to guard a site, outside the company's tech-by-weight grid ({@link #automated()}). A derelict chassis of
     * its own, no company mech's: a low armoured torso slung between reaching legs, its guns in torso ports (two side
     * ports and two racks on its back), drawn by its own leg rig ({@code DerelictLegRig}) and struck by its own body
     * plan ({@link MechBodyPlan#DERELICT}). Its numbers are its own brief's, all UNTUNED: heavier plate and structure
     * than anything the company fields, a deliberate legged pace, a narrow enough stance for a two-cell corridor. Its
     * stock fit is the line frame; the siege and vault frames are loadouts on this one chassis
     * ({@code DerelictWalkerFrame}).
     */
    SECURITY_WALKER("security_walker", "@gearMechVariantSECURITY_WALKER", 900f, 1200f, 22f, 0.75f, 0.45f, 52f,
            2.00f, 1.80f, 0.70f, 0.80f, 2.8f,
            LayeredMechAppearance.CHASSIS_DERELICT,
            null, MechWeaponComponent.HC_10, MechWeaponComponent.AC_6,
            MechWeaponComponent.SRM_5,
            MechWeaponComponent.SRM_5,
            MechRole.ARMORED_SUPPORT),

    /**
     * The derelict line's medium (`derelict-watch.md`, Medium and large walkers): the MG-25 rounds walker, the sentinel
     * built low and quick on its own leg rig at its size, light plate, a chaingun and an SRM-5. Narrow enough for a
     * one-cell door, so it walks its zone's round and answers in side rooms and stores. Automated, like the heavy.
     * All numbers UNTUNED.
     */
    SECURITY_ROUNDS("security_rounds", "@gearMechVariantSECURITY_ROUNDS", 380f, 450f, 14f, 1.45f, 0.45f, 55f,
            1.20f, 1.20f, 0.42f, 0.45f, 1.0f,
            LayeredMechAppearance.CHASSIS_DERELICT,
            null, MechWeaponComponent.CHAINGUN, null,
            MechWeaponComponent.SRM_5,
            null,
            MechRole.ASSAULT),

    /**
     * The derelict line's large (`derelict-watch.md`, Medium and large walkers): the MG-70 hall frame, the sentinel
     * built to twice the heavy's height, the heaviest plate outside the vault, a heavy cannon pair and fragmentation racks. Too
     * wide for a corridor or a two-cell door: it keeps the plant's halls, joins and cargo docks, and forces a dead door
     * in its way. Automated. All numbers UNTUNED.
     */
    SECURITY_HALL("security_hall", "@gearMechVariantSECURITY_HALL", 1800f, 2600f, 28f, 0.60f, 0.45f, 52f,
            3.40f, 2.60f, 1.25f, 1.20f, 5.0f,
            LayeredMechAppearance.CHASSIS_DERELICT,
            null, MechWeaponComponent.HC_10, MechWeaponComponent.HC_10,
            MechWeaponComponent.SRM_15_FRAG,
            MechWeaponComponent.SRM_15_FRAG,
            MechRole.BALANCED),

    /**
     * Tri-Tachyon's light crawler (`mech-lines.md`, high-tech light; owner: "an interesting 'drone' concept"): a
     * small carapace low on four short legs, a chassis of its own (law 1), drawn by the walker's leg rig at the
     * crawler's size ({@code DerelictLegRig.CRAWLER}) with its own parts. High-tech by its numbers: fast, thin plate,
     * a light structure, precise. One gun in a socket at the front, drawn as its own layer (law 4), and no shoulder
     * mounts ({@link #hasMount}); its compartment carries a shield emitter ({@link #shieldEmitter()}). Crewed, a
     * Tri-Tachyon pattern a company can buy (owner, 2026-11-09). All numbers UNTUNED.
     */
    CRAWLER("crawler", "@gearMechVariantCRAWLER", 220f, 160f, 8f, 1.85f, 0.50f, 55f,
            1.00f, 0.90f, 0.45f, 0.40f, 0.5f,
            LayeredMechAppearance.CHASSIS_CRAWLER,
            MechWeaponComponent.PL_1, null, null,
            null,
            null,
            MechRole.ASSAULT),

    /**
     * The derelict line's smallest (`derelict-watch.md`, Field repair): the MG-12 tender, the sentinel's chassis built
     * under half the heavy's size on its own leg rig, unarmed, a welding arm in place of guns. It follows its zone's
     * walkers and welds their plate back in the fight. Light enough for any door. Automated. All numbers UNTUNED.
     */
    SECURITY_TENDER("security_tender", "@gearMechVariantSECURITY_TENDER", 240f, 220f, 10f, 1.30f, 0.45f, 40f,
            0.90f, 0.60f, 0.32f, 0.35f, 0.6f,
            LayeredMechAppearance.CHASSIS_DERELICT,
            null, null, null,
            null,
            null,
            MechRole.ASSAULT);

    public final String id;
    private final String displayName;

    public String displayName() { return Strings.resolve(displayName); }
    public final float maxStructure;
    public final float armorCapacity;
    public final float armorRating;
    public final float moveSpeed;
    public final float accuracy;
    public final float visionRange;
    public final float renderScale;
    public final float moraleImpact;
    public final float radius;
    public final float hitHalfHeight;
    /** Relative inertia for manual drive only; not tonnage or an autonomous speed modifier. */
    public final float relativeMass;
    public final int chassisAppearance;
    /** The stock centreline gun, or null on a chassis that carries its guns on two arms. */
    public final MechWeaponComponent arms;
    /** The stock left-arm gun, or null on a chassis with one centreline mount. */
    public final MechWeaponComponent leftArm;
    public final MechWeaponComponent rightArm;
    public final MechWeaponComponent leftShoulder;
    public final MechWeaponComponent rightShoulder;
    public final MechRole defaultRole;

    MechVariant(String id, String displayName, float maxStructure,
                float armorCapacity, float armorRating, float moveSpeed,
                float accuracy, float visionRange, float renderScale,
                float moraleImpact, float radius, float hitHalfHeight, float relativeMass,
                int chassisAppearance, MechWeaponComponent arms,
                MechWeaponComponent leftArm, MechWeaponComponent rightArm,
                MechWeaponComponent leftShoulder,
                MechWeaponComponent rightShoulder, MechRole defaultRole) {
        this.id = id;
        this.displayName = displayName;
        this.maxStructure = maxStructure;
        this.armorCapacity = armorCapacity;
        this.armorRating = armorRating;
        this.moveSpeed = moveSpeed;
        this.accuracy = accuracy;
        this.visionRange = visionRange;
        this.renderScale = renderScale;
        this.moraleImpact = moraleImpact;
        this.radius = radius;
        this.hitHalfHeight = hitHalfHeight;
        this.relativeMass = relativeMass;
        this.chassisAppearance = chassisAppearance;
        this.arms = arms;
        this.leftArm = leftArm;
        this.rightArm = rightArm;
        this.leftShoulder = leftShoulder;
        this.rightShoulder = rightShoulder;
        this.defaultRole = defaultRole;
    }

    /**
     * The line this chassis is built on: a hardware variant's frame (`mech-lines.md`, law 2), whose mounts, body plan,
     * gait, footprint and weight class it keeps; a line's own chassis is its own frame.
     */
    public MechVariant frame() {
        return switch (this) {
            case HAULER, PILGRIM -> BULWARK;
            case FOUNDRY_BREAKER, TORCH -> HOUND;
            case CENSER -> WARDEN;
            case BULWARK, HOUND, WARDEN, SECURITY_WALKER, SECURITY_ROUNDS, SECURITY_HALL, SECURITY_TENDER, CRAWLER -> this;
        };
    }

    /** Whether this chassis is a hardware variant of another line's frame rather than a line's own chassis. */
    public boolean isVariant() {
        return frame() != this;
    }

    /**
     * Whether this chassis crushes an ordinary wall it walks into by its weight, as a heavy hull does
     * ({@code WallCrush}): the Pilgrim, the dozer-bladed mining hauler, alone among walkers.
     */
    public boolean crushesWalls() {
        return this == PILGRIM;
    }

    /**
     * Whether this chassis can tether a drone wing (a sensor pair's, mechs-nouns.md, one machine, one part catalog):
     * the wing flies off the torso as a hull's does off its deck. Chassis capability data, not a mech/vehicle label.
     */
    public boolean hostsDrones() {
        return switch (this) {
            case BULWARK, HOUND, WARDEN, HAULER, FOUNDRY_BREAKER, PILGRIM, TORCH, CENSER -> true;
            case SECURITY_WALKER, SECURITY_ROUNDS, SECURITY_HALL, SECURITY_TENDER, CRAWLER -> false;
        };
    }

    /**
     * Whether this chassis has the flanks to stand active protection's interceptor launchers and sensor panels on:
     * every chassis carries them on its torso, where the render draws them per chassis.
     */
    public boolean hostsPointDefence() {
        return switch (this) {
            case BULWARK, HOUND, WARDEN, HAULER, FOUNDRY_BREAKER, PILGRIM, TORCH, CENSER -> true;
            case SECURITY_WALKER, SECURITY_ROUNDS, SECURITY_HALL, SECURITY_TENDER, CRAWLER -> false;
        };
    }

    /**
     * Whether this chassis's compartment carries a shield emitter as built (`mech-lines.md`, high-tech): its stock
     * frame ({@link MechFrame#stock}) and every refit frame bring {@link MachineSystems#shieldEmitter}, so it fields
     * the pooled frontal shield ({@link MechShield}) wherever it spawns.
     */
    public boolean shieldEmitter() {
        return this == CRAWLER;
    }

    /**
     * What this chassis's own plant sheds a second beside its capacitor bank and vent arrays, so its stock fit runs at
     * its family's heat (`machine-budgets.md`, flux): the Bulwark's twin guns at about 2.5 times what it sheds, the
     * crawler's gun and shield at about 1.25. A chassis meets its band here because the bank is a part every machine
     * shares. UNTUNED.
     */
    public float plantDissipation() {
        return switch (this) {
            case BULWARK, HAULER, PILGRIM -> 6f;
            case CRAWLER -> 3f;
            case HOUND, WARDEN, FOUNDRY_BREAKER, TORCH, CENSER, SECURITY_WALKER, SECURITY_ROUNDS, SECURITY_HALL, SECURITY_TENDER -> 0f;
        };
    }

    /** What this chassis's own plant adds to its bank's reservoir: high-tech's larger reserve. UNTUNED. */
    public float plantCapacity() {
        return this == CRAWLER ? 50f : 0f;
    }

    /** Whether this chassis has shoulder mounts: the crawler carries its one gun in a front socket and nothing else. */
    public boolean hasShoulders() {
        return this != CRAWLER;
    }

    /**
     * Whether this chassis is an automated line (`mech-lines.md`, outside the grid): no cockpit and no pilot, never
     * fielded, built, bought or refitted by the company, since the Sector's law keeps armed machines under crewed control.
     * Only an opposition spawns it.
     */
    public boolean automated() {
        return this == SECURITY_WALKER || this == SECURITY_ROUNDS || this == SECURITY_HALL || this == SECURITY_TENDER;
    }

    /** Every line the company can field, build, buy or refit: all but the {@link #automated()} ones, in order. */
    public static List<MechVariant> companyLines() {
        List<MechVariant> lines = new ArrayList<>();
        for (MechVariant variant : values()) if (!variant.automated()) lines.add(variant);
        return lines;
    }

    public static MechVariant fromId(String id) {
        for (MechVariant variant : values()) {
            if (variant.id.equals(id)) return variant;
        }
        throw new IllegalArgumentException("Unknown mech variant id '" + id + "'");
    }

    /** Applies the chassis's spawn-time stats and persistent profile identity. */
    public EntitySpec applyTo(EntitySpec spec) {
        spec.mechVariant = this;
        spec.health(maxStructure)
                .armor(armorCapacity, armorRating)
                .moveSpeed(moveSpeed)
                .accuracy(accuracy)
                .attackRange(maxWeaponRange())
                .visionRange(Math.max(visionRange, maxWeaponRange()));
        return spec;
    }

    public MechLoadoutComponent createLoadout(MechRole role) {
        return new MechLoadoutComponent(this, role != null ? role : defaultRole);
    }

    public float maxWeaponRange() {
        float max = 0f;
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent stock = stockWeapon(slot);
            if (stock != null) max = Math.max(max, stock.weaponDef().range);
        }
        return max;
    }

    /** Whether this chassis carries its arm guns on two side mounts rather than on one centreline mount. */
    public boolean twoArmed() {
        return arms == null;
    }

    /** Whether this chassis exposes {@code slot} at all: the centreline mount or the two arms, never both. */
    public boolean hasMount(MechMountSlot slot) {
        if (slot == null) return false;
        if (slot == MechMountSlot.ARMS) return !twoArmed();
        if (slot.isSideArm()) return twoArmed();
        if (slot.isShoulder()) return hasShoulders();
        return true;
    }

    /** The arm mounts this chassis carries, left to right. */
    public MechMountSlot[] armMounts() {
        return twoArmed()
                ? new MechMountSlot[] {MechMountSlot.LEFT_ARM, MechMountSlot.RIGHT_ARM}
                : new MechMountSlot[] {MechMountSlot.ARMS};
    }

    /** The factory fit on {@code slot}, or null when it ships empty or the chassis has no such mount. */
    public MechWeaponComponent stockWeapon(MechMountSlot slot) {
        return switch (slot) {
            case ARMS -> arms;
            case LEFT_ARM -> leftArm;
            case RIGHT_ARM -> rightArm;
            case LEFT_SHOULDER -> leftShoulder;
            case RIGHT_SHOULDER -> rightShoulder;
        };
    }

    /** The factory arm gun: the centreline gun, or the left arm's on a two-armed chassis. */
    public MechWeaponComponent stockArmWeapon() {
        return twoArmed() ? leftArm : arms;
    }

    /** How this chassis is taken apart: its height bands and section pools. */
    public MechBodyPlan bodyPlan() {
        return switch (this) {
            case HOUND, FOUNDRY_BREAKER, TORCH -> MechBodyPlan.HOUND;
            case BULWARK, HAULER, PILGRIM -> MechBodyPlan.BULWARK;
            case WARDEN, CENSER -> MechBodyPlan.WARDEN;
            case SECURITY_WALKER, SECURITY_ROUNDS, SECURITY_HALL, SECURITY_TENDER -> MechBodyPlan.DERELICT;
            case CRAWLER -> MechBodyPlan.CRAWLER;
        };
    }

    /**
     * What this chassis asks of the aircraft that drops it: the weight class
     * seen from the air. Separate from {@link #relativeMass}, which is inertia.
     */
    public LiftClass liftClass() {
        return switch (this) {
            case HOUND, FOUNDRY_BREAKER, TORCH, CRAWLER -> LiftClass.LIGHT;
            case WARDEN, CENSER -> LiftClass.MEDIUM;
            case BULWARK, HAULER, PILGRIM -> LiftClass.HEAVY;
            case SECURITY_WALKER, SECURITY_HALL -> LiftClass.HEAVY;
            case SECURITY_ROUNDS -> LiftClass.MEDIUM;
            case SECURITY_TENDER -> LiftClass.LIGHT;
        };
    }
}
