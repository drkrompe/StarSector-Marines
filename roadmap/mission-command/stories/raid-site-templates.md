# Raid: a site per industry

Status: IN PROGRESS. Heavy industry is accepted (owner: roll it out); every other industry's site is built and its cards sent, awaiting verdicts.

Written: 2026-11-09

Read `raid-mission.md` (Site templates, Districts and compounds) and
`precincts.md` first.

## Intent

The owner picked an authored Raid site per industry (`raid-mapgen`). A Raid on
an industry lays that industry's site: the landing in the homes, an industrial
quarter, the works compound walled by security with its buffer of open ground,
and the garrison beside the works. The seed and the market vary it.

## Scope

1. **Heavy industry, end to end.** `RaidSiteTemplate` for `heavyindustry` and
   `orbitalworks`, laid through `PrecinctPlan.laidOut`. The works is a square
   walled place (`FortressProgram.works`) whose glacis follows security
   (`Fortification.withGlacis`). The raid's machines are its halls
   (`RaidWorksLayout.compoundHalls`). The industry id travels from the launch
   to `BattleSetup.createRaidWorks`.
2. **Preview.** Look-preset cards across two seeds and both market sizes
   (`createSnapshots -Psnapshot=raid-site-look`).
3. **The other industries.** One site each, with the works program and
   districts the industry needs. Candidates: mining and techmining (a pit and
   its head works), refining and fuel production (tank farm and cracking
   plant), light industry, farming and aquaculture (silos, packing sheds,
   fields), spaceport and megaport (the port district's cargo terminal), and
   military base and patrol HQ (the garrison is the prize). Each new site is
   shown on the picks page before the next.
4. **Steal on a site.** The port's bonded store and the government's vault as
   compounds in their districts, for a raid for valuables (`raid-mission.md`,
   Steal).

## The planet's chokepoint

The owner (2026-11-09, on the mining site): "We're on scale of planets here".
A Raid on an industry must read as a blow to the whole planet's industry, so
every site is the one place that industry cannot route around: the hub the
rest of the planet's sites feed. Three things carry that.

- **The site is the hub.** The map shows one works, but its roads say that
  more feeds it: feeder lines leave the works' far gates and run off the map's
  edge (`RaidSiteTemplate.FEEDER`). A haul road carries its output from the
  near gate to its industrial district (`RaidSiteTemplate.HAUL_ROAD`). The
  scale tells are the works' own: stockpiles, apron and yards sized for a
  planet's throughput, not a town's.
- **The briefing says so.** The briefing names what the works does for the
  planet, how many sites feed it, and the days its loss costs (the days the
  wreck-the-works rule already computes), in a line such as "this concentrator
  handles the output of N digs; wreck it and the planet's ore stops for X
  days". N follows the market's size.
- **Each industry has its own hub and its own tells:**

| Industry | The hub | Tells on the map |
|---|---|---|
| Heavy industry | the planet's assembly works, which every foundry ships into | feeder lines in; machine halls; yards beyond |
| Mining | the central concentrator, which every dig feeds | the open pit beside the head works (`RaidSiteTemplate.PIT`); ore lines in from the other digs; ore stockpiles |
| Refining | the only cracking and distillation plant | pipelines in; stills; a tank farm |
| Light industry | the central fabrication works | feeder lines; the halls in process order; the parts line between them; the feeders' trucks at the dock; dispatch sheds by the haul road |
| Farming | the central grain elevator and processing hub | farm roads in; silos; packing sheds |
| Spaceport | the only orbital lift | the lighter apron; bonded sheds; roads in from the planet's towns |
| Military | the base the planet's patrols are supplied from | motor pool; magazine; roads out to the patrol posts |

**The pit.** A mine holds its pit's ground beside the head works: a held
ground site on the plan's mission layout (`plan.layout().find(PIT)`,
`SitePurpose.GROUND`). Nothing grows on that ground before the stage that
cuts the pit runs. It runs from just past the works' claim
(`RaidSiteTemplate.PIT_GAP`) to the deep edge. Across the map it reaches
`PIT_HALF_SPAN` either side of the works, but never nearer the middle than
`PIT_INNER`, because ground held near the garrison can starve the works of its
road.

## Acceptance

- [x] Unit tests: security from the market; the works' wall and buffer graded
  by security and capped by the operation; the works is the objective and holds
  one hall per machine; districts in every orientation (homes between the
  landing and the deep end, the industrial quarter between the homes and the
  works, the works and the garrison on opposite flanks); the seed mirrors both
  ways and repeats; the market grows the town; the compound's halls
  (`RaidSiteTemplateTest`, `RaidWorksLayoutTest`).
- [x] `storyMapPools` holds, since story maps are untouched.
- [x] Heavy-industry preview cards, two seeds by two market sizes, sent to the
  parent.
- [x] The owner's verdict on the heavy-industry site: roll it out (2026-11-09). The
  garrison district and ground that matches the planet are a separate session's.
- [x] Mining (`mining`, `techmining`): the head works, unit-tested, cards sent
  (`createSnapshots -Psnapshot=raid-mine-site-look`).
- [x] A site for every industry a raid can name: refining, light industry,
  farming, spaceport and military too (`raid-<industry>-site-look` suites).
- [ ] The owner's verdict on each, with fixes folded in. Light industry rebuilt
  as its process after the owner rejected the generic shops (`FabricationWorks`,
  `FabricationYard`; `createSnapshots -Psnapshot=raid-light-industry-site-look`).

## Constraints

- Raid only: Assault, Sabotage, Conquest and story maps keep their maps.
- No mission runs; rules are proven by unit tests.

## Exit

Fold the site laws into `raid-mission.md` and `precincts.md`, add a row to
`shipped.md`, delete this story.
