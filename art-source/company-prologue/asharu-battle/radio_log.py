"""The Asharu recording's cast and its timed radio lines: one source for the playback screen
(asharu_preview.recording_screen), the S0 mock (fight_layout) and the shot doc's radio table.

Callsigns, never hull classes (owner, 2026-10-22: "Ford Focus I need you to be on my flank
Chrysler Challenger out"). The convoy flies under one callsign, CARAVEL: the Colossus, its master's
ship, is CARAVEL, and its freighters are numbered. Its escorts are WARDENs, with the Lasher lead
as WARDEN LEAD. Morrow keeps her own name, because the player later reads it on her name plate.
The pirates use their own handles. Hull classes appear only in the preview's legend.

Each line names the strings.json key the arrival player will caption it with
(`asharuRadio<Shot><letter>`). The keys are not in strings.json yet: the cinematic is not wired
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

# the channel list the playback screen shows: every voice the recording carries, in the order they
# first speak
SCREEN_CHANNELS = ['CARAVEL', 'WARDEN LEAD', 'MORROW', 'WARDEN FIVE', 'CARAVEL SIX', 'WARDEN THREE',
                   'WARDEN FOUR', 'CARAVEL FOUR', 'CARAVEL TWO', 'BARON', 'LATCH', 'HOOK']

# S0: the S1 traffic as audio only over the screen; the picture cuts in on "close up the column" (10 s)
OPENING = [('asharuRadioS1a', 'CARAVEL', 1.8, 6.4, 'All Caravel ships, Caravel. Window opens in forty. Ease to two-thirds.'),
           ('asharuRadioS1b', 'WARDEN LEAD', 6.9, 9.7, 'Warden Lead copies. Wardens, close up the column.')]

# S11: the plasma takes the channel; these drop out, in this order, at these times (s)
DROPS = {'CARAVEL': 124.9, 'MORROW': 125.3}

# S13: back on the log after the trails, the survivors and the pirates on the channel; the log
# ends on END OF RECORDING, not on a held frame
CLOSING = [('asharuRadioS13a', 'CARAVEL FOUR', 135.2, 138.4, 'All Caravel ships, Caravel Four. Who is still with me?'),
           ('asharuRadioS13b', 'WARDEN THREE', 139.0, 141.4, 'Warden Three. Still here. The column is scattered.'),
           ('asharuRadioS13c', 'BARON', 141.9, 144.6, 'Baron to all hands. We have what we came for. Burn out.')]
END_CARD_S = 145.6
