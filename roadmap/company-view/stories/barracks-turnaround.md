# Barracks turnaround

Status: IN PROGRESS — replenishment and first-use guidance
Written: 2026-10-11
Updated: 2026-10-22 — no equipment order: replacements take their seat's kit from the squad's cards.

Read `company-view-nouns.md`, `personnel-nouns.md` and `ui-nouns.md` first.

## Outcome

After settling a mission, the player can prepare the company for another operation
from Barracks: see ready, recovering and vacant billets and when the wounded
return; fill vacancies. Replacements take their seat's kit from the squad's
cards, so there is no equipment step. Armory remains the detailed equipment
authoring destination. `company-view-nouns.md` (Company resource bills) separately
standardizes bills across rooms.

## Scope and laws

- Give mission Results/salvage an explicit preparation route after settlement,
  retaining the ordinary mission-list exit and exactly-once salvage handling.
- Put personnel preparation ahead of officer administration. There is no
  marine transfer between squads: a short squad waits for its WIA (one day of
  ship time, counted down in the Barracks header) or fills true vacancies.
- WIA retain their billets. KIA/MIA history is not counted as living strength.
- Replenishment remains a separate explicit order: ready named reserves first,
  then cargo Marines, with exact available draw and remaining vacancies disclosed.
  Filling vacancies does not claim full combat readiness while WIA remain.
- A squad's kit is its cards (`company-view-nouns.md`, Seat kit). Replacements,
  wounded and understrength squads wear their seats' kit with no order, no material
  charge and no change to recovery state.
- Preserve selection when visiting a selected squad's Armory workspace. Expose
  the existing squad-founding route clearly from an empty Barracks; moving the
  founding transaction itself is not required for this bounded pickup.

## Castellan walkthrough

The company's pragmatic, matriarchal comms officer teaches the first company-management
loop in short, replayable steps: establish a formation; read the muster and the
heal timer; fill vacancies; return to work. Advice names real controls and current blockers. It never advances gameplay,
spends resources, invents a casualty, or requires wasteful orders to progress.
Present each lesson as an anchored callout beside the real Barracks controls,
with Castle's portrait and speaker name beside her words. Highlight the next
control and move the callout clear of it. Dim the rest of the screen and declare
one or more interactive openings for each lesson. Keep squad scrolling, filtering
and paging available during selection; expose each resource order with its costs and available stock. Pointer
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

1. Mixed WIA/KIA/MIA and stationed cases preserve billet and posting laws.
2. Limited reserves/cargo produce an honest partial refill and explicit shortfall;
   a squad with eight ready and four WIA has zero replaceable vacancies.
3. Replacements wear their seats' kit as they join, with no separate order.
4. Results with/without salvage and Retreat reach preparation without forfeiting
   an unreviewed claim or settling twice. The player can return to local work.
5. First-use guide, dismissal, replay and save compatibility are checked. No
   recording or sound device is required to understand or complete preparation.
6. Production MLX snapshots show the ordinary board, shortages,
   empty company and guide at 1920x1080 / 100%; scaled layouts retain scrolling.
7. Live campaign acceptance covers casualty settlement, preparation, Armory round
   trip, save/reload and another deployment. Record any unperformed acceptance
   honestly; headless snapshots do not establish live host behavior.

## Implementation order

Barracks preparation controls; mission and equipment navigation;
castellan text guide and recording script; deterministic visual evidence; live
campaign/voice acceptance. Keep this story open until remaining acceptance is done.

## Evidence and remaining acceptance - 2026-10-11

Implemented explicit preparation orders, Results/salvage handoff,
selected-squad Armory navigation, persistent guide dismissal and five text lessons.
The existing company-officer voice is configured in `company_management_guide.json`;
`generate_wayline_narration.py --cast company-management` dry-runs all four beats.
No new management recordings were generated or auditioned. Playback/replay/stop
for those future clips remains open; guide step navigation currently presents text.

Focused verification: `:test` with `*Barracks*Test`,
`*MarinePersonnelLogisticsTest`, `*ResultsScreenPersonnelPresentationTest`, and
`*LootSettlementServiceTest`. Production `createSnapshots -Psnapshot=barracks-command`
includes the guide, empty/large companies, a pointer-driven refill flow
and Results preparation; the guide also covers 150%.
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
Production snapshots cover selection and replenishment at
100% and 150% scale. Results retains the recovery conversation alongside preparation.

Spotlight verification: 155 focused retained-UI and Barracks tests
passed; shadowJar packaged successfully and all 32 Barracks snapshots rendered.
