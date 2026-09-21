#!/usr/bin/env bash
#
# Builds release jars and collects them in one folder.
#
# Usage:
#   scripts/build-release.sh                    # everything
#   scripts/build-release.sh --mc 1.20.1        # one Minecraft version
#   scripts/build-release.sh --loader fabric    # one loader, all versions
#   scripts/build-release.sh --mc 1.21.1 --loader neoforge
#
# Output: build/release/<mod_version>/
#
# Filters exist because a full build remaps Minecraft for every node, which is slow and
# memory-hungry. When iterating on one loader there is no reason to pay for the rest.

set -euo pipefail

cd "$(dirname "$0")/.."

mc_filter=""
loader_filter=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mc)     mc_filter="$2"; shift 2 ;;
    --loader) loader_filter="$2"; shift 2 ;;
    -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

mod_version="$(sed -n 's/^mod_version *= *//p' gradle.properties | tr -d '[:space:]')"
out="build/release/$mod_version"

# Node names come from settings.gradle.kts rather than being listed here, so adding a
# Minecraft version or a loader never needs this script edited to match.
mapfile -t nodes < <(
  sed -n 's/.*"\([0-9][^"]*-[a-z]*\)" *to *"[^"]*".*/\1/p' settings.gradle.kts
)

selected=()
for node in "${nodes[@]}"; do
  node_mc="${node%-*}"
  node_loader="${node##*-}"
  [[ -n "$mc_filter"     && "$node_mc"     != "$mc_filter"     ]] && continue
  [[ -n "$loader_filter" && "$node_loader" != "$loader_filter" ]] && continue
  selected+=("$node")
done

if [[ ${#selected[@]} -eq 0 ]]; then
  echo "No nodes match those filters. Known nodes: ${nodes[*]}" >&2
  exit 1
fi

echo "Building $mod_version: ${selected[*]}"

tasks=()
for node in "${selected[@]}"; do
  tasks+=(":$node:build")
done
./gradlew "${tasks[@]}"

rm -rf "$out"
mkdir -p "$out"

for node in "${selected[@]}"; do
  # Sources jars are published to Maven, not shipped to players.
  for jar in "versions/$node/build/libs/"*.jar; do
    [[ "$jar" == *-sources.jar ]] && continue
    cp "$jar" "$out/"
  done
done

echo
echo "Release jars in $out:"
ls -1sh "$out"
