"""The Asharu recording's cast and its timed radio lines: one source for the playback screen
(asharu_preview.recording_screen), the S0 mock (fight_layout) and the shot doc's radio table.

Callsigns, never hull classes (owner, 2026-10-22: "Ford Focus I need you to be on my flank
Chrysler Challenger out"). The convoy flies under one callsign, CARAVEL: the Colossus, its master's
ship, is CARAVEL, and its merchant ships answer to their own names, after Second World War
merchantmen (owner, 2026-10-28): the Tarsus is SOBIESKI, the Mule THYSVILLE, the Phaeton BATORY and
the first Buffalo ALBERTVILLE. Its escorts are WARDENs, numbered, with the Lasher lead as WARDEN LEAD.
Morrow keeps her own name, because the player later reads it on her name plate.
The pirates use their own handles. A caption leads with the callsign under the registry prefix the
game gives its faction's ships (faction shipNamePrefix: ISS for an independent, none for a pirate,
who flies unregistered), then the hull's game designation as a role word (ship_data.csv), never its
hull name: ISS CARAVEL, Heavy Freighter; BARON, Light Cruiser (`caption`).

Each line names the strings.json key the arrival player will caption it with
(`asharuRadio<Shot>_<n>`). The keys are not in strings.json yet: the cinematic is not wired
into the game. When it is, these ids are the keys."""

CALLSIGNS = {
    'colossus': 'CARAVEL', 'tarsus': 'SOBIESKI', 'mule': 'THYSVILLE', 'phaeton': 'BATORY',
    'buffalo-a': 'ALBERTVILLE', 'buffalo-b': 'CARAVEL EIGHT', 'dram': 'CARAVEL NINE',
    'lasher-lead': 'WARDEN LEAD', 'lasher-a': 'WARDEN TWO', 'lasher-b': 'WARDEN THREE', 'wolf-m': 'WARDEN FOUR',
    'hound-m': 'WARDEN FIVE', 'morrow': 'MORROW',
    'falcon-p': 'BARON', 'colossus3': 'HOOK', 'kite-p-a': 'LATCH', 'kite-p-b': 'LATCH TWO',
    'manticore-p': 'ANVIL', 'wolf-p-a': 'KNIFE', 'wolf-p-b': 'KNIFE TWO', 'hound-p-a': 'CUR',
    'hound-p-b': 'CUR TWO', 'hound-p-c': 'CUR THREE', 'buffalo2-a': 'SACK',
}

# the registry prefix of each side's ships, and each hull's designation (ship_data.csv)
PREFIX = {'convoy': 'ISS', 'pirate': ''}
DESIGNATIONS = {
    'colossus': 'Heavy Freighter', 'tarsus': 'Freighter', 'mule': 'Combat Freighter', 'phaeton': 'Tanker',
    'buffalo-a': 'Freighter', 'buffalo-b': 'Freighter', 'dram': 'Tanker',
    'lasher-lead': 'Frigate', 'lasher-a': 'Frigate', 'lasher-b': 'Frigate', 'wolf-m': 'Frigate',
    'hound-m': 'Combat Freighter', 'morrow': 'Drone Tender',
    'falcon-p': 'Light Cruiser', 'colossus3': 'Modified Freighter', 'kite-p-a': 'Shuttle', 'kite-p-b': 'Shuttle',
    'manticore-p': 'Destroyer', 'wolf-p-a': 'Frigate', 'wolf-p-b': 'Frigate', 'hound-p-a': 'Combat Freighter',
    'hound-p-b': 'Combat Freighter', 'hound-p-c': 'Combat Freighter', 'buffalo2-a': 'Destroyer',
}
PIRATES = {'falcon-p', 'colossus3', 'kite-p-a', 'kite-p-b', 'manticore-p', 'wolf-p-a', 'wolf-p-b',
           'hound-p-a', 'hound-p-b', 'hound-p-c', 'buffalo2-a'}


