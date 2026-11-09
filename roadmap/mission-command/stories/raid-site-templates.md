# Raid: a site per industry

Status: IN PROGRESS. Heavy industry is accepted (owner: roll it out); mining is built and its cards sent; the other industries remain.

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
- [ ] A site for every industry a raid can name.

## Constraints

- Raid only: Assault, Sabotage, Conquest and story maps keep their maps.
- No mission runs; rules are proven by unit tests.

## Exit

Fold the site laws into `raid-mission.md` and `precincts.md`, add a row to
`shipped.md`, delete this story.
