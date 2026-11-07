"""Prints the interesting nodes of a `uiautomator dump` (read from stdin): class, text, description, clickable, bounds."""
import sys
import xml.etree.ElementTree as ET

data = sys.stdin.read()
start = data.find("<?xml")
if start < 0:
    start = data.find("<hierarchy")
if start < 0:
    print("(no UI dump)")
    sys.exit(0)
root = ET.fromstring(data[start:])
for n in root.iter("node"):
    text = n.get("text") or ""
    desc = n.get("content-desc") or ""
    if text or desc or n.get("clickable") == "true":
        cls = (n.get("class") or "").split(".")[-1]
        print(f"{cls:16s} text={text!r} desc={desc!r} click={n.get('clickable')} {n.get('bounds')}")
