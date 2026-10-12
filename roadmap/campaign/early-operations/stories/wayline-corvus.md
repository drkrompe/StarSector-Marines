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

The Onslaught enters nose-first from the east after proof and holds station
outside the compound's entire eastern footprint. It opens fire immediately when
the company clears the bombardment exclusion area and committed inbound
reinforcements resolve. There is no ten-second grace period and no wait for
shuttle boarding. Re-entry cancels further launches; inflight rounds remain real.
Actual deployment boats return and recover survivors while bombardment continues.
Victory also requires those pickup flights to return. The briefing and HUD
explain the extraction and immediate-fire conditions.

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

Earlier intervention verification: 67 tests, the recommended-force production replay and
`verifyModJarBoundary` pass. Retained evidence is in the local visualization
artifact `cold-account-intervention`, including launch fixture, trace, samples,
phase frames and summary.
- [ ] Player-controlled live acceptance, including overriding withdrawal and
  seeing fire hold, remains required before balance acceptance.

No new voice recording is required; mission instructions and countdown are
readable on the HUD. Any later narration uses the authored voice workflow.

## One-off Hegemony commander: narrative direction

The intervention should introduce a cold, ruthless Hegemony naval officer for
this chapter. Their voice treats the evidence as a jurisdictional threshold:
once pirate ownership is established, demolition is an administrative decision.
The officer believes disciplined military force restores order. Keep their
speech restrained, formal and brief; the violence of the guns carries the
threat. Mara remains the human counterpoint. This is a proposed character pass,
not dialogue or a portrait already wired into the battle.

Working concept: Captain Irena Kord, commanding the Onslaught HSS Resolute.
Proposed event lines:

- Evidence: "Authentication accepted. This installation is now designated a
  pirate military asset. Withdraw your personnel."
- Withdrawal: "Your company has completed its function. Clear the exclusion zone."
- All clear: "Friendly elements accounted for. Batteries: demolition pattern.
  Open fire."
- After the guns: "The installation is no longer operational. Record the matter
  closed."
- Mara: "Keep the original records. I want an account of what happened here."

The bombardment should feel deliberate and overwhelming, with a clear opening
salvo and sustained structural destruction. Ruthlessness concerns the treatment
of the pirate installation; it does not silently revoke the player's promised
withdrawal interlock. Any change to completion or the fire pattern needs its own
production replay. Portrait, subtitles, optional voice, and the final fire-pattern
presentation remain to be implemented and reviewed.

## Outskirts fire and survivor pickup — 2026-10-11

- [x] Correct approach heading and station the full capital hull outside the
  eastern installation bounds, with eight cells of additional clearance.
- [x] Use the explicit structural standoff range and open fire when ground
  troops clear the firing area, while pickup continues.
- [x] Reuse fitted deployment boats for capacity-limited survivor pickup, retain
  named personnel/telemetry, and resolve loaded-aircraft losses.
- [x] Repair site-security travel being abandoned for overwatch on distant
  remembered contact; the original replay's rear squad showed exactly that.
- [ ] Player-controlled acceptance and final difficulty tuning remain open.

Final merged-ship playback: victory at tick 7,160 (3:58.7). Evidence at 4,140;
fire at 6,159 with no grace interval and pickup still underway. Four six-seat
sorties physically recovered all 24 surviving infantry. Ground losses were
24/48 and the Hound, compared with 29/48 and the Hound in the previous baseline.
The capital fired 368 rounds, destroying 596/725 target wall cells; six pirates
remained. No infantry died during bombardment. Per-tick assertions prove the
full hull stays beyond the outermost wall, approach heading follows its straight
flight line, and fire precedes pickup completion. The original accessible-POI
bounds omitted outer structures; station placement now uses the original wall
footprint, which remains fixed after demolition. Twenty-three focused tests and
`verifyModJarBoundary` pass. The retained `cold-account-outskirts-pickup` artifact
includes the updated silent replay, fixture, trace, samples and unit snapshots.
