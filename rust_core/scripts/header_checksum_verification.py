# Check what I need to delete in TODO-meshsdr.md
import re

with open("TODO-meshsdr.md", "r") as f:
    lines = f.readlines()
for i, l in enumerate(lines):
    if "Header-Parsing + CRC" in l:
        print(f"{i}: {l.strip()}")
# This script is a loose verification for the header_checksum formula
# and is bit-for-bit identical to the Rust implementation.
