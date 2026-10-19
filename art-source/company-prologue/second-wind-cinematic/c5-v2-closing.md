# C5 v2 — WE'RE DOWN (closing beat and title)

Status: DRAFT — direction agreed (overhead landing, pull-out, title); Castle's closing redrafted and chosen provisionally, to be judged against picture; review takes recorded, none shipped.
Written: 2026-10-18
Updated: 2026-10-19 — C4 and C5 are one shot; the port around Kilo Nine is staffed (`port_people.py`); travel-reels line moved to C3, C4 opens on "Greywater's past the cranes", Kilo Nine line drafts.

C5 in `storyboard-v3.md` is an 8 s push-in on the ship in Nessa's cradle, cut
straight into the yard report. v2 makes it the cinematic's ending: the ship
lands seen from overhead, the people walk off, the camera rises until Second
Wind is one patched hull among many, and the title comes up over the port.

## The dialogue arc into it

Agreed 2026-10-18. Hope builds in three short lines, each with one job. Nobody
makes a speech before the closing.

| Beat | Line (Castle) | Job |
|---|---|---|
| C1–C2 | The hail as recorded, ending "Corridor four, mounts cold. Thank you, Approach." | Scrutiny. The ship is small, Approach is cold, and Castle is careful. |
| Entry | **"Cleared. Everybody breathe."** (replaces "Well. Somebody knows we're here.", which could be heard as a threat) | Relief. Her first words to the passengers rather than to Approach, which sets up the closing's "All hands". |
| C3 | Silent while the ship levels out, then **"Like the travel reels they ran in the canteen... except this one doesn't loop."** (moved from C4, owner 2026-10-19, so it plays over the jungle) | Wonder, then recognition. Until now these people knew landscapes only from a screen on the Claim, and this one is real and goes on. |
| C4 | **"Greywater's past the cranes. Let me do the talking."** as the shot opens, then **"Look for the fenced yard on the east side. Kilo Nine. Mind your manners down there; it's a favour."** (below). | Arrival: back to business, and somewhere of their own to land. |
| C5 | The closing lines below. | Permission: go and live here. |

The prologue pages carry the wound the hope answers. Facility support ended in
air, heat and power, the days were chalked beside the airlock, and the ration
counter stopped taking scrip. The hope should be small, concrete and earned. It
should not promise riches, which belong to the campaign. Nessa's memory returns
to P6 (`storyboard-v3.md`, Decisions); Y1 introduces her in the yard.

Review takes of the new Entry, C4 and C5 lines exist in Castle's voice
(`storyboard-v3.md`, Casting), made with `arrival-audio/record_castle_lines.py`.
Until the owner picks takes, the shipped `jangalaArrivalHail09` string and audio
stay as they are, so caption and voice still match.

## Castle names Kilo Nine in C4

Chosen by the owner (2026-10-19, of three drafts, as the most personable):

> Look for the fenced yard on the east side. Kilo Nine. Mind your manners down there; it's a favour.

It tells the passengers where they are landing as the camera finds the yard, and
"it's a favour" sets up Nessa without explaining her. Review takes `c4_kilo_nine_t01`
and `_t02` (about 6.3 s, comms intercom) from `arrival-audio/record_castle_lines.py`.

**It fits C4 as built; the pan does not need to be longer.** With the travel-reels
line moved to C3, C4 carries only Castle's two lines. "Greywater's past the cranes"
(3.4 s) plays from about 0.5 s, over the fields and freight road. This line follows
from about 4.6 s and ends about 10.9 s, just before touchdown at 11.0: "Look for the
fenced yard" comes as the camera starts drifting east, and "Kilo Nine" lands about
7.4 s, as it settles over the yard (straight over it by 8.0 s). It runs under the
landing rocket (from 4.0 s), which the mix ducks. If it feels crowded in the mix, the
fix is a longer pan in `industrial-exterior/c4x_scene.py` (8 s to 10 s), not a cut.

## Who speaks, and from where

**Castle, on the all-ship intercom, after touchdown.** It is diegetic. She is
telling the families they can get off, which is what she would actually say.
The announcement plays over the overhead shot of the families coming off the
ship, so it is heard as the ship's loudspeaker carrying across the yard: a
stronger, narrower sound than the cabin intercom of C1–C4, with a short metallic
echo and still no squelch (owner's choice, 2026-10-18; the cabin intercom sound
stays for lines heard inside). Approach does not speak again. The Hegemony stays cold, and the last
word belongs to our people.

## Where it lands: Kilo Nine

