package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.fx.ImpactProfile;

import java.awt.Color;

/**
 * Chassis-mounted heavy weapons carried by mech-class units (currently just
 * {@link UnitType#HEAVY_MECH}). Distinct from {@link MarineWeapon} +
 * {@link MarineSecondary} because the lore concept is different — these are
 * armored-vehicle hardpoints, not handheld squad gear — and the firing model
 * differs too: a mech runs all three weapon tracks concurrently, each on its
 * own cooldown and engagement band, rather than the marine's primary-or-
 * secondary dispatch.
 *
 * <p>The catalog is intentionally small and opinionated:
 * <ul>
 *   <li>{@link #CHAINGUN} — close-band brawler. Heavy 8-round burst at high
 *       cycle rate. Reads as the mech walking forward "brrt"-ing.</li>
 *   <li>{@link #SRM_POD} — mid-close burst-damage salvo. Wave of 4 dumb
 *       rockets per launch, ammo-limited, big anti-anything punch.</li>
 *   <li>{@link #LRM_ARTILLERY} — long-band artillery. Slow arc, single
 *       heavy rocket, used to lob shots across the grid before targets close
 *       to chaingun range.</li>
 * </ul>
 *
 * <p>The "swappable chassis weapons" design pivot the user described — a
 * future {@code MechLoadout} picker — falls out of this naturally: the unit
 * holds its three slots in {@link MechLoadoutState}, so swapping in
 * different {@code MechWeapon} entries (e.g. AC/20-style heavy autocannon
 * over chainguns) is just a different state-bag at spawn time.
 */
public enum MechWeapon {

    /**
     * Dual chaingun arms — close-band saturation. Each trigger pull rips a
     * 10-round burst at 60ms spacing for a ~0.6s sustained brrt across both
     * arms, then a 2-second cooldown. Rounds scatter across a 1.2-cell
     * pattern and each detonates with a small AoE on landing, so a clustered
     * squad eats multiple rounds via splash — anti-cluster suppression, not
     * single-target precision. Ground-mounted, so scattered rounds raycast
     * against walls and splatter rather than peppering marines behind cover.
     * The chip wallDamage means a sustained chaingun lock-on grinds through
     * building walls over a few bursts. KINETIC impact for the punchier
     * visual flash that fits a heavy auto-cannon.
     *
     * <p>Tuning intent — the chaingun is the "ammo never runs out" hitter
     * the mech leans on once SRM/LRM salvos are spent. Lower per-round
     * damage (1.0 vs the rocket pods) keeps it from overshadowing the
     * signature heavy-AoE weapons, but the burst count + AoE pattern adds
     * up to a real threat when a fireteam clusters in cover.
     */
    CHAINGUN("Chaingun",
             "chaingun_fire",
             new Color(0xFF, 0xE8, 0xC0),
             22f, 1.0f, 0.55f, 2.00f, 0.4f,
             ImpactProfile.KINETIC,
             10, 0.06f,
             "graphics/missiles/shell_small_yellow.png", 0.18f, 0.10f,
             0f, 1.2f, false,
             0.6f, 3, /*raycastShots*/ true),

    /**
     * Shoulder SRM pod — wave of 4 dumb rockets per launch. Annihilator-pattern
     * for the audio + projectile. Salvos are intentionally infrequent (5.5s
     * cooldown) so a single mech doesn't permanently deny mid-close approach.
     * HE impact still shreds clustered infantry per salvo.
     */
    SRM_POD("SRM Pod",
            "annihilator_fire",
            new Color(0xFF, 0xC0, 0x80),
            18f, 5.5f, 0.55f, 5.50f, 2.0f,
            ImpactProfile.HE,
            4, 0.10f,
            "graphics/missiles/missile_SRM.png", 0.40f, 0.55f,
            0f, 0f, true,
            1.3f, 25),

    /**
     * Long-range indirect-fire artillery. Per trigger pull, lobs a wave of 5
     * Pilum-pattern LRMs (110ms apart). Rockets arc visibly over buildings and
     * scatter on a 1.5-cell radius around the locked target — the artillery
     * "rain" read. Per-rocket damage stepped down so a salvo is potent but
     * doesn't one-shot a fireteam. Ammo-capped at 3 salvos so doctrine is
     * "one opening barrage, two for emergencies."
     *
     * <p>Unlike chainguns + SRMs, LRMs can fire WITHOUT direct line of sight
     * at a {@link #LRM_NO_LOS_ACC_MULT}× accuracy penalty (the AI gates this
     * in {@code MechCombatantBehavior.tryFireMechWeapons}, not the weapon itself).
     */
    LRM_ARTILLERY("LRM Artillery",
                  "pilum_lrm_fire",
                  new Color(0xC8, 0xD8, 0xFF),
                  40f, 9f, 0.55f, 9.00f, 2.5f,
                  ImpactProfile.HE,
                  5, 0.11f,
                  "graphics/missiles/missile_LRM.png", 0.65f, 1.40f,
                  5.0f, 1.5f, true,
                  2.0f, 60);

    /**
     * Accuracy multiplier applied to LRM shots fired without direct line of
     * sight to the target. Reads as "ranged-in indirect fire": the salvo
     * still lands in the target area but each individual rocket is less
     * likely to connect — the mech is guessing from data link / sensor
     * feed rather than seeing the target with its own optics.
     */
    public static final float LRM_NO_LOS_ACC_MULT = 0.55f;

