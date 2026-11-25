#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 The Encircle contributors
#
# Computes the next version from Conventional Commit titles since the last stable release.
#
#   scripts/version.sh <beta|stable> [ref]
#
# Prints key=value lines (append them to $GITHUB_OUTPUT in workflows):
#   last       last stable tag, e.g. v0.1.1 (empty before the first release)
#   bump       major | minor | patch | none
#   base       next stable version, e.g. 0.2.0
#   name       versionName for this build, e.g. 0.2.0-beta.3 or 0.2.0
#   code       Android versionCode, e.g. 20003
#   tag        git tag for this build, e.g. v0.2.0-beta.3
#   releasable true when the range contains user-facing changes
#
# Rules (squash merges keep the pull request title as the commit subject):
#   feat                                    -> minor
#   fix, perf, refactor, revert, build(deps) -> patch
#   "type!:" or "BREAKING CHANGE" in body   -> major (minor while the version is 0.x)
#   docs, test, ci, chore, style, build     -> no release on their own
#
# versionCode = major * 1000000 + minor * 10000 + patch * 100 + n,
# where n is the beta number (1-98) for betas and 99 for the stable release.
set -euo pipefail

channel=${1:?usage: version.sh <beta|stable> [ref]}
ref=${2:-HEAD}
case "$channel" in beta | stable) ;; *) echo "channel must be beta or stable" >&2; exit 2 ;; esac

last=$(git tag --list 'v*' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)
if [ -n "$last" ]; then
  range="$last..$ref"
  IFS=. read -r major minor patch <<<"${last#v}"
else
  range="$ref"
  major=0 minor=0 patch=0
fi

bump=none
rank() { case "$1" in major) echo 3 ;; minor) echo 2 ;; patch) echo 1 ;; *) echo 0 ;; esac; }
raise() { if [ "$(rank "$1")" -gt "$(rank "$bump")" ]; then bump=$1; fi; }

# One record per commit: subject, newline, body, record separator.
while IFS= read -r -d $'\x1e' record; do
  record=${record#$'\n'}
  subject=${record%%$'\n'*}
  if [[ "$subject" =~ ^[a-z]+(\([^\)]*\))?!: ]] || [[ "$record" == *"BREAKING CHANGE"* ]]; then
    raise major
  elif [[ "$subject" =~ ^feat(\([^\)]*\))?: ]]; then
    raise minor
  elif [[ "$subject" =~ ^(fix|perf|refactor|revert)(\([^\)]*\))?: ]] || [[ "$subject" =~ ^build\(deps\): ]]; then
    raise patch
  fi
done < <(git log --no-merges --format='%s%n%b%x1e' "$range")

if [ "$bump" = major ] && [ "$major" -eq 0 ]; then bump=minor; fi
case "$bump" in
  major) major=$((major + 1)); minor=0; patch=0 ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  patch) patch=$((patch + 1)) ;;
  none) patch=$((patch + 1)) ;; # only used for display; nothing is released
esac
base="$major.$minor.$patch"

if [ "$channel" = beta ]; then
  count=$(git tag --list "v$base-beta.*" | grep -cE "^v$base-beta\.[0-9]+$" || true)
  n=$((count + 1))
  if [ "$n" -gt 98 ]; then echo "too many betas for $base" >&2; exit 1; fi
  name="$base-beta.$n"
else
  n=99
  name="$base"
fi

echo "last=$last"
echo "bump=$bump"
echo "base=$base"
echo "name=$name"
echo "code=$((major * 1000000 + minor * 10000 + patch * 100 + n))"
echo "tag=v$name"
echo "releasable=$([ "$bump" != none ] && echo true || echo false)"