Kilo Nine is not a public cradle. It is a private yard where Nessa is forewoman (its owner stays offscreen), fenced, on the
east edge of Greywater Down's port with the jungle beyond its east fence. Its
south half holds one cradle between two repair scaffolds, a derrick over a
salvage pile, a hangar, fuel, and the workshop the families walk into. Its north
half holds the works where hulls are actually mended: a fabrication hall, an
overhead crane over the plate stock and hull sections, a parts store of stacked
modules, the yard's own generators, and a scrap heap.

Ground traffic has one way in. The port road runs east along the container
yard to a gate in the yard's south fence, with a gatehouse and a raised
barrier. From the gate a haul road runs up the west fence to the works, with a
branch across to the cradle. The
Hegemony's public cradles lie west of it, each turned to face its own lead-in
lane, with freighters unloading from the stern down cargo lanes to the
container yard. The ships on them are a mix of the finished hull models at
their own scale (`industrial-exterior/port_fleet.py`): a Hegemony Hound, two
Shepherds in different operators' colours, two Mudskippers (Wayline's ferry
and a teal-trimmed one lifting out), Hermes lighters on the pad row, and a
Colossus in the walled heavy berth, the only bay large enough for it. Overhead,
a Shepherd lets down over the belt in C4's first seconds. The heavy-berth
Colossus leaves during C5's rise, flown rather than posed. Its lift jets spool
at C5 12 s and it comes off the deck at 15 s, slowly, the way a heavy hull
would. Clear of its walls it yaws 35 degrees, eased in and out between 18.5 and
23 s, sliding a little south so its corners pass under the gantry's beams. Its
mains light at 19.5 s and are full at 21 s; it keeps turning, slower and
banked a few degrees into the turn, onto a heading of 255 by 32 s, so its
course curves south-west over the open port. It is still in frame and
travelling at 38 s, about a quarter in from the west edge over the pad row. It
is 16 m above its deck at 22 s, 50 m at 26 s, 107 m at 30 s and 345 m at
40 s. Against a
camera at 1.8 km, it reads by its burn and its motion, not by growing in the
frame. Its drives are the shared VFX kit's effects on its own engines, each
throttled by its thruster marker. The Shepherd over the belt carries on north
out of frame.
C4's sky shot ends over this layout
(`industrial-exterior/c4x_scene.py`), and C5 must match it.

On the ground: Nessa's tug, bowser and crew carrier roll out from the hangar
and workshop and wait clear of the downwash. A Hegemony customs car, white with
an orange roof band and lights going, comes over from the port office and in
through her gate as the ship touches down. That pays off the customs mention in the hail. The officers meet the
ship at the ramp; the families pass them on the way to the workshop.

## The hand-off from C4

Decided 2026-10-18 by the owner. C4 does not end on the wide port. After its
8 s pan the sky camera comes down to about 480 m straight over Kilo Nine and
lingers for 8 s more, drifting north over the yard. Second Wind braked into a
hover over its pad (96 m across, room to spare around the hull) during the pan.
Now it sinks and touches down at about 11 s in a ring of yard dust, and its
drive spools down. Once the dust thins the yard goes to work. The tug, bowser
and crew carrier move in from their stand-offs, and the scaffold arms swing in
to the hull. Hands in hi-vis head out from the workshop. The customs car comes
in through the gate behind them. C4 ends with the ship down and the yard
moving.

**C4 and C5 are one shot** (owner, 2026-10-19): there is no cut. C5 picks up
C4's camera exactly where it stops, straight down over the yard on its wide
lens, and over the first 4 s pushes in to the long-lens view of the ramp, the
ground in frame shrinking at a steady rate while the lens lengthens. The yard's
vehicles are still rolling in as the push begins, and their crews climb out once
they stop. Nothing appears or vanishes at the join: whoever stood about the yard
in C4 is still standing there in C5. The wide reveal
(the yard as a small corner of a big working port, the ship one patched hull
among the freighters) is kept for C5's pull-out under the title, so it is seen
once and lands as the closing image. The public berths are seen passing
through frame during C4's pan; C4 just does not rest on them.

## Beats (about 30 s)

| t (s) | Picture | Sound |
|---|---|---|
| 0–5 | Overhead, looking straight down and a step closer than C4's last frame. Second Wind sits on its pad, the last of the dust drifting off. The tug, bowser and crew carrier pull up at the hull, and the customs car parks off the stern. | Hull ticks as it cools, the yard bed. |
| 5–8 | Engines dark. Steam vents. Crew in biofilter masks walk out; one figure with folded arms (Nessa, unreadable). | Intercom click. Castle begins. |
| 8–18 | The ramp drops. A thin stream of figures carrying bags crosses the yard, past the customs officers, toward Nessa's workshop. One stops at the ramp's foot and looks up. | Castle's middle lines. |
| 18–26 | The camera rises straight up. Nessa's yard shrinks to a small fenced corner of a big working port, and the ship becomes one patched hull among the freighters. | Castle's last line, then the bed opens up. |
| 26–32 | High over the port and the jungle edge. Title: **STARSECTOR: MARINES**. | Music swells and resolves; the yard sound fades out. |

