# Ship & Hull-Mod Flavor Survey — ground-combat tie-ins

> Reference data for [`overview.md`](overview.md). A sweep of vanilla
> Starsector (0.98a) ship descriptions (`data/strings/descriptions.csv`) and
> hull mods (`data/hullmods/hull_mods.csv`) for flavor that justifies a
> player Command Power in the ground battle sub-game. Quotes are the load-bearing
> fragment, lightly trimmed. Append as new hooks surface.
>
> **The anchor finding:** vanilla already ships a literal mechanic —
> `ground_support` / `advanced_ground_support` hull mods buff planetary-raid
> strength "up to the total number of marines in the fleet." That is the
> precedent that everything here builds on.
>
> **Read through the projection lens** (see `overview.md`): the question isn't
> "does the flavor mention combat" but "does this capability *project onto the
> ground op*?" Three buckets — (a) **becomes a power** (Valkyrie drop, Survey
> Equipment scan-ahead), (b) **scales a power** (hangar/deck capacity → bigger
> CAS wing, targeting → placement range), (c) **ship-only, reject** (Heavy
> Armor, Blast Doors, Reinforced/Hardened — hull survival with no line to the
> marines; these stay in campaign fleet-fitting). The "Marine Kit / Durability"
> rows below are mostly bucket (c) and are kept only as a what-we-considered
> record, not as a power shortlist.

## Ships — strong hooks (explicit marines / assault / bombardment)

| Ship (`id`) | Flavor fragment | Ground tie-in (candidate power) |
| --- | --- | --- |
| **Valkyrie** (`valkyrie`) | "standard destroyer-sized troop transport… atmosphere-capable combat dropship with ground support weapon mounts… deploy heavy infantry support for tech raids on decivilized worlds" | **The** canonical Marine Insertion — drop a heavy-infantry squad, optionally with a strafing run on the way in. |
| **Phantom** (`phantom`) | "advanced troop transport employing phase stealth technology… put to effective use by Tri-Tachyon black ops" | Stealth insertion — phase-cloaked drop behind enemy lines / inside the perimeter. |
| **Astral** (`astral`) | "heart of a carrier task force which can subjugate entire planets" | Saturation Close Air Support — multiple coordinated strike wings over a wide area. |
| **Legion** (`legion`) | "battlecarrier… nanoforges keyed to replacement of fightercraft" | Sustained CAS — air cover that regenerates between uses. |
| **Gryphon** (`gryphon`) | "rapidly concentrate bursts of specialized heavy firepower… a devastating hammer" | Missile fire-mission — precision missile barrage on a target zone. |
| **Onslaught** (`onslaught`) | "withstand a heavy barrage… unmatched ballistic potential" | Rolling ballistic barrage across a map line. |
| **Invictus** (`invictus`) | "gargantuan mass of armor and all-big-guns… the gauntleted fist that smashed all opposition" | Heavy orbital hammer — flatten a zone, long cooldown. |
| **Mora** (`mora`) | "hulls were half-buried on alien worlds and used as armored power-stations… to form the core of new settlements" | Forward Operating Base — landed hull becomes a hardened rally/resupply point. |
| **Revenant** (`revenant`) | "stealth freighter… phase technology to support covert fleet operations… boon to the logistics of Tri-Tachyon raiders" | Covert resupply — phase in, drop munitions without interception. |
| **War drones** (`bastillon`, `berserker`, `defender`, `sentry`, `warden`, `picket`, `rampart`) | "garrisons bolstered by drone ships… deployed liberally against rebellious worlds" | Automated suppression — release war drones to strafe a zone. |
| **"Kardakes" siege platform** (`standoff_unit`) | "intended to replace the Hathoda-class orbital siege-capable heavy ballistic weapons platform… deployed during the retaking of the Eridani Insurrec[tion]" | Strongest explicit lore for a slow-cycling, massive-damage orbital siege power. |
| **"Fulgurite" support cruiser** (`overseer_unit`) | "spinal-mount microwave emitter modulated for orbit-to-[surface]" (text redacted) | Orbit-to-surface directed-energy beam — sustained line damage or area denial. |
| **Rampart drone** (`rampart`) | "artillery platform with engines… heavy firepower necessary to crack armored orbital fortresses" | Drone artillery vs hardened positions / bunkers. |

