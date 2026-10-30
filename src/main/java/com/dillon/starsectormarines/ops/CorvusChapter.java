package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.AssaultBattleFixture;
import com.dillon.starsectormarines.battle.fixture.BlindWatchBattleFixture;
import com.dillon.starsectormarines.battle.fixture.ColdAccountBattleFixture;
import com.dillon.starsectormarines.battle.fixture.DustLedgerBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.Set;
import java.util.List;
import java.util.function.Predicate;

/** Authored local raids against the Sable crew, not wars against their host planets. */
public enum CorvusChapter {
    DUST_LEDGER("asharu", "Asharu", "corvusDustLedger", MissionType.RAID, RiskLevel.LOW, 2, 22000,
            SurfacePalette.ARID, EconomicFunction.COMMERCE),
    BLIND_WATCH("corvus_IIIb", "Warion", "corvusBlindWatch", MissionType.SABOTAGE, RiskLevel.LOW, 5, 30000,
            SurfacePalette.ROCK, EconomicFunction.MILITARY),
    COLD_ACCOUNT("corvus_IIIa", "Garnir", "corvusColdAccount", MissionType.ASSAULT, RiskLevel.HIGH, 5, 42000,
            SurfacePalette.FROZEN, EconomicFunction.HEAVY_INDUSTRY);

    public final String planetId, planetName;
    private final String textKey;
    public final MissionType type;
    public final RiskLevel risk;
    public final int squads, payment;
    public final SurfacePalette surface;
    private final EconomicFunction function;

    CorvusChapter(String planetId, String planetName, String textKey, MissionType type, RiskLevel risk,
                  int squads, int payment, SurfacePalette surface, EconomicFunction function) {
        this.planetId = planetId; this.planetName = planetName; this.textKey = textKey;
        this.type = type; this.risk = risk; this.squads = squads; this.payment = payment;
        this.surface = surface; this.function = function;
    }

    public String title() { return Strings.get(textKey + "Title"); }
    public String briefing() { return Strings.get(textKey + "Briefing"); }
    public String debrief() { return Strings.get(textKey + "Debrief"); }

    /** A remote compound's scale, with the actual world's surface captured at launch. */
    public TargetProfile profile(SurfacePalette ground) {
        // Scale three describes this small inhabited compound, not a new vanilla
        // colony. Zero selects the legacy unprofiled city recipe.
        return new TargetProfile(3, 5, this == COLD_ACCOUNT ? 2 : 1, 0, "pirates", Set.of(function),
                ground, SettlementLink.ROAD);
    }

    /** {@link #fixture(long, List, TargetProfile, Predicate)} on a campaign that holds no marks (evidence, debug). */
    public BattleFixture fixture(long seed, List<ShuttleAssignment> manifest, TargetProfile profile) {
        return fixture(seed, manifest, profile, mark -> false);
    }

    /**
     * Same production construction values for campaign launch and opt-in evidence. {@code campaignHas} answers for the
     * campaign's marks at this boundary: Blind Watch's post stands to unless the Dust Ledger delivered Teague
     * ({@link BlindWatchProgress#stoodTo}).
     */
    public BattleFixture fixture(long seed, List<ShuttleAssignment> manifest, TargetProfile profile,
                                 Predicate<String> campaignHas) {
        return switch (this) {
            case DUST_LEDGER -> new DustLedgerBattleFixture(seed, manifest, risk, profile);
            case BLIND_WATCH -> new BlindWatchBattleFixture(seed, manifest, risk, profile,
                    BlindWatchProgress.stoodTo(campaignHas));
            case COLD_ACCOUNT -> new ColdAccountBattleFixture(AssaultBattleFixture.fromFactoryInputs(seed, manifest, false,
                    OperationTier.ESTABLISHED, risk, profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.REMOTE));
        };
    }

    public Mission mission(String id, String actualPlanetName, String priorOutcome) {
        return Mission.builder().id(id).name(title()).source(MissionSource.STORY).type(type)
                .tier(OperationTier.ESTABLISHED).risk(risk).payout(payment)
                .targetPlanetName(actualPlanetName).defenderFactionOverride("pirates")
                .sprawl(PrecinctPlan.Sprawl.REMOTE)
                .flavor(Strings.format("corvusFlavor", OpeningBroker.NAME,
                        this == DUST_LEDGER ? Strings.join("waylineSentenceJoin", priorOutcome, briefing()) : briefing()))
                .requirements(Strings.format("corvusRequirements", squads))
                .requiredDrops(squads * 2).employerShuttles(0)
                .marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                .mapPosition(0.5f, 0.5f).battleSeed(0xC07A500L + ordinal() * 101L).build();
    }
}