    public final String displayName;
    /** Vanilla fire sound id ({@code fireSoundTwo} from a vanilla .wpn); mono, pre-registered. */
    public final String fireSoundId;
    /** Tracer color — only used as a renderer fallback when {@link #projectileSpritePath} fails to load. Every entry has a real projectile sprite in practice. */
    public final Color tracerColor;
    public final float range;
    /** Damage PER PROJECTILE — for salvo / burst weapons this is per-round, and the salvo's full impact is {@code damage × burstCount}. */
    public final float damage;
    public final float accuracy;
    /** Sim-seconds between trigger pulls. For CHAINGUN this is between bursts; for SRM between salvos; for LRM between shots. */
    public final float cooldown;
    /** Damage multiplier vs {@link MapTurret} targets — chainguns plink (0.4×), missiles wreck (2-2.5×). */
    public final float vsTurretMult;
    public final ImpactProfile impactProfile;
    /** Projectiles per trigger pull. CHAINGUN burst (8), SRM salvo (4), LRM single (1). */
    public final int burstCount;
    /** Sim-seconds between rounds within a burst / salvo. Zero for single-shot LRM. */
    public final float burstSpacing;
    /** Projectile sprite path — vanilla {@code graphics/missiles/...} for free art. */
    public final String projectileSpritePath;
    /** Projectile visual size in cells (long axis). Aspect from the loaded PNG. */
    public final float projectileVisualCells;
    /** Sim-seconds the projectile is visible in flight. Sets the per-shot lifetime used to compute travel progress in the renderer. */
    public final float flightSec;
    /**
     * Visual arc height in cells. When &gt; 0, the renderer draws the projectile
     * following a parabolic path that peaks {@code arcHeight} cells above the
     * straight-line lerp at mid-flight. Purely visual — the sim's hit/miss
     * resolution is unchanged. LRMs use this to read as artillery raining over
     * buildings; chaingun + SRM keep the linear trajectory (arcHeight = 0).
     */
    public final float arcHeight;
    /**
     * Endpoint scatter on a hit, in cells. When &gt; 0, the visual impact point
     * is randomly offset from the target cell by up to this radius — applied
     * AFTER the hit/miss roll, so a hit still does full damage to the locked
     * target; the spread is the "burst pattern" read of an indirect-fire
     * salvo. LRM uses this; precision weapons leave it at 0.
     */
    public final float hitSpread;
    /** True when projectiles in flight should leave a glowing engine trail (any rocket-class weapon). Chaingun shells are kinetic and skip it. */
    public final boolean engineTrail;
    /**
     * Splash radius in cells on detonation, 0 for non-AoE weapons (chaingun).
     * When &gt; 0, the weapon resolves damage at the projectile's impact
     * endpoint via a {@code PendingDetonation}: every unit within radius with
     * line of sight to the endpoint takes {@link #damage} (modified by cover).
     * Friendly fire ON.
     */
    public final float aoeRadius;
    /** Wall HP knocked off the endpoint cell on detonation. 0 for kinetic / non-AoE weapons. */
    public final int wallDamage;
    /**
     * When {@code true}, each scattered round raycasts from origin to endpoint
     * through the nav grid; if a wall sits in the path, the endpoint snaps to
     * that wall cell (the round "hits" the wall instead of passing through).
     * Used by ground-deployed area-spread weapons so wide scatter can't pepper
     * units behind cover — mirrors the turret-side
     * {@link com.dillon.starsectormarines.battle.TurretKind#raycastShots}
     * convention. Rocket-class mech weapons (SRM_POD, LRM_ARTILLERY) leave
     * this off — rockets travel in their own arc and don't ground-snap.
     */
    public final boolean raycastShots;

    /** Legacy constructor — defaults {@link #raycastShots} to false. Used by every entry except CHAINGUN. */
    MechWeapon(String displayName, String fireSoundId, Color tracerColor,
               float range, float damage, float accuracy, float cooldown, float vsTurretMult,
               ImpactProfile impactProfile,
               int burstCount, float burstSpacing,
               String projectileSpritePath, float projectileVisualCells, float flightSec,
               float arcHeight, float hitSpread, boolean engineTrail,
               float aoeRadius, int wallDamage) {
        this(displayName, fireSoundId, tracerColor,
                range, damage, accuracy, cooldown, vsTurretMult,
                impactProfile,
                burstCount, burstSpacing,
                projectileSpritePath, projectileVisualCells, flightSec,
                arcHeight, hitSpread, engineTrail,
                aoeRadius, wallDamage, /*raycastShots*/ false);
    }

    MechWeapon(String displayName, String fireSoundId, Color tracerColor,
               float range, float damage, float accuracy, float cooldown, float vsTurretMult,
               ImpactProfile impactProfile,
               int burstCount, float burstSpacing,
               String projectileSpritePath, float projectileVisualCells, float flightSec,
               float arcHeight, float hitSpread, boolean engineTrail,
               float aoeRadius, int wallDamage, boolean raycastShots) {
        this.displayName = displayName;
        this.fireSoundId = fireSoundId;
        this.tracerColor = tracerColor;
        this.range = range;
        this.damage = damage;
        this.accuracy = accuracy;
        this.cooldown = cooldown;
        this.vsTurretMult = vsTurretMult;
        this.impactProfile = impactProfile;
        this.burstCount = burstCount;
        this.burstSpacing = burstSpacing;
        this.projectileSpritePath = projectileSpritePath;
        this.projectileVisualCells = projectileVisualCells;
        this.flightSec = flightSec;
        this.arcHeight = arcHeight;
        this.hitSpread = hitSpread;
        this.engineTrail = engineTrail;
        this.aoeRadius = aoeRadius;
        this.wallDamage = wallDamage;
        this.raycastShots = raycastShots;
    }
}
