# Barracks turnaround

Status: IN PROGRESS — personnel consolidation, replenishment and first-use guidance
Written: 2026-10-11

Read `company-view-nouns.md`, `personnel-nouns.md` and `ui-nouns.md` first.

## Outcome

After settling a mission, the player can prepare the company for another operation
from Barracks: see ready, recovering and vacant billets; consolidate survivors;
fill remaining vacancies; then restore assigned equipment. Armory remains the
detailed equipment authoring destination. `company-resource-bills.md` separately
standardizes bills across rooms.

## Scope and laws

- Give mission Results/salvage an explicit preparation route after settlement,
  retaining the ordinary mission-list exit and exactly-once salvage handling.
- Put personnel preparation ahead of officer/profile administration. Separate
  receiving and donor squad selections, show every affected squad's projected
  ready/recovering/vacant counts, and require an explicit Apply Transfers.
- Consolidation draws ACTIVE personnel only from explicitly selected donor
  formations, in stable roster order, into true vacancies of selected recipients.
  It spends no cargo, never draws from unselected formations, and preserves named
  identity, kit, careers, squad records, desired profiles and home commands.
- WIA retain their billets. KIA/MIA history is neither transferred nor counted
  as living strength. Stationed squads cannot give or receive personnel. Empty
  donor formations survive for later rebuilding. Revalidate the preview at commit;
  changed personnel or postings require a new review rather than different transfers.
- Replenishment remains a separate explicit order: ready named reserves first,
  then cargo Marines, with exact available draw and remaining vacancies disclosed.
  Filling vacancies does not claim full combat readiness while WIA remain.
- Equipment issue remains separate and cargo-backed. Explain which selected
  squads are skipped and why; keep the existing full-ready-squad issue boundary.
- Preserve selection when visiting a selected squad's Armory workspace. Expose
  the existing squad-founding route clearly from an empty Barracks; moving the
  founding transaction itself is not required for this bounded pickup.

## Castellan walkthrough

The company's pragmatic, matriarchal comms officer teaches the first company-management
loop in short, replayable steps: establish a formation; read the muster; choose
recipients and donors; review consolidation; fill vacancies; issue kit; return to
work. Advice names real controls and current blockers. It never advances gameplay,
spends resources, invents a casualty, or requires wasteful orders to progress.
Present each lesson as a pop-over modal above the Barracks, with Castle's
portrait and speaker name beside her words. Dim the background and confine
keyboard/pointer input to the tutorial. Escape and Close / Try It return control
to the room; Company Guide reopens the lesson sequence. All future speaking
characters require their portrait alongside dialogue.
Her delivery is warm, observant and firm as the situation needs; dry humor is
occasional, never her default personality. Dismissal persists with the company; Help can reopen it. Existing saves receive
the same dismissible introduction. Text is complete without audio.

Author stable beat IDs and clean subtitles separately from synthesis direction.
Reuse the confirmed existing company-officer cast, with short offline ElevenLabs
takes, replay/stop and no overlap on navigation. Paid recording and audition are
separate acceptance from the text walkthrough; ordinary builds never call an API.
Coordinate only the management handoff with `opening-ladder-live-acceptance.md`;
the mission/tutorial session continues to own battle teaching and opening content.

## Acceptance

1. Two six-ready squads consolidate to twelve/zero without changing total named
   personnel, equipment, histories or cargo. Cancel changes nothing.
2. Mixed WIA/KIA/MIA and stationed cases preserve billet and posting laws;
   stale plans cannot partially move personnel. Repeating Apply cannot replay a plan.
3. Limited reserves/cargo produce an honest partial refill and explicit shortfall;
   a squad with eight ready and four WIA has zero replaceable vacancies.
4. Selected equipment issue explains all skipped squads and the combined bill.
5. Results with/without salvage and Retreat reach preparation without forfeiting
   an unreviewed claim or settling twice. The player can return to local work.
6. First-use guide, dismissal, replay and save compatibility are checked. No
   recording or sound device is required to understand or complete preparation.
7. Production MLX snapshots show the ordinary board, transfer preview, shortages,
   empty company and guide at 1920x1080 / 100%; scaled layouts retain scrolling.
8. Live campaign acceptance covers casualty settlement, preparation, Armory round
   trip, save/reload and another deployment. Record any unperformed acceptance
   honestly; headless snapshots do not establish live host behavior.

## Implementation order

Roster-owned transfer planning and atomic commitment; focused roster tests;
Barracks preparation controls and preview; mission and equipment navigation;
castellan text guide and recording script; deterministic visual evidence; live
campaign/voice acceptance. Keep this story open until remaining acceptance is done.

## Evidence and remaining acceptance - 2026-10-11

Implemented consolidation, explicit preparation orders, Results/salvage handoff,
selected-squad Armory navigation, persistent guide dismissal and six text lessons.
The existing company-officer voice is configured in `company_management_guide.json`;
`generate_wayline_narration.py --cast company-management` dry-runs all six beats.
No new management recordings were generated or auditioned. Playback/replay/stop
for those future clips remains open; guide step navigation currently presents text.

Focused verification: `:test` with `*SquadConsolidationTest`, `*Barracks*Test`,
`*MarinePersonnelLogisticsTest`, `*ResultsScreenPersonnelPresentationTest`, and
`*LootSettlementServiceTest`. Production `createSnapshots -Psnapshot=barracks-command`
includes transfer reviews, the guide, empty/large companies, a pointer-driven
refill/issue flow and Results preparation; guide and transfers also cover 150%.
The guide is dismissible to restore the formation workspace at enlarged scale.

The complete `build` compiled and packaged the mod but its 6,941-test run had 13
failures. Each failing case was reproduced in an unchanged `cef164b21` checkout:
`ConvoyMeansTest` (one), `PrecinctPlanForMissionTest` (one),
`NoUnseededRandomTest` (one), `PrecinctLandingAreaStageTest` (one),
`MapLayoutTest` (three), `PrecinctOneKeepTest` (one),
`PrecinctPlanIsPerCallTest` (one), `ShipDeckBattleSceneTest` (one),
`SnapshotDiscoveryTest` (one), `ShotFxTest` (one), and `ProjectTilesetToolsTest`
(one). They are pre-existing failures, not a green full-suite claim.

Live casualty settlement, Armory round trip, save/reload and another deployment
remain unperformed. Keep the story open for that host acceptance and voice work.

After merging the concurrent Castle debrief work, 33 focused tests passed,
including `HoundSalvageDebriefTest` and `NarrationSequenceTest`; `shadowJar` and
all 26 Barracks snapshots succeeded. Results retains the voiced recovery
conversation alongside the new preparation action.

The tutorial now uses Castle's existing portrait in a centered pop-over above a
dimmed, unchanged Barracks layout. The shared document input scope prevents
background clicks, focus, captured drags and scroll while open; Escape dismisses,
and Company Guide reopens it. The 145 retained-UI and Barracks tests pass,
including the production MLX pointer/keyboard flow and input-scope regression
cases. The mod packages successfully. All 26 Barracks snapshots render; the
portrait modal was visually checked at 100% and 150% scale.
