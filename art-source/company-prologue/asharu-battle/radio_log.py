"""The Asharu recording's cast and its timed radio lines: one source for the playback screen
(asharu_preview.recording_screen), the S0 mock (fight_layout) and the shot doc's radio table.

Callsigns, never hull classes (owner, 2026-10-22: "Ford Focus I need you to be on my flank
Chrysler Challenger out"). The convoy flies under one callsign, CARAVEL: the Colossus, its master's
ship, is CARAVEL, and its freighters are numbered. Its escorts are WARDENs, with the Lasher lead
as WARDEN LEAD. Morrow keeps her own name, because the player later reads it on her name plate.
The pirates use their own handles. A caption leads with the callsign under the registry prefix the
game gives its faction's ships (faction shipNamePrefix: ISS for an independent, none for a pirate,
who flies unregistered), then the hull's game designation as a role word (ship_data.csv), never its
hull name: ISS CARAVEL, Heavy Freighter; BARON, Light Cruiser (`caption`).

Each line names the strings.json key the arrival player will caption it with
(`asharuRadio<Shot>_<n>`). The keys are not in strings.json yet: the cinematic is not wired
into the game. When it is, these ids are the keys."""

CALLSIGNS = {
    'colossus': 'CARAVEL', 'tarsus': 'CARAVEL TWO', 'mule': 'CARAVEL FOUR', 'phaeton': 'CARAVEL SIX',
    'buffalo-a': 'CARAVEL SEVEN', 'buffalo-b': 'CARAVEL EIGHT', 'dram': 'CARAVEL NINE',
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
SCREEN_CHANNELS = ['CARAVEL', 'WARDEN LEAD', 'MORROW', 'WARDEN FIVE', 'CARAVEL SIX', 'WARDEN THREE',
                   'WARDEN FOUR', 'CARAVEL FOUR', 'CARAVEL TWO', 'BARON', 'LATCH', 'HOOK']

# The whole recording, timed in sequence seconds (the shot doc's "Shot list"): (shot, callsign, t0, t1,
# line, placeholder). The preview burns each line in as a caption at its time, so the owner can judge
# the traffic's density and whether the edit follows the radio. These are draft lines: the final
# script is a writer's job (the shot doc's "Radio traffic (draft)"). Lines marked placeholder only
# hold a gap's timing and are not dialogue to keep. S3 stays silent until BARON's "Hold", and S12
# stays silent throughout.
TRAFFIC = [
    # S1's traffic plays over S0's screen; the picture cuts in on "close up the column" (10 s)
    ('S1', 'CARAVEL', 1.8, 6.4, 'All Caravel ships, Caravel. Window opens in forty. Ease to two-thirds.', False),
    ('S1', 'WARDEN LEAD', 6.9, 9.7, 'Warden Lead copies. Wardens, close up the column.', False),
    ('S1', 'WARDEN TWO', 16.8, 18.6, 'Two, on station.', True),
    ('S1', 'CARAVEL SEVEN', 20.2, 22.4, 'Seven, two-thirds, aye.', True),
    ('S2', 'MORROW', 24.6, 28.6, 'Caravel, Morrow. Standing off your starboard quarter. Drones stowed.', False),
    ('S3', 'BARON', 31.2, 34.0, 'Hold. ... Hold. ... Light them.', False),
    ('S4', 'WARDEN FIVE', 38.6, 41.6, 'Lead, Five. Drives behind us. Low, off that old station. A lot of them.', False),
    ('S4', 'WARDEN LEAD', 41.9, 42.6, 'Say again.', False),
    ('S4', 'WARDEN FIVE', 42.9, 43.9, 'Eleven. Climbing.', False),
    ('S5', 'WARDEN LEAD', 44.4, 46.9, 'Wardens, come about. Caravel, full burn, now.', False),
    ('S5', 'CARAVEL', 47.2, 49.8, "We're committed to the window. We can't outrun them from here.", False),
    ('S6', 'CARAVEL SIX', 51.0, 53.2, "Six, we're hit, we're venting, we're —", False),
    ('S6', 'WARDEN LEAD', 54.5, 56.6, 'Lead turning back. Cover the tail.', False),
    ('S6b', 'WARDEN THREE', 58.4, 60.6, 'Three, rolling in. Four, take her left.', False),
    ('S6b', 'WARDEN FOUR', 61.0, 62.6, "She's got no shield. Hit her.", False),
    ('S6b', 'WARDEN THREE', 63.0, 65.6, "Feeders on. ... She's breaking up. One down.", False),
    ('S6c', 'CARAVEL FOUR', 66.4, 68.4, "Two frigates in the gap! They're in the column!", False),
    ('S6c', 'WARDEN FOUR', 68.8, 71.4, "Shields up, they blinked, where — on Two, they're on Caravel Two!", False),
    ('S6c', 'CARAVEL TWO', 71.8, 73.9, "Taking fire aft, flux at ninety, I can't hold the shield —", False),
    ('S6d', 'WARDEN FIVE', 74.4, 76.6, 'Two on me. ... Got one. Got one!', False),
    ('S6d', 'WARDEN FIVE', 77.4, 79.6, 'Drives hit. Falling back on the column. Flares out.', False),
    ('S7', 'LATCH', 80.8, 83.8, 'Caravel Two. Open your locks and you all walk away.', False),
    ('S7', 'CARAVEL TWO', 84.4, 87.0, "Drives are gone. They're alongside. Boarders, boarders —", False),
    ('S8', 'CARAVEL', 88.6, 91.0, "Port pod's gone. Morrow, get under us.", False),
    ('S8', 'CARAVEL FOUR', 91.8, 93.6, 'Four, flux at sixty, holding.', True),
    ('S9', 'MORROW', 94.4, 96.0, 'Under you. Taking the weight.', False),
    ('S9', 'MORROW', 96.4, 98.0, 'Borers out.', True),
    ('S9', 'HOOK', 98.6, 101.2, "Caravel. Heave to. You're next.", False),
    ('S9b', 'WARDEN LEAD', 102.4, 104.6, 'Lead. One more pass. On their flagship.', False),
    ('S9b', 'BARON', 105.0, 106.6, "Shield up. ... Shield's overloading —", False),
    ('S9b', 'WARDEN LEAD', 106.9, 107.9, "She's open. She's open!", False),
    ('S9b', 'BARON', 108.2, 109.9, 'Broadside. Burn that frigate off my hull.', False),
    ('S10', 'BARON', 110.4, 113.4, "Let her go. She's going down anyway. Take Caravel Two.", False),
    ('S10', 'CARAVEL', 113.9, 116.0, "We're losing the orbit. Morrow, break off.", False),
    ('S10', 'MORROW', 116.4, 117.4, 'Staying with you.', False),
    ('S11', 'CARAVEL', 119.0, 121.4, 'Caravel, hull temperature climbing.', True),
    ('S11', 'MORROW', 121.8, 123.6, 'Morrow, losing you in the —', True),
    ('S13', 'CARAVEL FOUR', 139.2, 142.4, 'All Caravel ships, Caravel Four. Who is still with me?', False),
    ('S13', 'WARDEN THREE', 143.0, 145.4, 'Warden Three. Still here. The column is scattered.', False),
    # owner, 2026-10-22: no pirate sign-off. Warden gives up on the lost ships, and reads the pirates as
    # satisfied with what they caught (Caravel Two); nothing says what the Colossus carried
    ('S13', 'WARDEN THREE', 145.9, 148.6, "Nothing from Caravel or the Morrow. ... They're not chasing. "
                                          "Looks like they're happy with what they caught.", False),
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
