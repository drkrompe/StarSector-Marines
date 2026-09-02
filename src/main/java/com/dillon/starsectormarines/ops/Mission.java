package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
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
        this.sprawl           = b.sprawl;
        this.standoff         = b.standoff;
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
        private PrecinctPlan.Sprawl sprawl;
        private Standoff standoff;
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
            this.sprawl = m.sprawl;
            this.standoff = m.standoff;
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
