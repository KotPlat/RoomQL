#!/usr/bin/env bash
# Prints the newest strict X.Y.Z tag; `git describe` breaks ties between tags sharing a commit and backup-* tags must not win.
set -euo pipefail
git tag --list --sort=-v:refname | grep -E '^[0-9]+\.[0-9]+\.[0-9]+$' | head -n1 || true
