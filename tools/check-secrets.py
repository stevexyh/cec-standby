#!/usr/bin/env python3
"""Scan only Git-indexed text; never print matched secret contents."""
import re
import subprocess
import sys

patterns = [r"/" + "Users/", r"192\.168\.\d+\.\d+", r"10\.\d+\.\d+\.\d+", "-" * 5 + r"BEGIN .*PRIVATE KEY", r"gh[pousr]_[A-Za-z0-9]{20,}", r"github_pat_[A-Za-z0-9_]{20,}", r"AKIA[A-Z0-9]{16}"]
files = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
issues = []
for name in filter(None, files):
    content = subprocess.check_output(["git", "show", ":" + name])
    if name.startswith(("local/", ".keys/", "build/")) or name.endswith((".apk", ".jks", ".keystore", ".pk8", ".pem")):
        issues.append(name + ": excluded artifact")
    if b"\0" in content:
        issues.append(name + ": binary")
        continue
    text = content.decode("utf-8")
    if any(re.search(pattern, text) for pattern in patterns):
        issues.append(name + ": sensitive pattern")
if issues:
    print("\n".join(issues), file=sys.stderr)
    sys.exit(1)
print("Indexed text scan passed; manual review is still required")