## Ships — loose hooks (need a creative leap: atmospheric / recon / logistics)

| Ship (`id`) | Flavor fragment | Ground tie-in (candidate power) |
| --- | --- | --- |
| **Apogee** (`apogee`) | "long-range tech scanners… exploration of hostile space" | Intel sweep — strip fog from a large map section. |
| **Shade** (`shade`) | "smallest phase ship… used as a scout… disable enemy ships with its EMP emitter" | Recon reveal + EMP — disable enemy vehicles/comms on the surface. |
| **Kite** (`kite`) | "aeroshuttle… streamlined for efficient atmospheric flight… powerful engines for a civilian ship" | Light insertion/extraction or fast forward-observer. |
| **Heron** (`heron`) | "hit quickly, and withdraw while enemy forces are still responding" | Quick-reaction airstrike — fast cooldown, limited wings. |
| **Condor** (`condor`) | "easiest way to procure fighter capability" | Budget CAS — lower-quality strike craft. |
| **Drover** (`drover`) | "strategically versatile… fighter platform with low logistical overhead" | Loiter CAP — persistent overhead spotting/intercept. |
| **Tarsus** (`tarsus`) | "hazard duty ships… to supply frontiers and front-line fleets" | Resupply — restore squad ammo mid-battle. |
| **Atlas** (`atlas`) | "resupply entire outposts… the lifeline of Hegemony citizens" | Mass resupply — full restock, high cost. |
| **Prometheus** (`prometheus`, `prometheus2`) | tanker of "strategic quantities of fuel"; Pather conversion "spews barrages of missiles" | Incendiary/fuel-air strike (creative leap). |
| **Mercury** (`mercury`) | "transporting VIPs or couriers between star systems" | Commander insertion / VIP extraction. |
| **Mudskipper** (`mudskipper`) | "interplanetary ferry… durable armor to protect passengers" | Cheap, risky single-squad lander. |
| **Crig / Salvage Rig** (`crig`) | "gantry and manipulators… recovering derelict hulks" | Field repair — restore a wrecked vehicle/emplacement. |
| **Ox** (`ox`) | "drive field stabilizer… reach a higher burn level" | Force-multiplier — reduce other powers' cooldowns. |
| **"Ilmari" mobile fabricator** (`fabricator_unit`) | "frontier manufacturing base to leapfrog development" | Field fabrication — temporary on-site nanoforge / reinforcement source. |
| **Doom** (`doom`) | "unnerving psychological phenomena… after rapid phase-shifts" | Morale/suppression aura on enemy units (thematic leap). |
| **Starliner / Nebula** (`starliner`, `nebula`) | passenger liners; Starliner has "a small landing/launch bay" | Mass reinforcement, or civilian-evac scenario power. |

## Hull mods — strong hooks (explicit ground / crew / combat)

| Hull mod (`id`) | Flavor fragment | Ground tie-in (candidate power/modifier) |
| --- | --- | --- |
| **Ground Support Package** (`ground_support`) | "Close support weapons and counter-measures for ground defenses. Increases the effective strength of planetary raids… up to the total number of marines in the fleet." | **The precedent.** 1:1 → orbital close-support / suppression-fire power; literally buffs marine ground strength. |
| **Advanced Ground Support** (`advanced_ground_support`) | same, larger magnitude | Tiered support — light strike vs heavy bombardment. |
| **Blast Doors** (`blast_doors`) | "heavily reinforced doors at critical junctures… fewer crew casualties from hull damage" | Fortify — reduced squad casualties holding a structure. |
| **Recovery Shuttles** (`recovery_shuttles`) | "Reduces the casualties suffered by fighter pilots" | CASEVAC — save downed marines after an engagement. |
| **Assault Package** (`assault_package`) | "Will try to engage enemies… act as a combat ship" | Aggressive-entry / breach buff on first contact. |
| **Expanded Deck Crew** (`expanded_deck_crew`) | "Reduces the rate at which fighter replacement rate decreases" | Faster reinforcement/replacement rate in prolonged fights. |
| **Automated Repair Unit** (`autorepair`) | "Reduces the time required to repair disabled weapons and engines" | Field repair of disabled vehicles/emplacements. |
| **Operations Center** (`operations_center`) | "Increases command point recovery rate… on the flagship" | C2 — accelerates the command-point regen that gates all powers. |
| **Nav Relay** (`nav_relay`) | "increases nav rating of your fleet… increases the top speed of deployed ships" | Coordinated movement — squad repositioning speed / reduced travel time. |

