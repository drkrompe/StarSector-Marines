# First mission preparation

Status: IMPLEMENTED - live campaign acceptance open
Written: 2026-10-11
Updated: 2026-10-13 - follow-on upkeep uses campaign-day recovery; retained walkthrough and cargo preflight verified, with live campaign acceptance remaining.

## Outcome

Castle leads a new company through choosing its mercenary ship, commissioning
its first shuttle, meeting Mira and choosing her specialty, founding a fully
equipped twelve-person squad, and assigning that squad to Mira. The completed
sequence hands off to Mara Venn's first story mission, Hold Until Relieved, on
Jangala through the existing accessible-office policy.

## Model and scope

`company-view-nouns.md` owns preparation; `early-operation-nouns.md` owns the
mission and its destination. Extend the saved officer induction rather than
creating a competing tutorial ledger. New companies start with empty boat
berths. Existing boats and completed or legacy inductions remain intact.
Moving ships must not manufacture free replacement boats.

Ship confirmation requires a hull with quarters and a boat berth, plus the
combined remaining cargo bill for one compatible boat and one full squad.
One boat can make repeated trips. The bill comes from the existing fabrication
and founding catalogs, combines shared supplies, and is checked again at commit.
Selection spends nothing; each real workshop order consumes its own bill.
Mira retains her ordinary appointment point and needs no invented recruitment fee.

Castle uses a portrait, compact controls, SVG milestones and live click-through
highlights. Actual state advances the lesson. There is no Later control, and Escape
cannot dismiss the required lesson. Return permits travel/resupply; re-entering
restores the unfinished lesson. Changing rooms and reloading cannot complete it.
Armory includes the real founding action and a return to assignment. Completion routes to the existing briefing or HQ's actual story lead.

## Acceptance

- A commissioned shuttle appears in its berth during the same open Boat Deck
  session. Shipboard asset loading includes aircraft hulls before renderer setup;
  empty berths stay empty. Evidence includes a converted Cerberus with a Hermes.
- Selecting the company hull unlocks its available room routes in the already-open
  UI once preparation finishes. No close, refresh or second designation is needed;
  missing rooms remain unavailable and changing hulls updates the same controls.
- Exact and insufficient cargo; combined shared-commodity requirements; no charge
  or designation on failed preparation preflight.
- Paid first boat, no transfer refill, preserved legacy boats and saved progress.
- Real specialty, fully ready squad and home assignment required in order.
- Ship, Boat Deck, Barracks and Armory tutorial presentation at 100% and 150%.
- First mission handoff retains the selected briefing or provides the real
  Jangala course/contact route; no automatic acceptance or launch.
- Live campaign save/reload and end-to-end first mission acceptance remain explicit.

## Remaining acceptance

The implementation passes 42 focused preparation, induction and boat tests.
The retained UI evidence exercises real controls across the preparation rooms
and produces 75 frames at 100% and 150%; packaging and the mod/tool boundary
check also pass. This evidence does not replace a live campaign save/reload,
first-contract handoff or mission launch. Those checks remain open, along with
recording Castle's new lines using the existing company-officer voice.

The live-navigation fix uses click-time room checks and updates the existing room
buttons each frame, replacing the ineffective one-time screen reattachment. Five
focused tests cover the retained no-ship/preparing/ready transition, all four room
routes, keyboard activation, a later missing bay, and existing preparation guards.
The mod package boundary check passes. Live campaign acceptance remains open.

Aircraft visibility validation covers the production company-deck asset loader,
including Hermes and repeated room reads without texture reloads. The corrected
preparation fixtures adopt the commissioned scene. Eight Cerberus frames (including
empty/commissioned Hermes berths) and 77 preparation/Barracks frames render; the mod
package boundary passes. This does not replace live game acceptance.

## Follow-on

Persistent boat condition, automatic campaign-day recovery and optional paid
acceleration are tracked separately in `boat-condition-and-repairs.md`; the
opening wreck remains a paid restoration, and tactical repair behavior is not
changed here.
