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
factories at Low, Medium and High risk. Sabotage's smaller force covers three
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
- [ ] Resolve the Dust Ledger service stall: command assigns the Hound to the
  target at (1,1), but it holds at (3.5,2.5), outside the objective's service
  radius. Infantry retain cordon assignments. The ten-minute trial loses 9/24
  infantry while the intact Hound kills 30 enemies; seizure never starts.
- [ ] Investigate A Cold Account's contact/search execution: all 48 infantry
  and the Hound land, but no shot is fired in ten minutes. Infantry route
  proofs to current search assignments exist; groups near the perimeter hold
  Engage/Overwatch while other search groups remain outside. This is not
  evidence of an easy encounter or proof of a disconnected map.
- [ ] Revisit Blind Watch's exposure and arrival pacing. All three charges
  complete at 2:27, but 31/36 infantry and the Hound are lost. First fire is
  at 8 seconds; the last marine arrives at 75 seconds. The four-boat control
  brings the last marine in at 68 seconds but times out with only one charge
  planted, 25/36 infantry lost and the Hound destroyed. More lift alone does
  not establish a safe baseline; this changes delivery composition as well as timing.
- [ ] After execution fixes, repeat the difficulty pass and verify with player
  intervention in a live battle before accepting the progression.

Reproduce with `commanderEvidence -Pmission=corvus -PmaxTicks=18000`.
The baseline uses Frontier Security Equipment and Frontier Patchwork Protection
for every squad, acting NCOs without officer bonuses, two standard Aeroshuttles,
and Wayward's recovered nose-chaingun fit with empty missile mounts. Each chapter
starts with a fresh full-strength company; this is not campaign casualty carryover.
The script calls the Hound at the first legal landing pad and otherwise leaves
command autonomous. Saved launch fixtures, command traces, ten-second samples,
minute frames and unit/route diagnostics accompany the report. A timeout is
unresolved, not a defeat, and the evidence task succeeding does not certify balance.

No new voice recording is required for this implementation; all correspondence
is readable text. Any later narration uses the existing authored voice workflow.
