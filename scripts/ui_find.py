"""Prints "x y" of the centre of the first node whose text or description contains argv[1] (dump read from stdin)."""
import re
import sys
import xml.etree.ElementTree as ET

needle = sys.argv[1].lower()
data = sys.stdin.read()
start = data.find("<?xml")
if start < 0:
    start = data.find("<hierarchy")
if start < 0:
    sys.exit(0)
root = ET.fromstring(data[start:])
for n in root.iter("node"):
    label = ((n.get("text") or "") + " " + (n.get("content-desc") or "")).lower()
    if needle in label:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
