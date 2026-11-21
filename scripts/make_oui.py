#!/usr/bin/env python3
"""Builds app/src/main/assets/oui.txt ("AABBCC<TAB>Vendor" per line) for MAC vendor lookups.

Run by CI before building. Tries the IEEE registry first, then Wireshark's copy.
Exits non-zero if nothing could be downloaded; the app still works, just without vendor names.
"""
import csv, io, sys, urllib.request

SOURCES = [
    ("ieee", "https://standards-oui.ieee.org/oui/oui.csv"),
    ("manuf", "https://www.wireshark.org/download/automated/data/manuf"),
]

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (build script)"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", "replace")

def from_ieee(text):
    out = {}
    for row in csv.DictReader(io.StringIO(text)):
        a = (row.get("Assignment") or "").strip().upper()
        name = (row.get("Organization Name") or "").strip()
        if len(a) == 6 and name:
            out[a] = name
    return out

def from_manuf(text):
    out = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        prefix = parts[0].strip()
        if "/" in prefix or len(prefix) != 8:
            continue
        name = (parts[2] if len(parts) > 2 and parts[2].strip() else parts[1] if len(parts) > 1 else "").strip()
        if name:
            out[prefix.replace(":", "").upper()] = name
    return out

def main(dest):
    for kind, url in SOURCES:
        try:
            text = fetch(url)
            table = from_ieee(text) if kind == "ieee" else from_manuf(text)
            if len(table) < 10000:
                raise ValueError(f"only {len(table)} entries")
        except Exception as e:  # try the next source
            print(f"{url}: {e}", file=sys.stderr)
            continue
        with open(dest, "w", encoding="utf-8") as f:
            for k in sorted(table):
                f.write(f"{k}\t{' '.join(table[k].split())}\n")
        print(f"Wrote {len(table)} vendors from {url} to {dest}")
        return 0
    return 1

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
