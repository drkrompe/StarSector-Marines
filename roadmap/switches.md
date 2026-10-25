# Switches

Status: ACTIVE
Written: 2026-10-23

Every `-Dbattle.*` and `-Dmarines.*` system property the shipped code reads, with
what kind of switch it is and what retires it. `SwitchRegistryTest` keeps this
list and `src/main` in step: a property read missing here fails, and so does a
row whose property is no longer read. Evidence-harness parameters read only
under `src/test` (`outputDir`, `maxTicks`, `fixture.path`, the matchup and
snapshot options) are task configuration and are not listed.

A switch added for an A/B comparison is **temporary** (CLAUDE.md). The session
that adds one writes its row here and the removal condition in its property
constant's Javadoc, and removes the switch with its losing code path once the
comparison is decided.

| Kind | Meaning |
|------|---------|
| `experiment` | A live A/B. Its default is the candidate; remove it with the losing arm when the condition is met. |
| `tuning` | A number under live tuning. Fold it into a named constant when the pass closes. |
| `undecided` | Default off: code that has not shipped. Someone has to decide whether to ship it (drop the switch, keep the code) or drop it (delete the code). |
| `control` | A standing control lever that an evidence task or behaviour scene deliberately runs. The reason it is kept is given in the row. |
| `config` | Real configuration: the dev gate, determinism and concurrency, opt-in profiling, campaign presets. |

A name holding a `<placeholder>` is composed in code from a prefix.

