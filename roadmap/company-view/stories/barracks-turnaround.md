# Barracks turnaround

Status: IN PROGRESS — personnel consolidation, replenishment and first-use guidance
Written: 2026-10-11
Updated: 2026-10-12 — equipment issue is independent of squad health and manning.

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
  squads are skipped and why. Wounded and understrength squads can receive kit;
  only occupied billets incur equipment charges, and recovery state is unchanged.
- Preserve selection when visiting a selected squad's Armory workspace. Expose
  the existing squad-founding route clearly from an empty Barracks; moving the
  founding transaction itself is not required for this bounded pickup.

## Castellan walkthrough

The company's pragmatic, matriarchal comms officer teaches the first company-management
loop in short, replayable steps: establish a formation; read the muster; choose
recipients and donors; review consolidation; fill vacancies; issue kit; return to
work. Advice names real controls and current blockers. It never advances gameplay,
spends resources, invents a casualty, or requires wasteful orders to progress.
Present each lesson as an anchored callout beside the real Barracks controls,
with Castle's portrait and speaker name beside her words. Highlight the next
control and move the callout clear of it. Dim the rest of the screen and declare
one or more interactive openings for each lesson. Keep squad scrolling, filtering
and paging available during selection; expose transfer preview and Apply/Cancel
together; expose each resource order with its costs and available stock. Pointer
and keyboard input share the same regions. Openings follow scrolling and resizing;
Dismiss restores the whole screen immediately. Successful orders advance the
guide; Next skips any unnecessary action without spending cargo. Escape and
Dismiss close it; Company Guide resumes the lesson sequence. All future speaking
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
The guide remains interactive at enlarged scale and reveals the highlighted order
within its scrolling pane when the target or viewport changes.

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

The reusable spotlight is covered by production MLX pointer/keyboard tests:
select a squad while Castle speaks, focus a highlighted region, reject a darkened
order, dismiss/resume, and keep equipment revealed and unobstructed after resizing.
Retained-UI coverage checks multiple/overlapping apertures, clipping, shade input
consumption, tab boundaries, stale capture/armed activation and immediate dismissal.
Guide progress follows successful orders; blocked orders stay on the current lesson.
Production snapshots cover selection, transfers, replenishment and equipment at
100% and 150% scale. Results retains the recovery conversation alongside preparation.

Spotlight verification: 155 focused retained-UI, Barracks and consolidation tests
passed; shadowJar packaged successfully and all 32 Barracks snapshots rendered.
