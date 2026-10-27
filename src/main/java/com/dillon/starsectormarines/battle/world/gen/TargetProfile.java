package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.command.AssaultStyle;
import com.dillon.starsectormarines.battle.infantry.SquadSupportDeclaration;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The campaign → battle read of the <em>target world</em>, distilled to plain
 * data so the procedural core stays campaign-free. This is the value object the
 * campaign → battle bridge threads inward: extracted from the vanilla
 * {@code MarketAPI} at the launch boundary (see {@code TargetProfileResolver},
 * beside {@code DetachmentResolver}) and read by generator stages via
 * {@link com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys#MARKET_PROFILE}.
 *
 * <p><b>No game API here, by contract.</b> {@code battle.world.gen} must compile
 * and run headless (every generator/taxonomy test drives it without a sector),
 * so the bridge carries primitives + an interned faction id, never a
 * {@code MarketAPI}. See {@code campaign-battle-bridge-nouns.md}.
 *
 * <p>Extracted as one whole snapshot so defense, urban-composition, port, and
 * later consumers opt into stable fields without re-touching the resolver.
 *
 * @param marketSize    vanilla market size (~0–10, population/urbanization
 *                      proxy); {@code 0} when no market backs the battle.
 * @param stability     vanilla market stability (~0–10).
 * @param defenseLevel  weighted planetary-defense rating (~0–7): ground
 *                      defenses / heavy batteries + orbital station tier + high
 *                      command + planetary shield. {@code 0} = undefended /
 *                      baseline. Drives the overwatch line's intensity.
 * @param spaceportTier {@code 0} none, {@code 1} spaceport, {@code 2} megaport.
 * @param factionId     owning faction id, or {@code ""} when unknown. Never null.
 * @param functions     the world's {@link EconomicFunction} mix (presence-only),
 *                      driving economy-reflective district selection. Empty when
 *                      no market backs the battle. Never null; stored as an
 *                      unmodifiable {@link EnumSet} copy.
 * @param surface       what the world's own wild ground is made of. Never null;
 *                      a null argument normalizes to {@link SurfacePalette#ROCK}
 *                      rather than to a living world, because most of the Sector
 *                      is not a garden and silence should not claim otherwise.
 *                      Cultivated ground -- parks, street verges -- does not
 *                      consult this; see {@link SurfacePalette}.
 * @param link          how the settlement joins the rest of its world. Never
 *                      null; a null argument normalizes to
 *                      {@link SettlementLink#ROAD}, because a battle with no
 *                      stated lifeline is more likely an ordinary place than an
 *                      off-grid one.
 * @param groundDefence vanilla's own defender strength for this market
 *                      ({@code MarketCMD.getDefenderStr}), read at resolve time.
 *                      Stability, industries and the company's own stationed
 *                      modifier are <em>already inside it</em> — vanilla scales
 *                      by stability itself, so nothing downstream may scale
 *                      again. {@code 0} when no market backs the battle, and
 *                      never negative.
 * @param stationedStrength the company's own contribution to
 *                      {@link #groundDefence} at this market — the sum of the
 *                      flat ground-defence modifiers this mod applied there.
 *                      Subtracted before the number becomes a headcount, so a
 *                      stationed detachment is never counted twice: once as the
 *                      company's own marines and again as the colony's.
 *                      {@code 0} when nothing is stationed, and never negative.
 * @param groundDefences the world holds a dispersed network of small hardened
 *                      emplacements (vanilla Ground Defenses, or its Heavy
 *                      Batteries upgrade, which keeps the network). Says only
 *                      that the world has them; who holds the world decides
 *                      whose they are.
 * @param heavyBatteries the world holds armoured ship-grade batteries (vanilla
 *                      Heavy Batteries). Distinct from {@link #defenseLevel},
 *                      which blends this with other tiers into one number.
 * @param planetaryShield the world holds a plasma envelope against bombardment
 *                      (vanilla Planetary Shield).
 * @param declaredDefences what the MISSION declared the battle carries. Distinct from the three market
 *                      signals above, which are inputs a mission may read: map generation and battle
 *                      setup place only this, and never derive it from the signals or the map's size.
 *                      {@link GroundDefences#NONE} for a mission that said nothing.
 * @param declaredMechDrops the mech-lance drops the MISSION lets each side's commander call down on its own authority;
 *                      {@link MechDropDeclaration#NONE} for a mission that said nothing.
 * @param declaredAirPool the pools of bombers and fighters the MISSION gives each side, which regenerate and are tasked by
 *                      comms operators; {@link AirPoolDeclaration#NONE} for a mission that said nothing.
 * @param declaredSupportKit the support kit the MISSION issues some of each side's squads: crew-served mortar teams and
 *                      forward observers with a comms radio. {@link SquadSupportDeclaration#NONE} for a mission that said nothing.
 * @param declaredAssault how the mission's attacking Conquest commander takes defended works: {@link AssaultStyle#MUSTER}, the
 *                      standing behaviour, for a mission that said nothing, or {@link AssaultStyle#TRICKLE} as that mission's own choice.
 * @param garrison      the market's own garrison estimate ({@link MarketGarrisonEstimate}): strength, quality, heavy lean
 *                      and readiness. {@link MarketGarrison#NONE} when no market backs the battle or the caller describes a
 *                      world rather than its defenders.
 */
public record TargetProfile(int marketSize, int stability, int defenseLevel,
                            int spaceportTier, String factionId,
                            Set<EconomicFunction> functions,
                            SurfacePalette surface,
                            SettlementLink link,
                            float groundDefence,
                            float stationedStrength,
                            boolean groundDefences,
                            boolean heavyBatteries,
                            boolean planetaryShield,
                            GroundDefences declaredDefences,
                            MechDropDeclaration declaredMechDrops,
                            AirPoolDeclaration declaredAirPool,
                            SquadSupportDeclaration declaredSupportKit,
                            AssaultStyle declaredAssault,
                            MarketGarrison garrison,
                            Basin basin) {

    /**
     * The baseline read used when no campaign market backs the battle (headless
     * tests, legacy/preview generation, story ops with no target planet). Every
     * field reads as "no signal" — including an empty {@link #functions} set, so
     * the selection layer falls back to its pre-bridge theme rolls.
     *
     * <p>{@link #surface} is the one field that cannot read as "no signal",
     * because ground has to be made of something. It is
     * {@link SurfacePalette#ROCK} here, so a battle with no world behind it
     * comes out bare rather than green — which does change wild ground on maps
     * that previously defaulted to grassland, deliberately.
     */
    public static final TargetProfile NEUTRAL =
            new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class),
                    SurfacePalette.ROCK, SettlementLink.ROAD, 0f, 0f,
                    false, false, false, GroundDefences.NONE);

    public TargetProfile {
        if (factionId == null) factionId = "";
        if (surface == null) surface = SurfacePalette.ROCK;
        if (link == null) link = SettlementLink.ROAD;
        if (declaredDefences == null) declaredDefences = GroundDefences.NONE;
        if (declaredMechDrops == null) declaredMechDrops = MechDropDeclaration.NONE;
        if (declaredAirPool == null) declaredAirPool = AirPoolDeclaration.NONE;
        if (declaredSupportKit == null) declaredSupportKit = SquadSupportDeclaration.NONE;
        if (declaredAssault == null) declaredAssault = AssaultStyle.MUSTER;
        if (garrison == null) garrison = MarketGarrison.NONE;
        if (basin == null) basin = Basin.NONE;
        functions = (functions == null || functions.isEmpty())
                ? Collections.unmodifiableSet(EnumSet.noneOf(EconomicFunction.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(functions));
        groundDefence = nonNegative(groundDefence);
        stationedStrength = nonNegative(stationedStrength);
    }

    /** The profile with no basin: every caller that predates basins. */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength,
                         boolean groundDefences,
                         boolean heavyBatteries,
                         boolean planetaryShield,
                         GroundDefences declaredDefences,
                         MechDropDeclaration declaredMechDrops,
                         AirPoolDeclaration declaredAirPool,
                         SquadSupportDeclaration declaredSupportKit,
                         AssaultStyle declaredAssault,
                         MarketGarrison garrison) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences,
                declaredMechDrops, declaredAirPool, declaredSupportKit, declaredAssault, garrison, Basin.NONE);
    }

    /** The profile with every declaration and no garrison estimate: every caller that predates the estimate. */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength,
                         boolean groundDefences,
                         boolean heavyBatteries,
                         boolean planetaryShield,
                         GroundDefences declaredDefences,
                         MechDropDeclaration declaredMechDrops,
                         AirPoolDeclaration declaredAirPool,
                         SquadSupportDeclaration declaredSupportKit,
                         AssaultStyle declaredAssault) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences,
                declaredMechDrops, declaredAirPool, declaredSupportKit, declaredAssault, MarketGarrison.NONE);
    }

    /**
     * The profile with the three market signals and no mission declaration: what a market read produces before a
     * mission has said what its battle carries.
     */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength,
                         boolean groundDefences,
                         boolean heavyBatteries,
                         boolean planetaryShield) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, GroundDefences.NONE);
    }

    /** The profile with a defence declaration and no mech-drop declaration: every caller that predates drops. */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength,
                         boolean groundDefences,
                         boolean heavyBatteries,
                         boolean planetaryShield,
                         GroundDefences declaredDefences) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences,
                MechDropDeclaration.NONE, AirPoolDeclaration.NONE);
    }

    /** The profile with a mech-drop declaration and no air pool: every caller that predates pools. */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength,
                         boolean groundDefences,
                         boolean heavyBatteries,
                         boolean planetaryShield,
                         GroundDefences declaredDefences,
                         MechDropDeclaration declaredMechDrops) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences,
                declaredMechDrops, AirPoolDeclaration.NONE);
    }

    /** The profile with a mech-drop declaration and an air pool but no squad support kit: every caller that predates kits. */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength,
                         boolean groundDefences,
                         boolean heavyBatteries,
                         boolean planetaryShield,
                         GroundDefences declaredDefences,
                         MechDropDeclaration declaredMechDrops,
                         AirPoolDeclaration declaredAirPool) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences,
                declaredMechDrops, declaredAirPool, SquadSupportDeclaration.NONE, AssaultStyle.MUSTER);
    }

    /** The profile with every declaration but the assault style, which is the standing {@link AssaultStyle#MUSTER}. */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength,
                         boolean groundDefences,
                         boolean heavyBatteries,
                         boolean planetaryShield,
                         GroundDefences declaredDefences,
                         MechDropDeclaration declaredMechDrops,
                         AirPoolDeclaration declaredAirPool,
                         SquadSupportDeclaration declaredSupportKit) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences,
                declaredMechDrops, declaredAirPool, declaredSupportKit, AssaultStyle.MUSTER);
    }

    /**
     * The profile as it was before the market's ground-defence industries were
     * part of it: every signal except the three defence flags, which read as
     * absent. Kept so the many callers that describe a world rather than its
     * fortifications need not state them.
     */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link,
                         float groundDefence,
                         float stationedStrength) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, groundDefence, stationedStrength,
                false, false, false);
    }

    /**
     * The profile as it was before the market's own ground strength was part of
     * it: every campaign signal the generator reads, and no defence numbers.
     *
     * <p>Kept because the generator's own callers — every headless fixture, every
     * taxonomy test, every scene — describe a world rather than a garrison, and
     * making them all state a defence strength they have no opinion about would
     * be noise. A profile built this way fields the garrison floor, which is what
     * a battle with no stated defence strength should field.
     */
    public TargetProfile(int marketSize, int stability, int defenseLevel,
                         int spaceportTier, String factionId,
                         Set<EconomicFunction> functions,
                         SurfacePalette surface,
                         SettlementLink link) {
        this(marketSize, stability, defenseLevel, spaceportTier, factionId,
                functions, surface, link, 0f, 0f);
    }

    /** Defence strengths are magnitudes; a negative or absent one reads as none. */
    private static float nonNegative(float value) {
        return Float.isNaN(value) || value < 0f ? 0f : value;
    }

    /**
     * This profile with a different owner, every other field left exactly as the
     * market gave it. The whole point of the wither is that {@link #factionId} is
     * an axis of its own: no generator stage reads it — it selects the defending
     * ground roster and nothing else — so swapping it changes who is standing on
     * the battlefield without disturbing the battlefield. That is what makes a
     * faction swap a controlled comparison rather than a reroll.
     *
     * @param overrideFactionId the replacement owner; {@code null} means "no
     *     override" and returns {@code this} unchanged, so a caller can apply an
     *     optional override without branching.
     */
    public TargetProfile withFactionId(String overrideFactionId) {
        if (overrideFactionId == null || overrideFactionId.equals(factionId)) return this;
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                overrideFactionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declaredMechDrops, declaredAirPool, declaredSupportKit, declaredAssault, garrison, basin);
    }

    /**
     * This profile with the three defence signals restated and every other field
     * left as it was. The seam a debug mission uses to field a fortress on a
     * world whose market has none (or strip one that does), the way
     * {@link #withFactionId} swaps the owner without disturbing the map.
     */
    public TargetProfile withDefences(boolean groundDefences, boolean heavyBatteries,
                                      boolean planetaryShield) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declaredMechDrops, declaredAirPool, declaredSupportKit, declaredAssault, garrison, basin);
    }

    /**
     * This profile with the mission's declaration of its defences, every market read left as it was. Called once, at
     * the mission boundary (MissionDefencePolicy), and read by the stages that place what it names.
     */
    public TargetProfile withDeclaredDefences(GroundDefences declared) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declared, declaredMechDrops, declaredAirPool, declaredSupportKit, declaredAssault, garrison, basin);
    }

    /**
     * This profile with the mission's declaration of the mech lances each side's commander may call, every other
     * read left as it was. Called once, at the mission boundary (MissionDefencePolicy).
     */
    public TargetProfile withDeclaredMechDrops(MechDropDeclaration declared) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declared, declaredAirPool, declaredSupportKit, declaredAssault, garrison, basin);
    }

    /**
     * This profile with the mission's declaration of the air pool each side holds, every other read left as it was.
     * Called once, at the mission boundary (MissionDefencePolicy).
     */
    public TargetProfile withDeclaredAirPool(AirPoolDeclaration declared) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declaredMechDrops, declared, declaredSupportKit, declaredAssault, garrison, basin);
    }

    /**
     * This profile with the mission's declaration of the support kit its squads carry (mortar teams, forward observers),
     * every other read left as it was. Called once, at the mission boundary (MissionDefencePolicy).
     */
    public TargetProfile withDeclaredSupportKit(SquadSupportDeclaration declared) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declaredMechDrops, declaredAirPool, declared, declaredAssault, garrison, basin);
    }

    /**
     * This profile with the mission's choice of how its attacking commander takes defended works, every other read left
     * as it was. Called once, at the mission boundary; a mission that never calls it musters.
     */
    public TargetProfile withDeclaredAssault(AssaultStyle declared) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declaredMechDrops, declaredAirPool, declaredSupportKit, declared, garrison);
    }

    /**
     * This profile with the market's garrison estimate, every other read left as it was. Called once, at the launch
     * boundary, by the resolver that read the market.
     */
    public TargetProfile withGarrison(MarketGarrison estimate) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declaredMechDrops, declaredAirPool, declaredSupportKit, declaredAssault, estimate, basin);
    }

    /** This profile with a basin of other ground in the map's middle, every other read left as it was. */
    public TargetProfile withBasin(Basin declared) {
        return new TargetProfile(marketSize, stability, defenseLevel, spaceportTier,
                factionId, functions, surface, link,
                groundDefence, stationedStrength,
                groundDefences, heavyBatteries, planetaryShield, declaredDefences, declaredMechDrops, declaredAirPool, declaredSupportKit, declaredAssault, garrison, declared);
    }
}
