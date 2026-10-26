#!/usr/bin/env python3
"""Check tracked files/reachable history for private local identifiers; Gitleaks scans credentials separately.

Findings name only the path/rule, never the matched private value. Images still require visual review.
"""
import argparse
from pathlib import PurePosixPath
import re
import subprocess
import sys


def git(*args):
    return subprocess.check_output(["git", *args])


PRIVATE_HOME = re.compile(rb"/(?:Users|home)/[A-Za-z0-9_.-]+/")
PRIVATE_IP = re.compile(rb"(?<![\w.])(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(?![\w.])")
LEGACY_BRANDING = re.compile(rb"\bApple[ _-]?TV\b|\btvOS\b|com\.apple\.", re.I)
RULES = [("private home path", PRIVATE_HOME), ("private network address", PRIVATE_IP), ("legacy design branding", LEGACY_BRANDING)]


def forbidden(path):
    name = PurePosixPath(path).name.lower()
    return (name == "local.properties" or name == ".env" or name.startswith(".env.") and name != ".env.example"
            or name.endswith((".keystore", ".jks", ".p12", ".pfx", ".apk"))
            or name.startswith("tv-") and name.endswith((".png", ".jpg", ".jpeg")))


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--history", action="store_true", help="also inspect every blob reachable from all local refs")
args = parser.parse_args()
findings = set()
objects = {}
for raw in git("ls-files", "--stage", "-z").split(b"\0"):
    if not raw:
        continue
    metadata, path = raw.split(b"\t", 1)
    objects[metadata.split()[1].decode()] = path.decode("utf-8", "replace")
if args.history:
    for raw in git("rev-list", "--objects", "--all").splitlines():
        parts = raw.split(b" ", 1)
        if len(parts) == 2:
            objects[parts[0].decode()] = parts[1].decode("utf-8", "replace")
checked = 0
if args.history:
    for oid in git("rev-list", "--all").splitlines():
        data = git("cat-file", "commit", oid.decode())
        for label, pattern in RULES:
            if pattern.search(data):
                findings.add(("commit " + oid.decode()[:12], label))
for oid, path in objects.items():
    if forbidden(path):
        findings.add((path, "private artifact path"))
    if git("cat-file", "-t", oid).strip() != b"blob":
        continue
    data = git("cat-file", "blob", oid)
    checked += 1
    if b"\0" in data or path.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
        continue
    for label, pattern in RULES:
        if pattern.search(data):
            findings.add((path, label))
for path, label in sorted(findings):
    print(f"FAIL: {label}: {path}", file=sys.stderr)
if findings:
    sys.exit(1)
print(f"PASS: {checked} tracked/reachable blobs checked for private paths and addresses; review image contents separately")
