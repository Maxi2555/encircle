#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The Encircle contributors
#
# Writes Markdown release notes from Conventional Commit titles.
#
#   scripts/release-notes.sh <from> [to]
#
# <from> is the previous tag (may be empty for "everything"), [to] defaults to HEAD.
# Entries are grouped by type and sorted alphabetically inside each group.
set -euo pipefail

from=${1-}
to=${2:-HEAD}
range=${from:+$from..}$to

declare -A groups=()
order=("Breaking changes" "Features" "Fixes" "Performance" "Other changes" "Maintenance")

while IFS= read -r -d $'\x1e' record; do
  record=${record#$'\n'}
  subject=${record%%$'\n'*}
  [[ "$subject" =~ ^([a-z]+)(\(([^\)]*)\))?(!)?:\ (.*)$ ]] || continue
  type=${BASH_REMATCH[1]}
  scope=${BASH_REMATCH[3]}
  breaking=${BASH_REMATCH[4]}
  text=${BASH_REMATCH[5]}
  text="${text^}"
  entry="- ${scope:+**$scope:** }$text"
  if [ -n "$breaking" ] || [[ "$record" == *"BREAKING CHANGE"* ]]; then
    group="Breaking changes"
  else
    case "$type" in
      feat) group="Features" ;;
      fix) group="Fixes" ;;
      perf) group="Performance" ;;
      refactor | revert) group="Other changes" ;;
      build) [[ "$scope" == deps ]] && group="Other changes" || group="Maintenance" ;;
      *) group="Maintenance" ;;
    esac
  fi
  groups[$group]+="$entry"$'\n'
done < <(git log --no-merges --format='%s%n%b%x1e' "$range")

printed=false
for group in "${order[@]}"; do
  [ -n "${groups[$group]-}" ] || continue
  if [ "$group" = Maintenance ]; then
    echo "<details><summary>Maintenance</summary>"
    echo
  else
    echo "### $group"
    echo
  fi
  # Alphabetical by the visible text, ignoring case and the Markdown around scopes.
  printf '%s' "${groups[$group]}" |
    awk '{ key = $0; sub(/^- /, "", key); gsub(/\*/, "", key); print tolower(key) "\t" $0 }' |
    LC_ALL=C sort -t $'\t' -k1,1 | cut -f2-
  echo
  [ "$group" = Maintenance ] && { echo "</details>"; echo; }
  printed=true
done

if [ "$printed" = false ]; then
  echo "No user-facing changes."
fi
