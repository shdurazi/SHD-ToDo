"""Reads `logcat -s ToDoIsland` text on stdin and prints the latest island geometry as
"pillCenterX pillCenterY pillBottom cardCenterX cardLowY density" (screen pixels), for the emulator test to tap on."""
import re
import sys

text = sys.stdin.read()
pill = card = None
dens = None
for line in text.splitlines():
    m = re.search(
        r"pill=\[([-\d.]+),([-\d.]+)\]\[([-\d.]+),([-\d.]+)\]\s+card=\[([-\d.]+),([-\d.]+)\]\[([-\d.]+),([-\d.]+)\]", line
    )
    if m:
        v = list(map(float, m.groups()))
        pill, card = v[:4], v[4:]
    m = re.search(r"density=([\d.]+)", line)
    if m:
        dens = float(m.group(1))
if pill and card:
    pcx = (pill[0] + pill[2]) / 2
    pcy = (pill[1] + pill[3]) / 2
    ccx = (card[0] + card[2]) / 2
    ccy = card[1] + (card[3] - card[1]) * 0.62
    print(int(pcx), int(pcy), int(pill[3]), int(ccx), int(ccy), dens or 2.6)
