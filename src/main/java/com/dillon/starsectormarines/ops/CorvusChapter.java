package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.AssaultBattleFixture;
import com.dillon.starsectormarines.battle.fixture.ColdAccountBattleFixture;
import com.dillon.starsectormarines.battle.fixture.RaidBattleFixture;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;

import java.util.Set;
import java.util.List;

/** Authored local raids against the Sable Wake crew, not wars against their host planets. */
public enum CorvusChapter {
    DUST_LEDGER("asharu", "Asharu", "The Dust Ledger", MissionType.RAID, RiskLevel.LOW, 2, 22000,
            SurfacePalette.ARID, EconomicFunction.COMMERCE,
            "The Sable Wake crew buys departure times from a broker on Asharu. Their inland counting house holds the dispatch archive. Seize the marked high-value target and return to the extraction point with the records. We need their buyers, not a fight with Asharu's authorities.",
            "The archive names a listening post on Warion. Sable Wake has been matching our manifests to intercepted departure calls. We can take that link away."),
    BLIND_WATCH("corvus_IIIb", "Warion", "Blind Watch", MissionType.SABOTAGE, RiskLevel.LOW, 3, 30000,
            SurfacePalette.ROCK, EconomicFunction.MILITARY,
            "The Asharu records led us to Sable Wake's listening post on Warion. Plant charges at all three marked installations to disable its interception network. Your squads must cover the planters while the site's security force responds. This is a temporary raider outpost on barren ground, far from any colony. Expect a light local garrison; our route watch has ruled out outside reinforcements.",
            "Their listening post is silent. The archive and captured routing tables point to a dispatch compound on Garnir. That is where Sable Wake sells the cargo and sends the next crews out."),
    COLD_ACCOUNT("corvus_IIIa", "Garnir", "A Cold Account", MissionType.ASSAULT, RiskLevel.HIGH, 4, 42000,
            SurfacePalette.FROZEN, EconomicFunction.HEAVY_INDUSTRY,
            "Sable Wake's dispatch compound on Garnir is the last link. Secure the marked pirate dispatch records and transmit the proof. A Hegemony Onslaught will intervene only once those records establish the site's pirate ownership. Withdraw every surviving squad and your Hound to the marked extraction point. The Onslaught will hold outside the eastern perimeter and open fire as soon as your people clear the marked bombardment area. There is no grace period. Your shuttles will return for the survivors while the batteries demolish the stronghold; reach the pickup and board. Expect fortified positions and better-equipped crews; you do not need to clear the entire garrison.",
            "The records proved the compound belonged to Sable Wake. Your company withdrew, and the Hegemony destroyed the dispatch stronghold. You have broken Sable Wake's operation against Wayline in Corvus. Other crews will still work these lanes; I will not sell you a promise that piracy is over.");

    public final String planetId, planetName, title, briefing, debrief;
    public final MissionType type;
    public final RiskLevel risk;
    public final int squads, payment;
    public final SurfacePalette surface;
    private final EconomicFunction function;

    CorvusChapter(String planetId, String planetName, String title, MissionType type, RiskLevel risk,
                  int squads, int payment, SurfacePalette surface, EconomicFunction function,
                  String briefing, String debrief) {
        this.planetId = planetId; this.planetName = planetName; this.title = title;
        this.type = type; this.risk = risk; this.squads = squads; this.payment = payment;
        this.surface = surface; this.function = function; this.briefing = briefing; this.debrief = debrief;
    }

    /** A remote compound's scale, with the actual world's surface captured at launch. */
    public TargetProfile profile(SurfacePalette ground) {
        // Scale three describes this small inhabited compound, not a new vanilla
        // colony. Zero selects the legacy unprofiled city recipe.
        return new TargetProfile(3, 5, this == COLD_ACCOUNT ? 2 : 1, 0, "pirates", Set.of(function),
                ground, SettlementLink.ROAD);
    }

    /** Same production construction values for campaign launch and opt-in evidence. */
    public BattleFixture fixture(long seed, List<ShuttleAssignment> manifest, TargetProfile profile) {
        return switch (this) {
            case DUST_LEDGER -> RaidBattleFixture.fromFactoryInputs(seed, manifest, false,
                    OperationTier.ESTABLISHED, risk, profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.REMOTE);
            case BLIND_WATCH -> SabotageBattleFixture.fromFactoryInputs(seed, manifest, false,
                    OperationTier.ESTABLISHED, risk, profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.REMOTE, false);
            case COLD_ACCOUNT -> new ColdAccountBattleFixture(AssaultBattleFixture.fromFactoryInputs(seed, manifest, false,
                    OperationTier.ESTABLISHED, risk, profile, FlybyRoster.EMPTY, FlybyRoster.EMPTY, PrecinctPlan.Sprawl.REMOTE));
        };
    }

    public Mission mission(String id, String actualPlanetName, String priorOutcome) {
        return Mission.builder().id(id).name(title).source(MissionSource.STORY).type(type)
                .tier(OperationTier.ESTABLISHED).risk(risk).payout(payment)
                .targetPlanetName(actualPlanetName).defenderFactionOverride("pirates")
                .sprawl(PrecinctPlan.Sprawl.REMOTE)
                .flavor("Mara Venn: " + (this == DUST_LEDGER ? priorOutcome + " " : "") + briefing)
                .requirements(squads + " squads recommended. Minimum: two squads with four ready marines each. "
                        + "Up to four fitted company boats can ferry the selected force. Wayline provides heavy lift "
                        + "for your active mech lance; select Mech Support. No fighter cover or employer infantry.")
                .requiredDrops(squads * 2).employerShuttles(0)
                .marineArrivalPolicy(MarineArrivalPolicy.PAIRED_HALF_SQUAD)
                .fieldPresencePolicy(FieldPresencePolicy.UNRESTRICTED)
                .mapPosition(0.5f, 0.5f).battleSeed(0xC07A500L + ordinal() * 101L).build();
    }
}