| Switch | Kind | Added | Removal condition or reason |
|--------|------|-------|-----------------------------|
| `battle.ambient.fixtureLeanCells` | tuning | 2026-10-21 | Ambient crew pace tuning: fold into a constant once the look is accepted. |
| `battle.ambient.jogPace` | tuning | 2026-10-21 | Ambient crew pace tuning: fold into a constant once the look is accepted. |
| `battle.bodyPileCap` | experiment | 2026-10-22 | Remove once the renderEvidence body-pile map accepts the cap. |
| `battle.colonyBreach.civilDefence` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.fieldLanding` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.harvest` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.impale` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.impale.areaHitsHeld` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.livingField` | experiment | 2026-10-22 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.livingField.arms` | experiment | 2026-10-22 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.livingField.density` | experiment | 2026-10-22 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.loopholes` | experiment | 2026-10-22 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.militia` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.redoubtWorks` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.redoubts` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.roadblockSpan` | experiment | 2026-10-22 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.roadblocks` | experiment | 2026-10-22 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.colonyBreach.strikes` | experiment | 2026-10-21 | Colony Breach / Bloom sessions: remove each once its colony-breach reading is accepted (colony-breach-nouns.md). |
| `battle.command.conquest.balancedHomeTracks` | experiment | 2026-10-11 | Conquest command (2026-10-11): remove once commanderEvidence -Pmission=conquest has been read against it. |
| `battle.conquest.attackerHoldsGround` | experiment | 2026-10-20 | Conquest command (2026-10-20): remove once commanderEvidence -Pmission=conquest accepts it. |
| `battle.cordon.objectives` | control | 2026-10-23 | Permanent evidence lever: forces a Cordon battle's optional objectives (`all`, `none`, or keys / L1..R6) instead of rolling them, so commanderEvidence can exercise each beat on demand (cordon-nouns.md, Optional objectives). |
| `battle.counterattack` | control | 2026-10-20 | Standing control: the commander-evidence counter-attack control (commander-evidence-missions.md). Its composed .<knob> evidence knobs (reserveShare, minStrength, ...) fold into constants when the counter-attack balance pass closes. |
| `battle.creep.feedMultiplier` | tuning | 2026-10-22 | Creep-speed pass (owner picks 2026-10-23): fold into constants once the owner confirms the rates in play. |
| `battle.creep.spreadScale` | tuning | 2026-10-22 | Creep-speed pass (owner picks 2026-10-23): fold into constants once the owner confirms the rates in play. |
| `battle.elevation.firingReveal` | experiment | 2026-10-22 | Elevation pass (elevation-nouns.md): remove once the elevation behaviour scenes accept it. |
| `battle.elevation.sight` | experiment | 2026-10-22 | Elevation pass (elevation-nouns.md): remove once the elevation behaviour scenes accept it. |
| `battle.fire` | control | 2026-10-22 | Standing control: master fire cost control that renderEvidence and the fire-colony/burn readings are measured against. |
| `battle.fire.creepFilm` | experiment | 2026-10-22 | Fire: retire it, keeping the rule, once the owner accepts the bloom-m3 Burn re-render with 5-cell breaks. |
| `battle.fire.laneDiscipline` | experiment | 2026-10-23 | Fire-system session: remove each with its losing path once the fire behaviour pass is accepted (fire-nouns.md). |
| `battle.fire.spreadScale` | experiment | 2026-10-23 | Fire: a multiplier on each limited fuel's spread (0 = the old unbounded spread). Remove it with the losing arm once the owner picks how far a grass fire runs, folding the pick into `FireFuel`. |
| `battle.fire.smoke` | control | 2026-10-22 | Standing control: the control loop of the fire-smoke scene verdict. Retire it with that loop. |
| `battle.fire.stepOut` | control | 2026-10-22 | Standing control: the control loop of the fire-fear scene verdict. Retire it with that loop. |
| `battle.fire.throwSafety` | control | 2026-10-22 | Standing control: the control loop of the fire-ignition kit scene verdict. Retire it with that loop. |
| `battle.fog.bodyReveal` | experiment | 2026-10-22 | Remove once the fog body-reveal rule is accepted (fog-of-war-nouns.md). |
| `battle.fort.restraint` | control | 2026-10-13 | Standing control: ShieldFortScene runs the off arm as its control loop. Retire it with that loop. |
| `battle.garrison.envelopes` | experiment | 2026-10-20 | Garrisonables (garrisonables.md): remove once garrisonEvidence and the reactive-defence evidence accept it. |
| `battle.garrison.stretches` | experiment | 2026-10-20 | Garrisonables (garrisonables.md): remove once garrisonEvidence and the reactive-defence evidence accept it. |
| `battle.heart.armourByNeed` | experiment | 2026-10-23 | Broad Front lanes call armour pairs from the vehicle park's tool shed by need, not on the waves' timetable (`heart-allied-commander.md`). Remove with the losing arm at the first played comparison once the owner answers `heart-allied-tool`; `false` is the control, the timetable. |
| `battle.heart.broadArmourBehind` | experiment | 2026-10-23 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.broadArmourWaves` | experiment | 2026-10-23 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.broadCompanyArmour` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.broadFallBack` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.broadFormUp` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.broadStrength` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.broadWaitForLanes` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.broadWaves` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.burnArmourBehind` | experiment | 2026-10-23 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.burnFront` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.burnFrontArmour` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.burnFrontArmourWaves` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.burnFrontLateWaves` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.burnFrontTeams` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.burnFrontWaves` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.companySector` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.followBarrage` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.frontage` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.holdBack` | experiment | 2026-10-22 | Bloom mission 3 (Broad Front, The Burn): remove each once the owner's pick for that mission is settled (heart-nouns.md). Numeric ones fold into constants. |
| `battle.heart.laneFormation` | experiment | 2026-10-23 | Broad Front lanes as screened advances, screen by country (`heart-allied-commander.md`). Remove with the losing arm once the owner answers `heart-lane-formation`; `false` is the control, straight to the bore. |
| `battle.infantry.fightingPositions` | experiment | 2026-10-13 | Fighting positions (ai-nouns.md): remove once the defender fighting-position work is accepted. |
| `battle.infantry.laneSidestep` | control | 2026-09-01 | Standing control: FiringLineScene runs the off arm as its control loop (behavior-scenes.md). Retire it with that loop. |
| `battle.infantry.rejoin` | control | 2026-09-01 | Standing control: the control and contested-control loops of LateArrivalScene run the off arm. Retire it with those loops. |
| `battle.influence.async` | config | 2026-09-26 | Permanent: determinism. Test and evidence tasks force the synchronous arm, and the live game runs async. |
| `battle.junction.talonTwo` | experiment | 2026-10-15 | Junction (commander-evidence-missions.md): remove once commanderEvidence -Pmission=junction accepts Talon Two. |
| `battle.mapgen.cityLaneMarkings` | experiment | 2026-10-21 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.cityRoadEnds` | experiment | 2026-10-21 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.cityRoads` | experiment | 2026-10-21 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.citySidewalks` | experiment | 2026-10-21 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.compoundPreparedGround` | experiment | 2026-10-20 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.outdoorLandscape` | experiment | 2026-10-11 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.plateaus` | experiment | 2026-10-22 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.ruralLandUse` | experiment | 2026-10-22 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.settlementLots` | experiment | 2026-10-22 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mapgen.townFields` | experiment | 2026-10-22 | Mapgen pass (mapgen-nouns.md): remove each once the owner accepts the generated maps with it on. |
| `battle.mech.<axis>.<short id>` | tuning | 2026-10-18 | Balance control over one authored mech-weapon value (MechWeaponComponent.tuned). Kept while armorMatchup balances mech weapons; fold the readings into the data files and drop it when that pass closes. |
| `battle.mech.armorArcs` | control | 2026-10-19 | Standing control alongside battle.vehicle.armorArcs. Retire with it. |
| `battle.mech.armorFireOrder` | experiment | 2026-10-18 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.closeOnAntiArmorTie` | experiment | 2026-10-18 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.damageAwareTargeting` | experiment | 2026-10-18 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.duelRange` | experiment | 2026-10-18 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.heavyCannonBuildMass` | experiment | 2026-10-18 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.heavyCannonFlux` | experiment | 2026-10-20 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.locationalDamage` | experiment | 2026-10-16 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.missileVariants` | experiment | 2026-10-18 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.overwatchKeepsArc` | experiment | 2026-10-18 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mech.torsoRear` | experiment | 2026-10-20 | Mech doctrine/armour pass (mechs-nouns.md, heavy-equipment-progression.md): remove each once armorMatchup has been read against it. Numeric ones fold into constants. |
| `battle.mission.counterattack` | experiment | 2026-10-21 | Mission reactive defence and flow (garrisonables.md): remove each once commanderEvidence on the story missions accepts it. Its composed .triggered/.dutyShare/.dutyPressure knobs go with it. |
| `battle.mission.flow` | experiment | 2026-10-21 | Mission reactive defence and flow (garrisonables.md): remove each once commanderEvidence on the story missions accepts it. Its composed .triggered/.dutyShare/.dutyPressure knobs go with it. |
| `battle.mission.flow.forward` | experiment | 2026-10-21 | Mission reactive defence and flow (garrisonables.md): remove each once commanderEvidence on the story missions accepts it. Its composed .triggered/.dutyShare/.dutyPressure knobs go with it. |
| `battle.mission.reactiveDefence` | experiment | 2026-10-20 | Mission reactive defence and flow (garrisonables.md): remove each once commanderEvidence on the story missions accepts it. Its composed .triggered/.dutyShare/.dutyPressure knobs go with it. |
| `battle.party.places` | experiment | 2026-10-21 | As battle.mission.*. |
| `battle.pathfinding.asyncClearance` | config | 2026-09-26 | Permanent: determinism, as for battle.influence.async. |
| `battle.pathfinding.asyncDefendSite` | config | 2026-09-26 | Permanent: determinism, as for battle.influence.async. |
| `battle.pathfinding.asyncDefendTrack` | config | 2026-09-22 | Permanent: determinism, as for battle.influence.async. |
| `battle.pathfinding.asyncDefendTrack.queueCapacity` | config | 2026-09-22 | Permanent: the capacity of the async route queue. |
| `battle.pathfinding.asyncDefendTrack.workers` | config | 2026-09-22 | Permanent: the size of the async route worker pool. |
| `battle.pathfinding.casualtyRouteCost` | control | 2026-08-30 | Standing control: the worked example of a control run (killing-ground) in `snapshot-suites.md`. Retire it when the example moves to another switch. |
| `battle.pathfinding.clearanceFailureBackoff` | experiment | 2026-10-14 | Mech clearance routing (mechs-nouns.md, 2026-10-14): remove once profileConvoyRoute has been read against it. |
| `battle.pathfinding.fireRouteCost` | control | 2026-10-22 | Standing control: FireFearScene and FireHarmScene compare against the off arm. Fire-system session; retire with those loops. |
| `battle.pathfinding.shareExhaustedFlood` | experiment | 2026-10-14 | As clearanceFailureBackoff. |
| `battle.power.armorDropCooldownSeconds` | tuning | 2026-10-18 | Heavy-equipment pass: fold into a constant once the cooldown is accepted. |
| `battle.profile.commandFrameDetail` | config | 2026-09-27 | Permanent: opt-in profiling instrumentation, off in play. |
| `battle.profile.commandTopologyCpu` | config | 2026-09-27 | Permanent: opt-in profiling instrumentation, off in play. |
| `battle.profile.pathRequests` | config | 2026-08-24 | Permanent: opt-in profiling instrumentation, off in play. |
| `battle.render.cliffLook` | control | 2026-10-22 | renderEvidence control lever (`render-evidence.md`). Remove it with its fallback path once its renderEvidence reading has been accepted and nothing reads the off arm. |
| `battle.render.collectCulling` | control | 2026-09-03 | Standing control: the renderEvidence culled-vs-unculled pixel-identity check (`render-evidence.md`). |
| `battle.render.colonyThreat` | control | 2026-10-22 | renderEvidence control lever (`render-evidence.md`). Remove it with its fallback path once its renderEvidence reading has been accepted and nothing reads the off arm. |
| `battle.render.creep` | experiment | 2026-10-21 | Bloom/creep sessions: remove once the creep look is accepted. |
| `battle.render.creepPalette` | experiment | 2026-10-21 | Bloom/creep sessions: remove once the creep look is accepted. |
| `battle.render.deadAirHaze` | control | 2026-10-20 | renderEvidence control lever (`render-evidence.md`). Remove it with its fallback path once its renderEvidence reading has been accepted and nothing reads the off arm. |
| `battle.render.domeShader` | control | 2026-10-13 | renderEvidence control lever (`render-evidence.md`). Remove it with its fallback path once its renderEvidence reading has been accepted and nothing reads the off arm. |
| `battle.render.doodadAtlas` | experiment | 2026-10-23 | Doodad decoration atlas: remove it with the per-cut bind path once renderEvidence has read the atlas against its control. |
| `battle.render.fire` | control | 2026-10-22 | Standing control: fire render cost control for renderEvidence, as for battle.fire. |
| `battle.render.groundFeathering` | control | 2026-10-22 | renderEvidence control lever (`render-evidence.md`). Remove it with its fallback path once its renderEvidence reading has been accepted and nothing reads the off arm. |
| `battle.render.settledBodyMesh` | control | 2026-10-22 | renderEvidence control lever (`render-evidence.md`). Remove it with its fallback path once its renderEvidence reading has been accepted and nothing reads the off arm. |
| `battle.sallyDoors` | experiment | 2026-10-20 | Sally doors (garrisonables.md): remove once the closable-door fold is accepted in play. |
| `battle.sallyDoors.closable` | experiment | 2026-10-21 | Sally doors (garrisonables.md): remove once the closable-door fold is accepted in play. |
| `battle.sallyDoors.derived` | experiment | 2026-10-20 | Sally doors (garrisonables.md): remove once the closable-door fold is accepted in play. |
| `battle.separation.capsules` | experiment | 2026-10-21 | Continuous positions (continuous-positions-nouns.md): remove once capsule separation is accepted. |
| `battle.squad.prosecutionFallThrough` | control | 2026-09-01 | Standing control: the frozen-control loop of ProsecutionHoldScene runs the off arm and its verdict depends on it. Retire it with that loop. |
| `battle.squad.routeCommitNeedsFreshContact` | experiment | 2026-10-13 | Squad zone actions (2026-10-13): remove once the squad zone-action evidence has been read against it. |
| `battle.squad.transitSpacing` | experiment | 2026-10-11 | Conquest command (2026-10-11): remove once commanderEvidence -Pmission=conquest has been read against it. Its composed .cells/.maxHold knobs go with it. |
| `battle.story.reactiveDefence` | experiment | 2026-10-21 | As battle.mission.reactiveDefence. Its composed .share knob goes with it. |
| `battle.swarm.barricadeAssault` | experiment | 2026-10-22 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.broodSpires` | control | 2026-10-25 | Kept: the control for Harriers in the Rav Bloom missions; `commanderEvidence` reads a mission with and without its spires and broods (colony-breach-nouns.md, Brood spire). |
| `battle.swarm.creepOutposts` | experiment | 2026-10-21 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.creepRoofCollapse` | experiment | 2026-10-21 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.creepTrees` | experiment | 2026-10-22 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.flowField` | experiment | 2026-10-21 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.groundColonies` | experiment | 2026-10-21 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.growthStages` | experiment | 2026-10-21 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.localPathBudget` | experiment | 2026-10-22 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.pack` | experiment | 2026-10-21 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.packLeaders` | experiment | 2026-10-21 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.swarm.packSplit` | experiment | 2026-10-22 | Swarm/Bloom sessions: remove each once colony-breach evidence accepts it (colony-breach-nouns.md). |
| `battle.tail.replanCpu` | config | 2026-09-27 | Permanent: opt-in profileConquestTail instrumentation, off in play. |
| `battle.tender.fairThreat` | control | 2026-10-12 | Standing control: TenderBuildScene runs the off arm as its control loop. Retire it with that loop. |
| `battle.thunderRun.reactiveColumn` | experiment | 2026-10-15 | Thunder Run: remove once commanderEvidence -Pmission=thunder-run accepts the reactive column. |
| `battle.turret.crewFriendlyHold` | experiment | 2026-10-22 | Lane-discipline work: remove once lane discipline is accepted (ballistics-nouns.md). |
| `battle.unitUpdate.minimumParallelUnits` | config | 2026-08-24 | Permanent: determinism. Evidence tasks force the serial unit update with it. |
| `battle.unitUpdate.parallelism` | config | 2026-09-26 | Permanent: an explicit unit-worker count for profiling runs. |
| `battle.vehicle.activeProtection` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.armorArcs` | control | 2026-10-19 | Standing control: the armorMatchup facing-dependent armour question (`evidence-tasks.md`). Retire once facing is accepted in the vehicle balance pass. |
| `battle.vehicle.backingTurnDegrees` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.crossfire` | experiment | 2026-10-23 | Crossfire (convoy-nouns.md): keep the rule and drop the switch if, against it as the control, two Hounds in a pincer beat the single approach against the laser, breakthrough and screened tanks (32 seeds) while the default ladder's one-on-one rows hold within noise; remove the class otherwise. |
| `battle.vehicle.crushUndergrowth` | control | 2026-10-25 | Standing control for forest drives: off, every hull stops at undergrowth and lone boulders as before (convoy-nouns.md, heavy tracks). Retire once forest-mission evidence no longer runs it. |
| `battle.vehicle.crushYoungTrees` | experiment | 2026-10-25 | Awaiting the owner's pick of whether a heavy tank pushes over living young trees or only dead ones (convoy-nouns.md, heavy tracks); remove with the losing arm once picked. |
| `battle.vehicle.disarmedWithdraw` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.duelRange` | experiment | 2026-10-18 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.edgeClearance` | control | 2026-10-23 | Control for edge-aware vehicle clearance; remove once the edge rule has shipped and a later Cordon/Heart evidence pass ran without reaching for it. |
| `battle.vehicle.faceInBand` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.faceKnownOnly` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.faceWorstThreat` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.pivotAccuracy` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.orderPlansPerTick` | tuning | 2026-10-24 | Move-order route proofs per tick (`VehicleMoveOrderSystem`); 0 plans every queued order at once, the control. Fold into the constant once the convoy and story-mission evidence has been read against it. |
| `battle.vehicle.progressiveRecovery` | experiment | 2026-09-27 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.recoveryExpansionsPerTick` | experiment | 2026-09-27 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.rejectInvalidPlannerStart` | experiment | 2026-09-27 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.repositionStalled` | experiment | 2026-10-19 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.repulsion` | experiment | 2026-10-15 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.reuseFailedRecovery` | experiment | 2026-09-27 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.tankReverseFraction` | experiment | 2026-10-18 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.turretDamage` | experiment | 2026-10-18 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.turretHitShare` | experiment | 2026-10-18 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.vehicle.turretPoolShare` | experiment | 2026-10-18 | Vehicle sessions (convoy-nouns.md): remove each with its losing path once armorMatchup or the convoy evidence has been read against it. Numeric ones fold into constants. |
| `battle.warship.atmosphericHandling` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.atmosphericSpeedShare` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.atmosphericTurnShare` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.biteTargeting` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.clearLineFire` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.duelManoeuvres` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.edgePressure` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.escortClearance` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.finisherHull` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hardFlux` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hardpointSight` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hullContact` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hullDurability` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hullFightFooting` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hullPresence` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hullRange` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hullReachShare` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.hullTempo` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.inFlightCollision` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.layBetweenRounds` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.missileFlight` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.missileRestraint` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.missileWindows` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.packRelay` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.pdHullReach` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.pdThreatFirst` | experiment | 2026-10-19 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.plateSoak` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.platingGrid` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.pressing` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.shieldReserve` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.standoffLengths` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.tactics` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.threatTargeting` | experiment | 2026-10-18 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `battle.warship.unshieldedComeAbout` | experiment | 2026-10-16 | Warship tactics pass (warship-tactics.md, air-nouns.md): remove each with its losing path once the warship scenes have been read against it. Numeric ones fold into constants. |
| `marines.bloom.frequency` | config | 2026-10-22 | Permanent: the Rav Bloom frequency preset, as for severity. |
| `marines.bloom.playerColonies` | config | 2026-10-22 | Permanent: the Rav Bloom player-colony policy preset, as for severity. |
| `marines.bloom.severity` | config | 2026-10-22 | Permanent: the Rav Bloom severity preset, which bloomEvidence sweeps and live play reads as the control-run override. |
| `marines.debug.screenTrigger` | config | 2026-08-29 | Permanent while integral-system screens are tuned: a dev scaffold, announced on stderr when on, OFF in shipping. |
| `marines.dev` | config | 2026-10-21 | Permanent: the dev-build gate (DevConfig.DEV_MODE). runStarsector passes it and a player's install never has it. |
