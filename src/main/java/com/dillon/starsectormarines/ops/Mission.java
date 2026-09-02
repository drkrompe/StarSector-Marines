package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.world.gen.precinct.LanePath;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A contract offered to the player. {@link #normalizedX}/{@link #normalizedY}
 * are 0..1 within the tactical map area — the tactical panel converts them to
 * absolute screen coords at render time.
 *
 * <p>{@link #targetPlanetName} + {@link #targetIndustryId} encode the mission's
 * physical target on the planet; on a successful Sabotage/Raid/Assault, the
 * resolver flags the industry as {@code disrupted}, which feeds back into the
 * next visit's mission generator.
 *
 * <p>{@link #clientFighterSupport} / {@link #enemyFighterSupport} are the air
 * support each side brings into the battle. Both default to {@link FlybyRoster#EMPTY}
 * when the generator decides nobody can spare anything.
 */
public final class Mission {

    public final String        id;
    public final String        name;
    public final MissionType   type;
    public final MissionSource source;
    public final int           payout;
    public final RiskLevel     risk;
    /**
     * Where on the campaign arc this work sits — owns scale (map size, lift,
     * defender count) and whether the type may be offered at all. Never null;
     * defaults from {@link #risk} for missions authored before the split.
     * See `mission-tier-nouns.md`.
     */
    public final OperationTier tier;
    public final String        requirements;
    public final String        flavor;
    public final float         normalizedX;
    public final float         normalizedY;
    public final FlybyRoster   clientFighterSupport;
    public final FlybyRoster   enemyFighterSupport;
    /**
     * Command-power ids the employer/contract offers for this mission — the
     * co-source alongside the player's committed fleet (see
     * {@code ops.detachment.PowerCatalog}). Empty for missions whose employer
     * offers no powers; populated by {@code MissionGenerator} (Slice 2).
     */
    public final List<String>  employerPowerIds;
    /**
     * Total landing sorties the mission needs delivered. With cycling, one
     * physical transport can cover multiple drops by flying repeat sorties —
     * the arrival policy decides whether one sortie holds a full load or a
     * six-seat Conquest half-squad. This is not a raw marine count or a number
     * of physical transports.
     * Mission is gated when {@link #employerShuttles} covers all the drops
     * AND the player has zero transports to contribute (i.e., {@code
     * employerShuttles >= requiredDrops || playerTransports >= 1}).
     */
    public final int requiredDrops;
    /**
     * Drops the employer covers. Distributed across a small number of
     * physical Aeroshuttles cycling to deliver these drops (see
     * {@code DetachmentResolver.EMPLOYER_PHYSICAL_CAP}) — every visible
     * employer ship returns to base between sorties just like the player's.
     * The player's transports cycle to cover the remaining
     * {@code max(0, requiredDrops - employerShuttles)} drops.
     */
    public final int employerShuttles;
    /** How this mission turns committed transports into physical ground arrivals. */
    public final MarineArrivalPolicy marineArrivalPolicy;
    /** Concurrent player-squad exposure; independent from total committed force. */
    public final FieldPresencePolicy fieldPresencePolicy;
    /** Mission-authored Conquest beachhead count, reusable fleet shape, and timing variance. */
    public final ConquestArrivalConfig conquestArrivalConfig;
    /** Planet name (campaign-unique) the mission targets; null for missions not tied to a place. */
    public final String targetPlanetName;
    /** Industry id (e.g. {@code "refining"}) the mission targets; null for non-industry ops. */
    public final String targetIndustryId;
    /** Faction whose equipment flavors post-battle recovery; null when unknown. */
    public final String targetFactionId;
    /**
     * Faction id to stand up as the defender instead of the one the target
     * market actually belongs to; null for every ordinary mission, where the
     * market's own owner defends. Applied once, at the launch boundary, onto the
     * resolved {@code TargetProfile} — it replaces only that profile's faction
     * id, and no generator stage reads that field, so an override changes who
     * defends and leaves the terrain alone. See {@code campaign-battle-bridge-nouns.md}.
     */
    public final String defenderFactionOverride;
    /**
     * Faction whose own troops stand beside the company in this battle, or null
     * — which is every mission but the two defences. A defence is fought at
     * somebody's market and that somebody has a garrison of its own; every other
     * mission is the company arriving somewhere it has no friends. The id names
     * whose ground doctrine those troops wear, resolved through the bridge's one
     * roster path; how many of them there are is a reading of the target market
     * rather than of this field. See {@code polity-ground-doctrine.md}.
     */
    public final String alliedGarrisonFactionId;
    /**
     * How much of the allied faction's own strength turns out here — the numbers
     * axis of the defending polity's ground doctrine, reaching the battle as a
     * multiplier on {@code AlliedGarrisonSize} rather than as a headcount.
     *
     * <p>{@code 1} on a mission that states none, which is every mission that has
     * no allied garrison at all and every patron's Garrison defence: a patron's
     * militia is sized by its own market and the company has no say in it. Only
     * the polity's own defence carries anything else, because doctrine is a thing
     * the player's faction has and a patron's is vanilla's business. See
     * {@code polity-ground-doctrine.md}.
     */
    public final float alliedGarrisonStrengthMult;
    /**
     * How much of this battle's map is settled, or null to derive it from the
     * target market's size.
     *
     * <p>This is the battle's statement and not the planet's. {@code
     * precincts.md} makes that a law: the same world can be an installation in
     * wilderness or a city with an installation in it, and which one it is
     * belongs to the battle. A mission that names one overrides whatever the
     * market's size would have said; a mission that does not lets
     * {@code SettlementZoning.sprawlFor} answer from the size.
     */
    public final PrecinctPlan.Sprawl sprawl;

    /**
     * How far from the objective this battle's force lands, or null to take the
     * mission type's own default.
     *
     * <p>The same shape as {@link #sprawl} and for the same reason: how much
     * approach a force has to fight through is a statement about this battle
     * rather than about the planet. Only Conquest consults one today, and its
     * default is the walk the balance was measured on; everything else lands on
     * the map edge.
     */
    public final Standoff standoff;

    /**
     * How many lanes of resistance run between this battle's beachhead and its
     * objective, or null for one per command track.
     *
     * <p>The third of the map statements a battle owns, beside {@link #sprawl}
     * and {@link #standoff}. A mission states the <em>count</em> and nothing
     * else: what stands on each lane is a ladder stepped down from the
     * objective's own fortification, which is a fact about the world rather
     * than about the operation. Only Conquest consults one; see
     * {@code precincts.md}.
     */
    public final Integer lanes;

    /**
     * What this battle's force comes down on, or null for the kind the target
     * world offers.
     *
     * <p>The fourth of the map statements a battle owns, beside {@link #sprawl},
     * {@link #standoff} and {@link #lanes}. Whether the beachhead is a civil
     * spaceport, a bare field or a strip with a hut on it is a fact about the
     * world, so a mission that says nothing gets the world's own answer; a
     * mission that wants the marines put down on open ground says so. Only
     * Conquest lays a landing place; see {@code precincts.md}.
     */
    public final LandingKind landing;

    /**
     * The route each of those lanes takes, in lane order, or null for lanes
     * that derive their own.
     *
     * <p>The other half of the lane statement, and the one a mission designer
     * reaches for when the ground matters: a lane is a path from the beachhead
     * to the objective, and writing one down is how a scenario says that the
     * eastern approach goes round the lake rather than across it. Stated as
     * fractions of the map, so the same brief lays out at any size.
     *
     * <p>Empty rather than null for a mission that says nothing, so a caller
     * need not guard. A count of zero outranks any route: a lane that does not
     * exist has nowhere to go.
     */
    public final List<LanePath> lanePaths;

    /**
     * Fixed battle seed, or null to seed the battle off the wall clock (the
     * ordinary case — a relaunched mission should be a fresh map). Pinned only
     * where the point of the mission is comparing two launches on one
     * battlefield.
     */
    public final Long battleSeed;

    /**
     * Campaign-tier contract id this mission resolves a phase of; {@code -1} for
     * ad-hoc missions not bound to a contract. The resolver bridges back to
     * {@link com.dillon.starsectormarines.campaign.CampaignState#contractIndex}
     * on this id to advance the contract row.
     */
    public final long contractId;

    /** Stable black-swan event lineage; {@code -1} for non-event missions. */
    public final long campaignEventId;
    /** Frozen event market registry slot; {@code -1} for non-event missions. */
    public final int campaignEventMarketId;
    /** Frozen civilian stakes for rescue missions; zero otherwise. */
    public final int civiliansAtRisk;
    /** Frozen automated-threat authority for Silent Colony; {@code -1} otherwise. */
    public final long campaignEventThreatSeed;

    /** Salvage % cap baked into the contract (0..255). 0 for non-contract missions. */
    public final byte salvageBaseline;
    /** Salvage % the player locked in at acceptance (0..salvageBaseline). */
    public final byte salvageNegotiated;
    /** Cash multiplier from salvage negotiation (0..255; 100 = baseline). */
    public final byte cashMultiplier;
    /** Contract-wide salvage cap; differs from phase cap on Planetary Assault. */
    public final byte contractSalvageBaseline;
    /** Contract-wide negotiated salvage frozen at first deployment. */
    public final byte contractSalvageNegotiated;

    /** @return a builder with every optional field defaulted to its "absent" value. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a builder pre-loaded with every field of {@code source}, for the
     *     copy-with-changes case (re-negotiating a briefing's salvage terms).
     *     Seeding from the mission itself is the point: a hand-written copy has
     *     to be revisited every time this class gains a field, and silently
     *     drops the new one until somebody notices.
     */
    public static Builder builder(Mission source) {
        return new Builder(source);
    }

    private Mission(Builder b) {
        this.id           = b.id;
        this.name         = b.name;
        this.type         = b.type;
        this.source       = b.source != null ? b.source : MissionSource.GENERATED;
        this.payout       = b.payout;
        this.risk         = b.risk;
        this.tier         = OperationTier.clampTo(
                b.tier != null ? b.tier : OperationTier.forRisk(b.risk),
                b.type != null ? b.type.tierFloor : null);
        this.requirements = b.requirements;
        this.flavor       = b.flavor;
        this.normalizedX  = b.normalizedX;
        this.normalizedY  = b.normalizedY;
        this.clientFighterSupport = b.clientFighterSupport != null
                ? b.clientFighterSupport : FlybyRoster.EMPTY;
        this.enemyFighterSupport  = b.enemyFighterSupport  != null
                ? b.enemyFighterSupport  : FlybyRoster.EMPTY;
        this.employerPowerIds     = b.employerPowerIds != null
                ? Collections.unmodifiableList(new ArrayList<>(b.employerPowerIds))
                : Collections.emptyList();
        this.requiredDrops = Math.max(0, b.requiredDrops);
        this.employerShuttles = Math.max(0, Math.min(b.employerShuttles, this.requiredDrops));
        this.marineArrivalPolicy = b.marineArrivalPolicy != null
                ? b.marineArrivalPolicy : MarineArrivalPolicy.defaultFor(this.type);
        this.fieldPresencePolicy = b.fieldPresencePolicy != null
                ? b.fieldPresencePolicy : FieldPresencePolicy.defaultFor(this.type);
        this.conquestArrivalConfig = b.conquestArrivalConfig != null
                ? b.conquestArrivalConfig : ConquestArrivalConfig.defaultFor(this.type);
        this.targetPlanetName = b.targetPlanetName;
        this.targetIndustryId = b.targetIndustryId;
        this.targetFactionId  = b.targetFactionId;
        this.defenderFactionOverride = b.defenderFactionOverride;
        this.alliedGarrisonFactionId = b.alliedGarrisonFactionId;
        this.alliedGarrisonStrengthMult =
                Float.isNaN(b.alliedGarrisonStrengthMult)
                        || b.alliedGarrisonStrengthMult < 0f
                        ? 1f : b.alliedGarrisonStrengthMult;
        this.sprawl           = b.sprawl;
        this.standoff         = b.standoff;
        this.landing          = b.landing;
        this.lanes            = b.lanes;
        // Not List.copyOf: a null entry is a lane that derives its own route,
        // which is exactly what a mission stating one lane's path and leaving
        // the others alone writes down.
        this.lanePaths        = b.lanePaths == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(b.lanePaths));
        this.battleSeed       = b.battleSeed;
        this.contractId        = b.contractId;
        this.campaignEventId = b.campaignEventId > 0L ? b.campaignEventId : -1L;
        this.campaignEventMarketId = this.campaignEventId > 0L
                ? Math.max(-1, b.campaignEventMarketId) : -1;
        this.civiliansAtRisk = this.source.isCivilianRescue()
                ? Math.max(0, b.civiliansAtRisk) : 0;
        this.campaignEventThreatSeed = this.campaignEventId > 0L
                && b.campaignEventThreatSeed >= 0L
                ? b.campaignEventThreatSeed : -1L;
        this.salvageBaseline   = b.salvageBaseline;
        this.salvageNegotiated = b.salvageNegotiated;
        this.cashMultiplier    = b.cashMultiplier;
        this.contractSalvageBaseline = b.contractSalvageBaseline;
        this.contractSalvageNegotiated = b.contractSalvageNegotiated;
    }

    /**
     * Mutable staging area for one {@link Mission}. Defaults are the values a
     * plain ad-hoc mission carries — no contract, no campaign event, no salvage
     * entitlement, a baseline cash multiplier — so a caller names only the
     * fields its mission kind actually has.
     *
     * <p>The salvage setters take {@code int} and narrow internally. The fields
     * are bytes holding 0..255 percentages, and making every call site spell
     * {@code (byte) 100} bought nothing.
     *
     * <p>Not thread-safe. Build one, call {@link #build()}, drop it.
     */
    public static final class Builder {

        private String id;
        private String name;
        private MissionType type;
        private MissionSource source;
        private int payout;
        private RiskLevel risk;
        private OperationTier tier;
        private String requirements = "";
        private String flavor = "";
        private float normalizedX;
        private float normalizedY;
        private FlybyRoster clientFighterSupport = FlybyRoster.EMPTY;
        private FlybyRoster enemyFighterSupport = FlybyRoster.EMPTY;
        private List<String> employerPowerIds = Collections.emptyList();
        private int requiredDrops;
        private int employerShuttles;
        private MarineArrivalPolicy marineArrivalPolicy;
        private FieldPresencePolicy fieldPresencePolicy;
        private ConquestArrivalConfig conquestArrivalConfig;
        private String targetPlanetName;
        private String targetIndustryId;
        private String targetFactionId;
        private String defenderFactionOverride;
        private String alliedGarrisonFactionId;
        private float alliedGarrisonStrengthMult = 1f;
        private PrecinctPlan.Sprawl sprawl;
        private Standoff standoff;
        private Integer lanes;
        private LandingKind landing;
        private List<LanePath> lanePaths;
        private Long battleSeed;

        private long contractId = -1L;
        private long campaignEventId = -1L;
        private int campaignEventMarketId = -1;
        private int civiliansAtRisk;
        private long campaignEventThreatSeed = -1L;

        private byte salvageBaseline;
        private byte salvageNegotiated;
        private byte cashMultiplier = (byte) 100;
        private byte contractSalvageBaseline;
        private byte contractSalvageNegotiated;

        private Builder() {
        }

        private Builder(Mission m) {
            this.id = m.id;
            this.name = m.name;
            this.type = m.type;
            this.source = m.source;
            this.payout = m.payout;
            this.risk = m.risk;
            this.tier = m.tier;
            this.requirements = m.requirements;
            this.flavor = m.flavor;
            this.normalizedX = m.normalizedX;
            this.normalizedY = m.normalizedY;
            this.clientFighterSupport = m.clientFighterSupport;
            this.enemyFighterSupport = m.enemyFighterSupport;
            this.employerPowerIds = m.employerPowerIds;
            this.requiredDrops = m.requiredDrops;
            this.employerShuttles = m.employerShuttles;
            this.marineArrivalPolicy = m.marineArrivalPolicy;
            this.fieldPresencePolicy = m.fieldPresencePolicy;
            this.conquestArrivalConfig = m.conquestArrivalConfig();
            this.targetPlanetName = m.targetPlanetName;
            this.targetIndustryId = m.targetIndustryId;
            this.targetFactionId = m.targetFactionId;
            this.defenderFactionOverride = m.defenderFactionOverride;
            this.alliedGarrisonFactionId = m.alliedGarrisonFactionId;
            this.alliedGarrisonStrengthMult = m.alliedGarrisonStrengthMult;
            this.sprawl = m.sprawl;
            this.standoff = m.standoff;
            this.lanes = m.lanes;
            this.landing = m.landing;
            this.lanePaths = m.lanePaths;
            this.battleSeed = m.battleSeed;
            this.contractId = m.contractId;
            this.campaignEventId = m.campaignEventId;
            this.campaignEventMarketId = m.campaignEventMarketId;
            this.civiliansAtRisk = m.civiliansAtRisk;
            this.campaignEventThreatSeed = m.campaignEventThreatSeed;
            this.salvageBaseline = m.salvageBaseline;
            this.salvageNegotiated = m.salvageNegotiated;
            this.cashMultiplier = m.cashMultiplier;
            this.contractSalvageBaseline = m.contractSalvageBaseline;
            this.contractSalvageNegotiated = m.contractSalvageNegotiated;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(MissionType type) {
            this.type = type;
            return this;
        }

        public Builder source(MissionSource source) {
            this.source = source;
            return this;
        }

        public Builder payout(int payout) {
            this.payout = payout;
            return this;
        }

        public Builder tier(OperationTier tier) {
            this.tier = tier;
            return this;
        }

        public Builder risk(RiskLevel risk) {
            this.risk = risk;
            return this;
        }

        public Builder requirements(String requirements) {
            this.requirements = requirements;
            return this;
        }

        public Builder flavor(String flavor) {
            this.flavor = flavor;
            return this;
        }

        /** Position on the tactical map, both 0..1. */
        public Builder mapPosition(float normalizedX, float normalizedY) {
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            return this;
        }

        public Builder clientFighterSupport(FlybyRoster clientFighterSupport) {
            this.clientFighterSupport = clientFighterSupport;
            return this;
        }

        public Builder enemyFighterSupport(FlybyRoster enemyFighterSupport) {
            this.enemyFighterSupport = enemyFighterSupport;
            return this;
        }

        public Builder employerPowerIds(List<String> employerPowerIds) {
            this.employerPowerIds = employerPowerIds;
            return this;
        }

        public Builder requiredDrops(int requiredDrops) {
            this.requiredDrops = requiredDrops;
            return this;
        }

        public Builder employerShuttles(int employerShuttles) {
            this.employerShuttles = employerShuttles;
            return this;
        }

        public Builder marineArrivalPolicy(MarineArrivalPolicy marineArrivalPolicy) {
            this.marineArrivalPolicy = marineArrivalPolicy;
            return this;
        }

        public Builder fieldPresencePolicy(FieldPresencePolicy fieldPresencePolicy) {
            this.fieldPresencePolicy = fieldPresencePolicy;
            return this;
        }

        public Builder conquestArrivalConfig(ConquestArrivalConfig conquestArrivalConfig) {
            this.conquestArrivalConfig = conquestArrivalConfig;
            return this;
        }

        public Builder targetPlanetName(String targetPlanetName) {
            this.targetPlanetName = targetPlanetName;
            return this;
        }

        public Builder targetIndustryId(String targetIndustryId) {
            this.targetIndustryId = targetIndustryId;
            return this;
        }

        public Builder targetFactionId(String targetFactionId) {
            this.targetFactionId = targetFactionId;
            return this;
        }

        /** @param defenderFactionOverride faction to defend instead of the market's owner; null to leave it alone. */
        public Builder defenderFactionOverride(String defenderFactionOverride) {
            this.defenderFactionOverride = defenderFactionOverride;
            return this;
        }

        /**
         * @param alliedGarrisonFactionId faction whose own troops fight beside the
         *     company here; null for a mission with no friends on the ground.
         */
        public Builder alliedGarrisonFactionId(String alliedGarrisonFactionId) {
            this.alliedGarrisonFactionId = alliedGarrisonFactionId;
            return this;
        }

        /**
         * @param alliedGarrisonStrengthMult how much of the allied faction's own
         *     strength turns out here; {@code 1} for a mission that states no
         *     doctrine of its own. Negative or NaN reads as 1.
         */
        public Builder alliedGarrisonStrengthMult(float alliedGarrisonStrengthMult) {
            this.alliedGarrisonStrengthMult = alliedGarrisonStrengthMult;
            return this;
        }

        /** @param sprawl how settled this battle's map is; null derives it from the market. */
        public Builder sprawl(PrecinctPlan.Sprawl sprawl) {
            this.sprawl = sprawl;
            return this;
        }

        /** @param standoff how far out this battle lands; null takes the mission type's default. */
        public Builder standoff(Standoff standoff) {
            this.standoff = standoff;
            return this;
        }

        /** @param lanes how many lanes of resistance the map lays; null takes one per command track. */
        public Builder lanes(Integer lanes) {
            this.lanes = lanes;
            return this;
        }

        /** @param landing what this battle comes down on; null takes the world's own answer. */
        public Builder landing(LandingKind landing) {
            this.landing = landing;
            return this;
        }

        /**
         * @param lanePaths the route each lane takes, in lane order, with a
         *                  null entry for a lane that derives its own; null or
         *                  empty leaves every lane to derive.
         */
        public Builder lanePaths(List<LanePath> lanePaths) {
            this.lanePaths = lanePaths;
            return this;
        }

        /** @param battleSeed fixed generation seed; null restores wall-clock seeding. */
        public Builder battleSeed(Long battleSeed) {
            this.battleSeed = battleSeed;
            return this;
        }

        public Builder contractId(long contractId) {
            this.contractId = contractId;
            return this;
        }

        public Builder campaignEventId(long campaignEventId) {
            this.campaignEventId = campaignEventId;
            return this;
        }

        public Builder campaignEventMarketId(int campaignEventMarketId) {
            this.campaignEventMarketId = campaignEventMarketId;
            return this;
        }

        public Builder civiliansAtRisk(int civiliansAtRisk) {
            this.civiliansAtRisk = civiliansAtRisk;
            return this;
        }

        public Builder campaignEventThreatSeed(long campaignEventThreatSeed) {
            this.campaignEventThreatSeed = campaignEventThreatSeed;
            return this;
        }

        public Builder salvageBaseline(int salvageBaseline) {
            this.salvageBaseline = (byte) salvageBaseline;
            return this;
        }

        public Builder salvageNegotiated(int salvageNegotiated) {
            this.salvageNegotiated = (byte) salvageNegotiated;
            return this;
        }

        public Builder cashMultiplier(int cashMultiplier) {
            this.cashMultiplier = (byte) cashMultiplier;
            return this;
        }

        public Builder contractSalvageBaseline(int contractSalvageBaseline) {
            this.contractSalvageBaseline = (byte) contractSalvageBaseline;
            return this;
        }

        public Builder contractSalvageNegotiated(int contractSalvageNegotiated) {
            this.contractSalvageNegotiated = (byte) contractSalvageNegotiated;
            return this;
        }

        public Mission build() {
            return new Mission(this);
        }
    }

    /** Null-safe compatibility accessor for missions loaded from older saves. */
    public ConquestArrivalConfig conquestArrivalConfig() {
        return conquestArrivalConfig != null
                ? conquestArrivalConfig : ConquestArrivalConfig.defaultFor(type);
    }
}