def caption(callsign):
    """A speaker's caption: (registry prefix and callsign, hull designation)."""
    key = next((k for k, v in CALLSIGNS.items() if v == callsign), None)
    if key is None:
        return callsign, ''
    prefix = PREFIX['pirate' if key in PIRATES else 'convoy']
    return (prefix + ' ' + callsign).strip(), DESIGNATIONS[key]


# the channel list the playback screen shows: every voice the recording carries, in the order they
# first speak
SCREEN_CHANNELS = ['CARAVEL', 'WARDEN LEAD', 'WARDEN TWO', 'MORROW', 'WARDEN FIVE', 'BATORY', 'WARDEN THREE',
                   'WARDEN FOUR', 'THYSVILLE', 'SOBIESKI']

# The whole recording, timed in sequence seconds (the shot doc's "Shot list"): (shot, callsign, t0, t1,
# line, placeholder). This is the voiced draft on the Battle Cut page (its captions_draft lines that are
# on, at the times the owner set against the takes), so the terminal captions what the radio says. A line
# marked placeholder only holds a gap's timing and is not dialogue to keep; none is, now that every line
# has a take. Lines overlap where the radio talks over itself. The pirates are not heard (BARON and the
# rest keep their handles for captions), and S3 stays silent.
TRAFFIC = [
    ('S1', 'CARAVEL', 1.8, 7.92, 'All escorts, Caravel. Window opens in forty. Enjoy the view before deep '
                                 'space.', False),
    ('S1a', 'WARDEN LEAD', 10.0, 12.66, 'Nothing but void and the odd picket from here.', False),
    ('S1', 'WARDEN TWO', 16.0, 21.43, 'Ha more picket "ping" to drive me crazy. Guess they keep us '
                                      'honest.', False),
    ('S2', 'MORROW', 24.6, 30.17, 'Caravel, Morrow. Standing off your starboard quarter. Long ride even to '
                                  'the jump point.', False),
    ('S4', 'WARDEN FIVE', 37.6, 41.85, 'Scanners picking up new contacts behind us. No '
                                       'identification.', False),
    ('S4', 'WARDEN LEAD', 42.0, 42.84, 'Say again?', False),
    ('S5', 'WARDEN LEAD', 44.9, 49.53, 'Wardens, come about! Caravel, full burn for the nearest picket, '
                                      'now!', False),
    ('S5', 'WARDEN FIVE', 48.38, 50.1, 'Launch, launch!', False),
    ('S5', 'WARDEN FIVE', 50.15, 54.1, 'Missiles inbound! Twelve! Point defence, now!', False),
    ('S6', 'WARDEN TWO', 53.3, 54.45, "Two's hit!", False),
    ('S6', 'BATORY', 53.8, 56.42, "Batory, we're hit, we're venting, we're —", False),
    ('S6', 'WARDEN LEAD', 56.99, 58.68, 'Lead turning back. Cover the —', False),
    ('S6b', 'WARDEN THREE', 58.0, 61.77, 'Three, rolling in. Four, take her left.', False),
    ('S6b', 'WARDEN FOUR', 60.6, 62.39, 'No shield. Hit her!', False),
    ('S6b', 'WARDEN THREE', 61.92, 62.97, 'Feeders on!', False),
    ('S6b', 'WARDEN THREE', 63.12, 65.18, "She's breaking up! One down!", False),
    ('S6c', 'THYSVILLE', 66.4, 69.16, 'Wolf coming in! Another on the far side!', False),
    ('S6c', 'WARDEN FOUR', 69.1, 74.09, "Shields up, she blinked — where — there, she's right on "
                                        'them!', False),
    ('S6c', 'SOBIESKI', 69.8, 76.09, "Taking fire aft! Flux at ninety, can't hold shield and guns — drives! "
                                     "We've lost drives!", False),
    ('S6c', 'THYSVILLE', 72.6, 75.17, "She's through the gap! She's in the column!", False),
    ('S6d', 'WARDEN FIVE', 74.3, 75.12, 'Two on me —', False),
    ('S6d', 'WARDEN FIVE', 76.9, 78.83, "One down, they're coming through!", False),
    ('S7', 'WARDEN FIVE', 81.21, 82.91, "They're heading for the Sobieski!", False),
    ('S7', 'CARAVEL', 82.0, 85.07, 'That mark three is hammering down on us!', False),
    ('S7', 'SOBIESKI', 84.9, 87.23, 'Marines prepare to repel boarders!', False),
    ('S8', 'CARAVEL', 88.4, 92.6, 'Taking fire on the port pod! Morrow, keep pressure with the '
                                  'drones!', False),
    ('S9', 'MORROW', 91.6, 94.34, "Borers are out — we're raking her sides.", False),
    ('S9', 'CARAVEL', 93.4, 96.08, "Hits on the port pod! Drive's failing —", False),
    ('S9', 'CARAVEL', 96.47, 99.21, "Pod's gone! Turrets on that freighter!", False),
    ('S9', 'MORROW', 96.6, 99.01, "Destroyer's venting — she's breaking off!", False),
    ('S9', 'CARAVEL', 99.36, 102.56, "We're losing hull here - Where's the warden escort!", False),
    ('S9b', 'WARDEN LEAD', 104.91, 107.6, "Her shield's down! Keep on her, make them back off — I'm "
                                          '—', False),
    ('S10', 'SOBIESKI', 110.4, 113.69, 'Boarding parties nearing the bridge. Vent the passages -', False),
    ('S10', 'CARAVEL', 112.4, 117.27, "We're losing thrust, we're in the well, we're going down. Morrow, "
                                      'break off.', False),
    ('S10', 'MORROW', 116.4, 118.05, 'Staying with you.', False),
    ('S11', 'CARAVEL', 118.2, 124.8, "Hull temperature's climbing — helm, hold forty degrees angle of "
                                     "attack, belly to the burn! Don't let her nose —", False),
    ('S11', 'MORROW', 125.0, 127.54, 'Come again Caravel losing your signal in the —', False),
    ('S12', 'THYSVILLE', 130.0, 137.11, 'All ships this is Thysville. Regroup on me, we need to tighten '
                                        "formation. Helm what's the pirates heading?", False),
    ('S13', 'WARDEN THREE', 139.0, 145.14, "This is Three. We're scattered, ships going down. Do we go back "
                                           'for Caravel? Morrow?', False),
    ('S13', 'THYSVILLE', 146.0, 151.6, "Negative, we don't have the numbers. We form up and continue burn "
                                       'for the nearest picket.', False),
]


def _keyed():
    """(key, callsign, t0, t1, line, placeholder): the strings.json key, asharuRadio<Shot>_<n>, numbered
    in order within the shot; a placeholder has none."""
    out, n = [], {}
    for shot, who, t0, t1, line, ph in TRAFFIC:
        n[shot] = n.get(shot, 0) + 1
        out.append((None if ph else 'asharuRadio%s_%d' % (shot, n[shot]), who, t0, t1, line, ph))
    return out


LINES = _keyed()


def speaking(t):
    """The lines on the channel at sequence time t."""
    return [ln for ln in LINES if ln[2] <= t <= ln[3]]


# S0's screen: the S1 traffic as audio only; the picture cuts in on "close up the column" (10 s)
OPENING = [ln[:5] for ln in LINES if ln[3] <= 10.0]

# S11: the plasma takes the channel; these drop out, in this order, at these times (s)
DROPS = {'CARAVEL': 124.9, 'MORROW': 125.3}

# S13: back on the log after the trails, the survivors and the pirates on the channel; the log
# ends on END OF RECORDING, not on a held frame
CLOSING = [ln[:5] for ln in LINES if ln[2] >= 138.0]
END_CARD_S = 149.6
