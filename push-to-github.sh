#!/usr/bin/env bash
set -euo pipefail

REPO_URL="https://github.com/odje0118/znplugin.git"
WORKDIR="${1:-$HOME/znplugin-push}"

if [ -d "$WORKDIR/.git" ]; then
  echo "Using existing clone: $WORKDIR"
  git -C "$WORKDIR" fetch origin
  git -C "$WORKDIR" checkout main
  git -C "$WORKDIR" pull --ff-only origin main
else
  git clone "$REPO_URL" "$WORKDIR"
fi

# Copy this extracted plugin tree over the clone, while preserving .git.
SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"
rsync -a --delete --exclude='.git' "$SOURCE_DIR/" "$WORKDIR/"

git -C "$WORKDIR" add -A
if git -C "$WORKDIR" diff --cached --quiet; then
  echo "No changes to commit."
  exit 0
fi

git -C "$WORKDIR" commit -m "Import full Zweetnekken RuneLite plugin source"
git -C "$WORKDIR" push origin main
echo "Pushed to $REPO_URL"
