package com.dillon.starsectormarines.ops.intel;

import com.dillon.starsectormarines.ops.MissionType;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static map from industry id to the mission archetypes that industry offers.
 * The mission generator walks a planet's industries, looks them up here, and emits
 * one mission per (industry, archetype) pair it picks.
 *
 * <p>Not every industry is represented — Population, Waystation, Aquaculture, Farming,
 * Cryosanctum, etc. are deliberately omitted as too low-value or too narrative-specific
 * for the generic pipeline. Add entries as gameplay grows into them.
 *
 * <p>Orbital/military fortifications aren't keyed individually — they raise the planet's
 * {@code DefenseLevel} score instead of producing their own mission strands. Their gameplay
 * weight is "ambient difficulty," not "go hit the Star Fortress directly."
 */
public final class IndustryMissionCatalog {

    private static final Map<String, List<MissionArchetype>> BY_INDUSTRY = build();

    private IndustryMissionCatalog() {}

    public static List<MissionArchetype> archetypesFor(String industryId) {
        List<MissionArchetype> archetypes = BY_INDUSTRY.get(industryId);
        return archetypes != null ? archetypes : Collections.<MissionArchetype>emptyList();
    }

    private static Map<String, List<MissionArchetype>> build() {
        Map<String, List<MissionArchetype>> m = new HashMap<>();

        // -- Industrial production targets ---------------------------------------
        m.put(Industries.HEAVYINDUSTRY, Arrays.asList(
                new MissionArchetype(MissionType.SABOTAGE, "Cripple the Foundry",
                        "Target floor is networked across three production halls; charges placed at the convergence " +
                        "node take all of it offline. Security relies on overhead gantry walks — clear those first."),
                new MissionArchetype(MissionType.RAID, "Loot the Production Floor",
                        "High-tech components are stockpiled near the shipping dock. Smash-and-grab opportunity, " +
                        "but the on-site militia rolls fast — extraction window is tight.")
        ));
        m.put(Industries.ORBITALWORKS, Arrays.asList(
                new MissionArchetype(MissionType.SABOTAGE, "Cripple the Shipworks",
                        "Atmospheric assembly bays. Vent the slipway pressure systems and the whole line goes dark " +
                        "until they can re-seal — weeks of downtime from one well-placed charge."),
                new MissionArchetype(MissionType.RAID, "Plunder the Assembly Line",
                        "Half-finished modules and rare alloy stocks. Most of it's bulky — bring breaching tools " +
                        "or accept that a third of the take stays behind.")
        ));
        m.put(Industries.REFINING, Collections.singletonList(
                new MissionArchetype(MissionType.SABOTAGE, "Cripple the Refinery",
                        "Smelter feeds and cracking towers. Heat-exchange manifolds are the weak point — drop them " +
                        "and the whole refinery has to cool for repair before it can restart.")
        ));
        m.put(Industries.FUELPROD, Collections.singletonList(
                new MissionArchetype(MissionType.SABOTAGE, "Cripple Fuel Synthesis",
                        "Volatiles are stored on-site. Charges on the catalysis loop trigger a chain rupture; " +
                        "operators evacuate, the plant goes inert for months while it's reconditioned.")
        ));

        // -- Extractive industries -----------------------------------------------
        m.put(Industries.MINING, Collections.singletonList(
                new MissionArchetype(MissionType.RAID, "Strip the Mining Camp",
                        "Outpost workforce is unarmed — the militia rides out from the perimeter station. " +
                        "Move fast, take the stockpile, be gone before the response sweep arrives.")
        ));
        m.put(Industries.TECHMINING, Collections.singletonList(
                new MissionArchetype(MissionType.RAID, "Raid the Tech-Mining Site",
                        "Recovered Domain-era artifacts are catalogued in a dig-site warehouse. Most of the find " +
                        "is junk; intel says the cataloging shed is where the actual finds are kept.")
        ));
        m.put(Industries.LIGHTINDUSTRY, Collections.singletonList(
                new MissionArchetype(MissionType.RAID, "Loot the Consumer Goods Stores",
                        "Volume target, not a high-tech one. Easy money, light resistance, fast loadout — a good " +
                        "shakedown for a green captain.")
        ));

        // -- Commerce / logistics -------------------------------------------------
        m.put(Industries.COMMERCE, Collections.singletonList(
                new MissionArchetype(MissionType.RAID, "Skim the Trade Floor",
                        "The exchange clearing house is a single hardened building. Hit it during a quarterly " +
                        "settlement and the take is real money instead of warehouse junk.")
        ));
        m.put(Industries.SPACEPORT, Arrays.asList(
                new MissionArchetype(MissionType.SABOTAGE, "Cripple the Spaceport",
                        "Drop the traffic-control tower and the orbital lanes go dark — incoming traffic re-routes " +
                        "to other systems for the duration. Symbolic damage outweighs the physical.")
        ));
        m.put(Industries.MEGAPORT, Arrays.asList(
                new MissionArchetype(MissionType.SABOTAGE, "Cripple the Megaport",
                        "Three stacked traffic control rings. Coordinated charges across all three are required " +
                        "for the closure to stick — partial damage just slows things down."),
                new MissionArchetype(MissionType.EXTRACTION, "Smuggle Out the Dock Master",
                        "Asset is the senior traffic controller — knows the orbital approach codes for the whole " +
                        "system. High-value, light personal security, but the dock crew might intervene.")
        ));

        // -- Military targets ----------------------------------------------------
        m.put(Industries.PATROLHQ, Arrays.asList(
                new MissionArchetype(MissionType.ASSAULT, "Overrun the Patrol Post",
                        "Lightly fortified barracks. Quick action while the patrol rotation is out; hit it before " +
                        "the standby element wakes up."),
                new MissionArchetype(MissionType.EXTRACTION, "Extract the Patrol Defector",
                        "Junior officer wants out. Smuggling route runs through the motor pool; subject is briefed.")
        ));
        m.put(Industries.MILITARYBASE, Arrays.asList(
                new MissionArchetype(MissionType.CONQUEST, "Conquer the Military Base",
                        "Full beachhead-to-citadel push. Drop on the perimeter, fight uphill through the " +
                        "civilian buffer, breach the fortress wall. Bring the regiment — this is not a raid."),
                new MissionArchetype(MissionType.ASSAULT, "Storm the Military Base",
                        "Fully fortified perimeter, dug-in defenders, layered killzones. This is the " +
                        "real thing — assume losses, plan for them."),
                new MissionArchetype(MissionType.EXTRACTION, "Recover the Defecting Officer",
                        "Senior officer with operational knowledge. Located in officers' quarters; the routine " +
                        "patrol checks each barracks block hourly.")
        ));
        m.put(Industries.HIGHCOMMAND, Arrays.asList(
                new MissionArchetype(MissionType.CONQUEST, "Take High Command",
                        "Full-scale planetary assault. The fortress walls aren't decorative — the regiment " +
                        "lives behind them. Coordinated breach, sustained push, hold the gates."),
                new MissionArchetype(MissionType.ASSAULT, "Decap Strike on High Command",
                        "Hardest target on the map. The chain of command operates from a buried bunker complex; " +
                        "above-ground assault is a feint, the real op is the access tunnel.")
        ));
        m.put(Industries.GROUNDDEFENSES, Collections.singletonList(
                new MissionArchetype(MissionType.ASSAULT, "Silence the Ground Defenses",
                        "Distributed battery emplacements. They cover each other — the assault is sequenced, " +
                        "not simultaneous; first battery dies fast, the rest of them know you're coming.")
        ));
        m.put(Industries.HEAVYBATTERIES, Collections.singletonList(
                new MissionArchetype(MissionType.ASSAULT, "Silence the Heavy Batteries",
                        "Hardened emplacements, automated turret cover, dedicated security platoon. Going at this " +
                        "without orbital fire support is borderline reckless.")
        ));

        return m;
    }
}