## Hull mods — loose hooks (mechanical mods needing a thematic leap)

| Hull mod (`id`) | Maps to |
| --- | --- |
| **Reinforced Bulkheads** (`reinforcedhull`) | Squad survivability — KIA→injured threshold. |
| **Heavy Armor** (`heavyarmor`) | Heavy-armor loadout — damage reduction, trade mobility. |
| **Shield Shunt** (`shield_shunt`) | Drop-shield-for-plate assault variant. |
| **Hardened Subsystems** (`hardened_subsystems`) | Endurance — slower morale/readiness decay. |
| **Efficiency Overhaul** (`efficiency_overhaul`) | Faster post-battle recovery, cheaper resupply. |
| **Militarized Subsystems** (`militarized_subsystems`) | Non-combat units operate in hot zones without penalty. |
| **High Resolution Sensors** (`hiressensors`) | Recon — extend vision / lift fog. |
| **ECM Package** (`ecm`) | Jamming — degrade enemy targeting / delay reinforcements. |
| **ECCM Package** (`eccm`) | Counter-jamming — cancel enemy ECM on friendlies. |
| **Integrated Targeting Unit** (`targetingunit`) / **Dedicated Targeting Core** (`dedicated_targeting_core`) | Targeting uplink — extend marine weapon range / hit chance. |
| **Surveying Equipment** (`surveying_equipment`) | Pre-battle recon — reveal map layout / caches. |
| **Additional Berthing** (`additional_berthing`) | Carry more marines — extra reinforcement squad. |
| **Expanded Cargo Holds** (`expanded_cargo_holds`) | Supply drop — ammo/medical resupply mid-mission. |
| **Auxiliary Fuel Tanks** (`auxiliary_fuel_tanks`) | Extended deployment — operate further from LZ / longer timer. |
| **Augmented Drive Field** (`augmentedengines`) | Rapid insertion — earlier/faster reinforcement drop. |
| **Auxiliary Thrusters** (`auxiliarythrusters`) | Extra extraction window per engagement. |
| **Safety Overrides** (`safetyoverrides`) | "Push the line" — burst speed/attack rate, more casualties. |
| **Insulated Engine Assembly** (`insulatedengine`) / **Phase Field** (`phasefield`) | Silent/cloaked insertion — surprise-attack opener. |
| **Rugged Construction** (`rugged`) | Rugged kit — chance to avoid permanent casualties. |
| **Hardened Shields** (`hardenedshieldemitter`) / **Accelerated Shields** (`advancedshieldemitter`) | Powered-armor shield — temporary damage absorb / quick-raise reaction defense. |
| **Salvage Gantry** (`repair_gantry`) | Field salvage — extra loot from held objectives. |
| **Distributed Fire Control** (`distributed_fire_control`) | Leader-death resilience — no coherency penalty for one activation. |
| **Shielded Cargo Holds** (`shielded_holds`) | Masked approach — reduced detection in hostile territory. |

## Notes for the next pass

- **Special Modifications** (`andrada_mods`) is a *debuff* flavor ("increased
  crew casualties") — a candidate negative modifier if d-mod / cut-corners
  ships ever penalize the ground op.
- The redacted `overseer_unit` / `standoff_unit` / `fabricator_unit`
  descriptions (Domain INFOSEC-violation flavor) are the richest *explicit*
  orbit-to-surface lore in vanilla — worth re-reading in full when designing
  the heavy orbital-support tier.
- Weapon descriptions (not surveyed exhaustively here) also carry hooks — e.g.
  the **Heavy Mortar** "originally designed for use in planetary surface
  combat… urban bombardment." A weapon-flavor pass is a cheap follow-up.
