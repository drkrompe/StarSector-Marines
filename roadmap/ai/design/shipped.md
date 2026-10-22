# AI shipped ledger

Status: SHIPPED — folded legacy AI implementation stories.

Written: 2026-08-23

Updated: 2026-10-21 — folded party places and story garrisons; earlier, folded sally doors, reactive garrisons, the machine garrison assault, player reactive manning and the machine guard.

| Retired story | Ship date | Evidence | Fold destination |
|---|---|---|---|
| `party-places.md` | 2026-10-21 | `ca70645e8`, `83313925b` and the ship commit; `PlaceDirectivesTest`, `PartyPlacesTest`, `ThunderRunLocksTest`, `YardOwnersSitesTest`; scene `party-places` (11 verdicts); `commanderEvidence` on yard-owners, limited-warrant, thunder-run against `-Dbattle.party.places=false` | `garrisonables.md` (parties, place directives, locked positions, parties wired); `ai-nouns.md` (Sides); `mission-command-nouns.md`; `commander-evidence-missions.md` |
| `story-garrisons.md` | 2026-10-21 | this change; `StoryGarrisonsTest`, `MissionGarrisonsTest` (holder cap); scene `story-defence` (5 verdicts) and the `mission-defence`, `mission-envelopes`, `machine-guard`, `sally-door`, `place-counterattack`, `reactive-belt` scenes green; `commanderEvidence` for Counterattack, Junction, Relay Nine, Thunder Run, Relief and Last Lift against `-Dbattle.story.reactiveDefence=false`: beat lists identical on every fixture | `garrisonables.md` (story commanders; open questions) |
| `machine-guard.md` | 2026-10-20 | `aaaf0a627` and the ship commit; `MachineGuardOrderTest`, `MachineGuardDirectorTest`; the four-loop `machine-guard` scene (mech, tank, empty-building control, defender lances on a production fort: all verdicts pass; mech and tank guard 3 cells outside the building off its lanes, face the threat on every settled idle tick, fire at the approach, stand down 150 ticks after the garrison leaves; the defender attached 1 of 2 lances, its bound, on the pulse a run was manned) | `ai-nouns.md` (machine guard under fighting positions, mech tactical move); `garrisonables.md` (decided direction); `convoy-nouns.md`; `mechs-nouns.md` |
| `reactive-garrisons.md` | 2026-10-20 | `7e7585780`, `f970d49bd`, `b2df91e40`, `d23998ef6`, `1612a94b8`, `43d9ff227`, `039aeeaeb` and the fold commit; `reactive-belt` scene (quiet, passing attack, breach: all verdicts pass); steady rallies on the trial ground, 56 fixtures against a same-build control: rally swings back to the post just left 106 to 42, defender losses 2,269 to 2,195, marine losses level, defender holds 17 to 18; defender marches of 1,000 ticks or more account for about 1% of defender dead (28 of 2,269 in the control), not pursued | `garrisonables.md` (flow, assignment, steady rallies, open question on a stretch the attack has left); `ai-nouns.md` (belt manning); `conquest-nouns.md` (track rally) |
| `machine-garrison-assault.md` | 2026-10-20 | this change; `MachineAssaultOrderTest` and the four-loop `machine-assault` scene (mech, tank, empty-building control, mech with clear-and-hold infantry) | `ai-nouns.md` (machine assault under fighting positions, mech tactical move); `garrisonables.md`; `convoy-nouns.md`; `phased-assault.md` (wall fire) |
| `player-reactive-manning.md` | 2026-10-20 | `1994bd04c` and the fold commit; `ReactiveWatchTest`, `ReactiveManningServiceTest`, order-bar model and shortcut tests; the four-loop `player-reactive-manning` scene (opted-in, not-opted, explicit-order, breach: all verdicts pass; opted-in manned 74 ticks after the trench was believed pressed, released 209 ticks after it cooled, home 171 ticks later) | `ai-nouns.md` (GARRISON stance under fighting positions); `garrisonables.md` (the player's version, decided direction) |
| `fighting-position-occupation.md` | 2026-10-13 | occupy order and overlay, Conquest position counts, belt-post structure defence, belt-nest garrisons, destructible fort sandbags; fort control: 20 belt squads hold their trenches dug in | `ai-nouns.md` (occupy, belt garrisons); `mapgen-nouns.md`; `forts.md`; `conquest-nouns.md`; `moddable-tilesets-nouns.md`; fort fixture in `CLAUDE.md` |
| `fighting-positions.md` | 2026-10-13 | `bd9a60f7b`, `1031d77f8`, `22690feaa` and the fold commit; unit tests plus the five-loop `fighting-position` scene (composure isolation, fill, hold under live fire, overflow) | `ai-nouns.md` (fighting positions); `mapgen-nouns.md` (prepared ground) |
| `district-patrol-work-budget.md` | 2026-09-27 | `2372adc75`, `de08ea3bb`; 199 focused tests, independently controlled JFR pair and paced eight-mech replay; bounded work verified, not a general tail-latency guarantee | `ai-nouns.md` (optional district waypoints); independent controls and counters in `conquest-tail-profile.md` |
| `clearance-owner-isolation.md` | 2026-09-27 | `64a7d5de5`; JFR monitor evidence, 509 focused tests including real-coordinator monitor isolation and mech proof invalidation, 6000-tick paced eight-mech replay | Existing route ownership unchanged; immutable-identity cancellation boundary in `CLAUDE.md` |
| `patrol-tail-and-mech-coverage.md` | 2026-09-27 | `100dcfa3f`, `0d65eea5b`; 612 focused tests, 154 legacy-control mech tests, zero-input and paired eight-mech paced captures (6000 ticks each) | `ai-nouns.md` (optional quiet guard travel); existing mech connectivity/clearance authority unchanged; controls and coverage semantics in `conquest-tail-profile.md` |
| `local-hazard-avoidance.md` | 2026-09-27 | `0aae587bf`; 380 focused tests, same-input legacy control and 6000-tick paced capture | `ai-nouns.md` (imminent hazard avoidance and owned escape retention); controls and diagnostic semantics in `conquest-tail-profile.md` |
| `flank-selection-work-budget.md` | 2026-09-27 | `c10a51d38`; 335 focused tests and 6000-tick paced capture | `ai-nouns.md` (optional flank allowance and explicit refusal); controls and diagnostic semantics in `conquest-tail-profile.md` |
| `route-region-membership.md` | 2026-09-27 | `9334c7199`, `9cc18023e`; exact route/perception equivalence tests and production-paced tail captures | Existing `ai-nouns.md` route-admission and contact rules unchanged; controls and diagnostic semantics in `conquest-tail-profile.md` |
| `squad-route-work-budget.md` | 2026-09-27 | this change; tiny-budget progress, cancellation, fairness, backoff and immutable-publication tests | `ai-nouns.md` (shared route admission); controls and diagnostic semantics in `conquest-tail-profile.md` |
| `squad-route-admission.md` | 2026-09-27 | `9756d8ec2`, `d365b08f6`; exact pending/fairness/resumption tests and paired 16,000-tick capture | `ai-nouns.md` (shared route admission); diagnostics and control in `CLAUDE.md` |
| `bounded-contact-selection.md` | 2026-09-26 | this change | `ai-nouns.md` (knowledge-bounded enumeration); selector/publication Javadoc and profiler controls in `CLAUDE.md` |
| `phase-owned-worker-requests.md` | 2026-09-26 | this change | `ai-nouns.md` (route intent ownership and projectile observation); `ballistics-nouns.md` (joined launch publication) |
| `stable-execution-decisions.md` | 2026-09-26 | this change | `ai-nouns.md` (priority-bounded deliberation and persistent execution firing positions); profiler controls in `CLAUDE.md` |
| `spatial-planning-cost.md` | 2026-09-26 | this change; navigation parity, local-post unit coverage, and same-build paced controls | `ai-nouns.md` (capture-post locality); picker Javadoc and profiler controls in `CLAUDE.md` |
| `allied-faction.md` | 2026-09-02 | `9fa08a408`, `b8935445d`, `b7f6d6ae2` | `ai-nouns.md` (Sides), `fog-of-war-nouns.md` law 2, `battle-render-nouns.md`, `polity-ground-doctrine.md` |
| `01-world-state.md` | 2026-05-18 | `afa1a84b` | `ai-nouns.md` |
| `garrison-firing-reachability.md` | 2026-08-31 | Conquest matrix: both fixtures ~10% shorter, squad losses 10 -> 7 on `full-strength-west`, no idle introduced | `ai-nouns.md` (firing position reachability) |
| `02-interfaces.md` | 2026-05-18 | `afa1a84b` | `ai-nouns.md` |
| `03-planner.md` | 2026-05-18 | `9c770137` | `ai-nouns.md` |
| `04-world-state-builder.md` | 2026-05-18 | `0aa340a4` | `ai-nouns.md` |
| `05-suitability-scorer.md` | 2026-05-18 | `c36c3798` | `ai-nouns.md` |
| `06-parity-actions.md` | 2026-05-18 | `ab6e2fca` | `ai-nouns.md` |
| `07-parity-goal.md` | 2026-05-18 | `5705e798` | `ai-nouns.md` |
| `08-behavior-wiring.md` | 2026-05-18 | `892c4044` | `ai-nouns.md` |
| `09-parity-validation.md` | 2026-05-18 | `496bcd39` | `ai-nouns.md` |
| `11-stage2-foundation.md` | 2026-05-18 | `d89c1e8e`, `1460ba16`, `6d753cd4` | `ai-nouns.md` |
| `14-mech-stage1.md` | 2026-05-19 | `10582052`, `c1d6bc03`, `965ea98f`, `7936e66f`, `2b7a034d`, `2dc747e6` | `ai-nouns.md` |
| `16-assault-command.md` | 2026-05-27 | `670bc8fa`, `dc68a1f9` | `ai-nouns.md` |
| `17-garrison-zone-clear-scoping.md` | 2026-06-01 | `2b31af4e`, `87cf47c5`, `94e30605`, `4cebcb89`, `8e73d0d6`, `7fc24152`, `08ef31e6` | `ai-nouns.md` |
| `18-guardpost-area-patrol.md` | 2026-06-01 | `bf186b74`, `8ffe2c44`, `ce46a5fd`, `cc73dfb1` | `ai-nouns.md` |
| `19-threat-scored-engagement-leash.md` | 2026-08-19 | `8d33ca5b`, `14d646ab` | `ai-nouns.md` |
| `20-bounding-overwatch.md` | 2026-08-19 | `b72819fc` | `ai-nouns.md` |
| `21-last-stand-objective-camper.md` | 2026-08-19 | `ef76c7ba`, `780ea91f` | `ai-nouns.md` |
| `22-assault-mech-and-difficulty-roster.md` | 2026-08-19 | `1ef74f23` | `ai-nouns.md` |
| `23-mech-screened-advance.md` | 2026-08-19 | `0daf058e` | `ai-nouns.md` |
| `24-engagement-discipline.md` | 2026-08-19 | `43c619ff`, `8888e6f8` | `ai-nouns.md` |
| `25-direct-squad-belief.md` | 2026-08-19 | `db69ed73` | `ai-nouns.md` |
| `26-audio-contact-belief.md` | 2026-08-19 | `63ffdb6a` | `ai-nouns.md` |
| `27-commander-influence-substrate.md` | 2026-08-19 | `4e7089d0` | `ai-nouns.md` |
| `28-assault-mech-cohesion.md` | 2026-08-22 | `eaef38b2` | `ai-nouns.md` |
| `29-warden-screened-overwatch.md` | 2026-08-22 | `98fcc3fb` | `ai-nouns.md` |
| `30-mech-formation-discipline.md` | 2026-08-22 | `861a5bf1` | `ai-nouns.md` |
| `31-adaptive-squad-formations.md` | 2026-08-22 | `4c0864d9` | `ai-nouns.md` |
| `squad-tactical-move-order.md` | 2026-08-31 | this change | `ai-nouns.md` |
| `squad-contextual-capture-order.md` | 2026-08-31 | this change | `ai-nouns.md`, `conquest-nouns.md` |
| `tiled-navigation-derivations.md` | 2026-09-03 | `b00ba1430`, `08bf8ea23`, `7629478f1`, `b2d9c6e01` | `battle.nav` package charter (`package-info.java`), `convoy-nouns.md` |
| `attacker-holds-ground.md` | 2026-10-20 | `be349ff91`, this change | `garrisonables.md` (holding taken ground), `ai-nouns.md` |
| `garrison-stretches.md` | 2026-10-20 | this change | `garrisonables.md` (stretches, holding the stretch facing the threat, the trial-ground reading), `ai-nouns.md` |
| `mission-reactive-defence.md` | 2026-10-20 | this change | `garrisonables.md` (mission defenders), `ai-nouns.md`, `raid-command.md`, `assault-command.md`, `sabotage-command.md`, `extraction-command.md` |
| `sally-doors.md` | 2026-10-20 | session `fort-works-routes` commits ending in the merge to `main`; `SallyDoorSurveyTest`, `FortSallyPortPlanTest`; scenes `sally-door` (6 loops) and `sally-port` (5 loops) green; `HolderRouteEvidence`, `FortWorksRouteEvidence`; conquest-trial 76 fixtures and Relief, Counterattack, Junction against `-Dbattle.sallyDoors=false` | `garrisonables.md` (sally doors; marching late); `forts.md` (sally ports); `ai-nouns.md` (a holder goes out by its own sally door) |
| `closable-sally-doors.md` | 2026-10-21 | `b62fb57c5`, this change; `PassageCloseTest`, `SallyDoorRouteCrossingTest`; scenes `sally-door` (10 loops) and `sally-port` (7 loops) green; `simDeterminism` green; conquest-trial 40 combined-arms + 24 rifles-only fixtures against `-Dbattle.sallyDoors.closable=false` | `garrisonables.md` (sally doors: shutting, the player's bar, losing the place, forcing a shut door, shutting in play); `forts.md` (sally ports); `ai-nouns.md`; `mapgen-nouns.md`; `battle.nav` package charter |
| `mission-flow.md` | 2026-10-21 | `7d33b2b3d`, this change; `PlaceFlowTest`, `GarrisonFlowTest`, `MissionGarrisonsTest`; scene `mission-flow` green; `commanderEvidence` Raid, Assault, Sabotage, Extraction six seeds each against `-Dbattle.mission.flow=false` | `garrisonables.md` (flow, then local assignment: axis-free flow and the back/forward split; mission defenders: flow and its figures; open questions), `ai-nouns.md` |
