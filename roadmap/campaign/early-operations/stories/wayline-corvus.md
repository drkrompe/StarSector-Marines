# The Corvus Account

Status: IN PROGRESS — combat playback exposes objective execution stalls and excessive sabotage losses.
Written: 2026-10-11
Updated: 2026-10-11 — recommended-force playback completed; balance acceptance remains blocked by the findings below.

Read `early-operation-nouns.md` for the enduring model, `mission-tier-nouns.md`
for force scale, and `campaign-battle-bridge-nouns.md` for target authority.

## Scope

Mara Venn follows Sable Wake's sale of Wayline sailing schedules across Corvus.
The Dust Ledger raids a counting house on Asharu; Blind Watch demolishes three
installations at a temporary Warion listening post; A Cold Account clears a
dispatch compound on Garnir. These are authored jobs against one pirate crew.
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

## Proposed Hegemony intervention

The user proposed a Hegemony capital ship intervention once the installation is
proven pirate. The shipped Onslaught station/hold-fire/area-fire/budget seams
in `warship-on-station.md` support this direction. A Cold
Account is the natural culmination: earlier chapters supply evidence, ground
forces confirm the target, and the ship receives explicit authority after the
company clears the bombardment area. Arrival, identification, warning, safe
withdrawal and objective credit need one authored mission contract before wiring
this in. Friendly fire remains real. This is planned content, not a ship already
present in the current combat trials or a substitute for repairing command stalls.

No new voice recording is required for this implementation; all correspondence
is readable text. Any later narration uses the existing authored voice workflow.
