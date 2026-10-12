# The Corvus Account

Status: IN PROGRESS — execution repaired; intervention implemented; live balance acceptance remains.
Written: 2026-10-11
Updated: 2026-10-11 — recommended-force playback completed; balance acceptance remains blocked by the findings below.

Read `early-operation-nouns.md` for the enduring model, `mission-tier-nouns.md`
for force scale, and `campaign-battle-bridge-nouns.md` for target authority.

## Scope

Mara Venn follows Sable Wake's sale of Wayline sailing schedules across Corvus.
The Dust Ledger raids a counting house on Asharu; Blind Watch demolishes three
installations at a temporary Warion listening post; A Cold Account confirms
pirate evidence and withdraws before Hegemony bombardment on Garnir. These are authored jobs against one pirate crew.
Neither Asharu's government, Garnir's orbital depot nor the abandoned
terraforming platform becomes an enemy asset or a campaign conquest.

The chapters recommend two, three and four squads, with ordinary Established
factories at Low, Low and High risk. Sabotage's smaller force covers three
separate sites; increasing difficulty is not a promise of monotonically increasing
headcount. The company owns its personnel, boats and mech lance. There are no
free replacement troops or chassis.

## Acceptance

- [x] Both closed supply-route branches, including exhausted failures, unlock
  the arc without rewriting their historical outcomes or recurring work.
- [x] Fixed planet identities, ordered chapters, retries and a persistent
  accepted-attempt receipt gate payment and casualty writeback.
- [x] HQ course guidance and the local broker expose the current destination;
  an unpopulated story moon has a Marine operations entry.
- [x] Pure progression, settlement, destination and exact ferry-seat checks.
- [x] Inspect production chapter maps, named deployment and broker/HQ renders
  with `createSnapshots -Psnapshot=wayline-corvus`.
- [ ] In a live campaign, travel Jangala → Asharu → Warion → Garnir; verify the
  planetary interaction menus, missing-destination behavior and save/reload.
- [x] Run each chapter with its recommended company and recovered owned Hound
  through production construction, campaign equipment/organization overlay,
  normal support activation and physical delivery.
- [x] Dust Ledger now seizes and withdraws: 4,222 ticks (2:21), 1/24 infantry
  lost, with the recovered Hound. Mech servicing holds the exact interaction
  cell; service/withdrawal do not discard their order on remembered contact.
- [x] A Cold Account now enters combat and resolves. Unfinished search legs
  survive directive stability expiry, contact cannot erase the search order,
  and gate approaches are guaranteed after optional road pruning. The earlier
  routes to outdoor search waypoints did not prove access inside the base.
- [ ] Tune A Cold Account: the unchanged High-risk force defeats all 48 infantry
  and the Hound at 9,202 ticks (5:07). Resolution is fixed; balance is not accepted.
- [x] Blind Watch uses its authored remote layout, a finite light garrison and
  Low risk. Both charge handoff and active-kit selection are repaired. With
  two boats it wins at 4,230 ticks (2:21), losing 12/36 infantry with the Hound
  surviving, compared with 31/36 and the Hound previously. Four boats now win
  at 4,333 ticks (2:24), losing 15/36 with the Hound surviving. More lift changes
  delivery composition as well as timing and is not automatically safer.
- [ ] Blind Watch still needs a player-controlled acceptance pass: autonomous
  losses of one-third to two-fifths of the company are costly. Do not describe
  this headless baseline as final balance acceptance.
- [x] Repeat all three recommended-force playbacks after the execution fixes.
- [ ] Verify with player intervention in a live battle before accepting the progression.
- [ ] Broader map access: the existing `aPrecinctMapIsNeverSealed` check fails
  for Assault/Established seed 141418, including with the pre-change gate
  stage restored. The Corvus gate repair does not resolve every generated
  map's access defect; investigate that separate case before general acceptance.

Reproduce with `commanderEvidence -Pmission=corvus -PmaxTicks=18000`.
The baseline uses Frontier Security Equipment and Frontier Patchwork Protection
for every squad, acting NCOs without officer bonuses, two standard Aeroshuttles,
and Wayward's recovered nose-chaingun fit with empty missile mounts. Each chapter
starts with a fresh full-strength company; this is not campaign casualty carryover.
The script calls the Hound at the first legal landing pad and otherwise leaves
command autonomous. Saved launch fixtures, command traces, ten-second samples,
minute frames and unit/route diagnostics accompany the report. A timeout is
unresolved, not a defeat, and the evidence task succeeding does not certify balance.

## Hegemony intervention

A Cold Account now uses an explicit intervention fixture around the fortified
Assault map. The near dispatch office holds pirate evidence; the company secures
it through the ordinary contested service objective and withdraws through Raid
command, respecting player orders. The installation footprint is the structural
target. No reinforcement layer is installed for this finite authored encounter.

The Onslaught enters after proof and holds fire until every surviving company
unit is inside the marked withdrawal area, all committed lift is resolved, and
the ship is on station. A continuous ten-second warning gates fire. Re-entry
cancels unlaunched burst rounds and restarts clearance. Existing projectiles
remain real. The objective HUD, briefing survey and ground markers show the
records, withdrawal area, exclusion area and current instruction.

The ship targets standing walls with its actual vanilla guns, bounded ordnance
and station time. At least 75% of original target wall cells must fall, shells
must finish landing, and the survivors must remain clear before victory. No
pirate deletion or elapsed-time success is used. Normal chapter settlement and
casualty writeback remain the campaign boundary.

- [x] First production playback proves evidence, whole-company withdrawal,
  warning and physical bombardment, with no premature ground shots.
- [x] Final replay against the merged ship combat: Marine victory at 9,587 ticks
  (5:19.6), 29/48 infantry lost, Hound destroyed at 2,589 ticks before evidence.
  Proof at 5,764; all survivors clear at 8,267; fire at 8,567. All 423 rounds
  followed clearance and the ten-second warning. Bombardment destroyed 557/725
  target wall cells; all 19 withdrawn infantry survived. Five pirates remained.
- [ ] Ground losses remain severe (60% and the recovered Hound). The intervention
  resolves the mission but is not final balance acceptance; revisit the approach
  and records position with player-controlled evidence.

Focused verification: 67 tests, the recommended-force production replay and
`verifyModJarBoundary` pass. Retained evidence is in the local visualization
artifact `cold-account-intervention`, including launch fixture, trace, samples,
phase frames and summary.
- [ ] Player-controlled live acceptance, including overriding withdrawal and
  seeing fire hold, remains required before balance acceptance.

No new voice recording is required; mission instructions and countdown are
readable on the HUD. Any later narration uses the authored voice workflow.
