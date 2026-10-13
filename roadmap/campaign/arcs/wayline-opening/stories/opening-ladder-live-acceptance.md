# Opening operation live acceptance

Status: READY

Written: 2026-08-23

Updated: 2026-10-13 — Relief uses the starter company and western assembly apron; linked live pacing remains.

Read `early-operation-nouns.md` before accepting this story.

## Goal

Verify that the finite Wayline story arc gives a newly formed
company a readable, winnable introduction to First Contract work, using the
current twelve-marine squad and lift behavior. Story chapters retire on victory; separate Militia Relief and Depot Clearance
postings unlock after the arc. Company growth does not remove access to small work.

## Headless review boundary

`commanderEvidence -Pmission=relief` compares three production-factory maps
with ordinary, delayed, and absent player arrival. It records original militia
survival separately from employer reinforcements and supports 1920x1080 review
frames and optional exact replay checks. These verdant-surface cases use twelve
named starter recruits in Cordon shells with surplus Rooks, carried in three
four-seat sorties by one fitted standard Hermes. They report full-squad assembly,
first company fire/loss, and employer arrival at the gate. They exercise the
production roster overlay but do not establish live Jangala save, fog, or HUD
acceptance. Delayed cases are a controlled intervention, not a slow transport
model; extend the observation with `-PmaxTicks=12000` for longer fights.

Counterattack's corresponding harness uses three depot interiors with ordinary,
delayed and absent player arrival, plus two fitted boats arriving together.
Ordinary and delayed cases carry twelve marines in two sorties on one six-seat
boat; the paired control carries the same force on two boats. It records gate,
yard and warehouse vicinity, defensive orders, losses and terminal outcome.
Those older factory controls cover the repeatable elimination setup, not the
unique story defense. `commanderEvidence -Pmission=counterattack --tests
'*DepotHoldEvidenceTest'` exercises the story setup with twelve named company
marines, six employer militia, actual boat arrivals, and a Hound detour versus
ordinary advance. It records physical capture, both road attacks, casualties,
Hound and militia fire, friendly command reasons and positions, physical warehouse
presence, and terminal outcome. A 60-second delayed company boat and a 600-second
delay observed for 120 seconds check that militia wait without a ready company
and begin screening once the company assembles. These are controlled arrival
delays, not slow-airframe acceptance. Whole-map images are neutral observer views;
they do not validate live fog or GL HUD rendering.
The 300s absent-player observation does not establish a new failure rule.

## Remaining pacing checks

The expanded apron lets the twelve-person starter squad finish its three Hermes
sorties before entering combat in all three ordinary construction controls.
The longer 400-second controls still expose a late standoff in one ordinary
seed and several delayed runs. Inspect the surviving raider's contact/route
behavior and confirm live player advance can finish the fight; safe assembly
alone is not full mission balance acceptance. Absent-player controls must still
fail to clear the finite assault without inventing a timeout defeat rule.

## Acceptance

- Relief's first victory retires that chapter and opens Counterattack at Mara
  Venn's office. Its local line survives long enough for a slow
  player transport to matter, but cannot win unattended.
- Relief's defensive anchor reads as a communications/logistics place the local
  militia must preserve: a four-person exterior screen, four-person gate post,
  recessed relay, expanded western apron and
  connected access roads. Its finite road arrivals cannot become recurring supply.
- Employer lift waits for company assembly before reinforcing the gate; absent or delayed company arrival must not send it ahead alone.
- The first briefing clearly accepts one starting transport and one selected
  fire team; counterattack clearly communicates its larger player-lift demand.
- Live results justify the local-militia versus bandit ratio and approach
  pressure without adding regulars, mechs, turrets, fighters, or open-ended reinforcements.
- Defeat leaves the relevant operation retryable. Completing Relief unlocks
  Counterattack even if the company has grown or suffered losses. After
  victory, each story chapter stays closed. Completing the arc opens distinct
  recurring work at Venn's office and other Independent brokers.
- Squad assembly at the landing zone reads as deliberate rather than stalled
  while the exterior militia fights the opening raiders. The starter Hermes
  must finish all three sorties before the company is drawn into the gate fight.
- Counterattack's joint force advances from the apron through the gate and
  loading yard toward dispatch. The rear guard remains distinct from the
  reserve; contact and own casualties drive commitment and fallback. Confirm
  these beats with actual campaign fittings and mixed equipment, including a
  slow boat and two boats sharing the apron.
- Counterattack militia wait for the company's complete landing, screen its
  actual advance and Hound recovery detour, then join the warehouse defense.
  A delayed company must not leave militia assaulting the gate alone; loss of
  the company infantry can hand support to a surviving Hound. Accepted external
  orders retain ownership. Militia still take ordinary combat losses while holding.
- The story briefing explains eight seconds inside dispatch, two finite road
  attacks, and a final secured warehouse. A persistent recovery beacon stays
  after Castle's notice expires and points toward the berth when off camera.
  Recovery remains optional. Confirm the Hound is useful during the defense
  without requiring direct control or imposing a guaranteed casualty quota.
- The initial eight-person road attack and final eight-person road attack put
  pressure on a twelve-person company without late-game weapons. Losing and
  retaking dispatch never grants another wave; surviving mech-only forces can
  complete the hold after the infantry are lost.
- The two missions introduce preserve/secure command language consistent with
  `faction-ground-contract-policy.md` without adding objective mandates before
  that contract seam exists.

## Out of scope

A third pinned-assault or fabrication-cache recovery variant, new campaign
eligibility brackets, parts/schematic payout, changes to general operation-tier
policy, and any guarantee that changes how many Independent brokers offer the
first operation. `broken-supply-route.md` plans the follow-on alpha arc; its gameplay implementation
follows this evidence and requires an explicit small Conquest profile.