The title comes before the yard dialogues (Y1 onward) in the usual cold-open
way: cinematic, title, then play.

## Castle's closing lines

**Chosen 2026-10-18, provisionally.** The owner will judge it against the
picture and may change it.

> All hands, we're down.
> Filters on before you step out; it's wet out there.
> Take it slow on the ramp, and follow the yard crew to the workshop.
> *(loudspeaker off; quieter, to the Commander)*
> That was the easy part, Commander.

The first draft ("Everyone we took aboard at the Claim is walking off this ship
on their own feet… Go and see the sky… A new start, Commander. Let's not waste
it.") read as cheesy once recorded: it told the audience what to feel. The
announcement is now only what she would actually say. The hope is in the
picture, the families walking off under an open sky. Castle's voice is calm and
resolute, a captain who has brought her people down: tired reads came out
worried (owner, 2026-10-18), so the directions no longer ask for tired. The last line is private, and the
ear knows it because the loudspeaker is gone. It is wry rather than tender, and
it turns the arrival into the work the campaign is about.

**Timing.** The announcement is about 8.5 s. Starting at about 5 s, "Take it
slow on the ramp" lands as the ramp drops at 8 s and the announcement ends
before the camera rises at 18 s. The private line plays over the pull-out as the
ship shrinks among the working hulls, and the title follows.

**Callbacks.** The filters recall air the Claim had to ration. Entry's
"Everybody breathe" sets up "All hands".

**Alternatives if it does not land:** the last line "It's a start, Commander."
or "One ship, twelve rifles and a yard bill. It's a start, Commander." A shorter
announcement: "All hands, we're down. Take your filters and your bags. Mind the
ramp."

## Camera and crowd

Tested 2026-10-18 with `industrial-exterior/c5_crowd_test.py`, which builds C4's
scene at touchdown and walks a line of families from the ramp toward the
workshop. It does this two ways side by side, under three candidate cameras.

**The camera looks straight down through a long lens from higher up.** C4's
wide 18 mm lens sees a person at the frame edge from 45° off vertical, so
buildings lean outward and a figure there would need a side-on view. A 50 mm
lens covers the same ground from about 2.8 times the height, and nobody in
frame is more than about 20° off straight down. C5 opens at 50 mm about 220 m
up: the yard from the ramp to the workshop fills the frame, Second Wind fills
its upper right, and a person is about 6 pixels across. The cut from C4's
18 mm view to this one reads as a step closer, not a jump. The title pull-out
rises straight up along the same line.

**At that size a person is a handful of pixels, so detail cannot be seen and
perspective barely matters.** Both methods read as people walking:

- **Simple 3D walkers.** A body, a head and two swinging legs. They read
  crisper and darker, and their shadows, lighting and perspective come for free.
  They are the layout's default: nothing to keep consistent, and a bag, a mask
  or a folded-arms pose is one more block.
- **Flat sprite cards.** Lying on the ground and turned to each walker's
  heading, flipping through a top-down walk cycle. A second upright card faces
  the sun and only casts a shadow, so the shadow carries a side-on walk. With
  the straight-down long lens, one top-down sheet works in every direction and
  anywhere in frame. Codex could make the sheets: 8 frames across, clothing
  variants down, 64x64 px a frame at 2.5 cm a pixel for the top view and 64x96
  for the side view, both walking toward +x (the layout the test's placeholders
  follow). Kept in reserve for a closer insert, such as the figure at the ramp
  who looks up.

**The rise.** From 18 s the camera goes straight up, easing in and out, to
about 1.8 km at 26 s, drifting west as it climbs so the yard ends as one fenced
corner of the port, with the public berths, the container yard and the jungle
edge all in frame. It keeps rising a few percent more under the title, so the
port is still settling when the title arrives (`title-logo.md`). The title's
reveal is counted from 24 s, when Castle's last line ends, and the picture is
black at 33 s. `industrial-exterior/c5_title.py` lays the title over the
rendered frames, so the wordmark is read from the player's install and never
rendered into the scene. The title sits over the yard, with Second
Wind under the name, which the owner accepted on 2026-10-19.

**The look waits on the texture pass.** At this distance even the placeholder
sprites hold up, so the choice between them and the 3D figures is made once
the yard is painted. `industrial-exterior/c5_scene.py` takes either
(`--body 3d|sprite`).

**How the crowd moves** (`industrial-exterior/c5_crowd.py`):

- **Groups, not a queue.** Families come off the ramp in small groups: one to
  five people, a child with the larger ones, bags in most hands. Each group
  walks at its own pace, two abreast with the rest behind. The leader slows for
  stragglers, and strangers keep a little more space than family does.
- **People peel off.** Near the ramp, while the camera is still close, one
  family steps aside to wait for somebody still aboard and then goes on. Another
  stops and stays. One person stops a few steps off the ramp to look up.
- **Nobody walks through anything.** The walk map is read from the built yard
  itself. Every crate, vehicle, wall, washing line and the hull is sliced at
  knee and chest height, and paths keep a body's width clear of the outline.
  Each run counts the walker-frames spent inside anything, and it should be 0.
- **The yard is at work.** Nessa's hands in hi-vis guide at the ramp's foot,
  walk between the crew carrier and the hull, and stand by the tug and the
  bowser. The two customs officers leave their car to stand either side of the
  way off the ship. Nessa comes out of her workshop and stands apart near the
  door.
- **It is a long walk.** The workshop door is 52 m from the ramp, nearly 40
  seconds on foot, so the stream is still crossing as the camera rises. That is
  the picture the beats ask for: a thin line of people across the yard.

Kilo Nine's pad is a low slab (0.4 m) rather than the public berths' 3 m
plinth, so people and the yard's vehicles step straight onto it.

## The port around it

The public port works around Kilo Nine through C4 and on through C5, so the
wide image under the title is a working port rather than a set
(`industrial-exterior/port_life.py`). Vans, box trucks and container trucks run
the port road, the cross streets and the strip between the warehouse rows,
with a MuniSec patrol car among them. They wait at the crossings for the
container carriers coming off the berths, and nothing drives through anything:
the module's report counts overlaps and should read 0. The yard gantries move
boxes between stacks, and the one over aisle -125 lifts boxes off yard
tractors and sets others on. Forklifts run stores up the gaps beside the worked
freighters and along the warehouse faces; a bowser comes in to the one
fuelling; baggage tugs, the port bus and trucks at the terminal doors work the
apron between the ferry and the freight terminal. A Hermes lifts off in C4's
last seconds. During C5's rise another comes down on that pad, and the Colossus
climbing away over the title is the fleet's (`port_fleet.py`). All of it is outside
Kilo Nine's fence and keyed through C5's last frame.

**The port's people** (`industrial-exterior/port_people.py`). The rise would
show a port of empty vehicles without them, so the ground is staffed site by
site, thickest close to Kilo Nine and on the public apron west of it and
thinner further out, but present everywhere the rise reveals. Each job is one
that reads from above as a shape:

- an arrivals queue along the terminal hall's faces, moved up by two officials;
- porters unloading the baggage tug while it stands at the hall;
- a road gang crouched along the kerb with a foreman pacing it;
- gate guards, one of whom stops a courier;
- a marshaller waving the returning lighter onto its pad, then hands
  unloading it;
- signallers and lashers at the two working cranes, standing clear while a
  box comes down (the girder hides anything under it from above);
- crates carried from Kilo Nine's stores to its hangar;
- cutters and a slinger in the salvage pile;
- a smoke-break ring;
- mechanics;
- welders on the scaffold arms;
- crews on the berths, pads and fuel farm.

People face each other, hand things over, wait for each other and walk in
pairs. Poses (crouch, signal, carry, talk, arms folded, work) are blocks on
the 3D figures and extra columns on the sprite sheet. Each site has its own
small walk map, read from the built scene like the yard's. Every vehicle that
moves during C5 is blocked along its whole path, so nobody stands in a lane
or under a wheel. The run's report counts people inside anything and should
read 0. `--no-port` leaves the port people out.

## Open questions

- **Line choice and delivery.** Castle's voice is calm and steady, and the hope
  is under the words, not in them, and she sounds sure of herself. Confident
  and resolute reads were recorded. Choose by ear against the picture.
- **Music.** The hopeful ambient track the owner is looking for should arrive
  here, under Castle's last line, rather than be spent earlier.
- **Crowd look.** Simple 3D figures or sprite cards (see "Camera and crowd").
  Decide after the texture pass.
- **Y1 hand-off.** Y1 opens with Castle reporting the meeting with Nessa. The
  title separates the two, so the change of tone is allowed.
