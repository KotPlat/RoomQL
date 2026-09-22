#!/usr/bin/env bash
# Builds and tests the demo on newer Kotlin/KSP/Room toolchains; too slow for CI, so run it before each release.
# Usage: scripts/verify-toolchains.sh

set -euo pipefail

# kotlin  ksp  room  useKSP2 — the baseline (2.0.21, KSP1, Room 2.6.1) is already covered by CI.
TOOLCHAINS=(
    "2.1.21 2.1.21-2.0.1 2.6.1 false"
    "2.2.0  2.2.0-2.0.2  2.7.2 true"
)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="0.0.0-toolchains"
M2="${HOME}/.m2/repository/io/github/kotplat/roomql"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK" "$M2"/*/"$VERSION"' EXIT

echo "Publishing this checkout as $VERSION to Maven local..."
(cd "$ROOT" && VERSION="$VERSION" ./gradlew -q \
    :runtime:publishToMavenLocal :runtime-android:publishToMavenLocal :ksp-processor:publishToMavenLocal)

failed=0
results=()
for toolchain in "${TOOLCHAINS[@]}"; do
    read -r kotlin ksp room ksp2 <<< "$toolchain"
    label="Kotlin $kotlin, KSP $ksp ($([[ $ksp2 == true ]] && echo KSP2 || echo KSP1)), Room $room"
    dir="$WORK/demo-$kotlin-$ksp2"

    rsync -a --exclude build --exclude .gradle "$ROOT/demo/" "$dir/"
    # -i.bak works on both GNU and BSD sed.
    sed -i.bak \
        -e "s/^kotlin = .*/kotlin = \"$kotlin\"/" \
        -e "s/^ksp = .*/ksp = \"$ksp\"/" \
        -e "s/^room = .*/room = \"$room\"/" \
        -e "s/^roomql = .*/roomql = \"$VERSION\"/" \
        "$dir/gradle/libs.versions.toml"
    echo "ksp.useKSP2=$ksp2" >> "$dir/gradle.properties"

    echo "Building demo on $label..."
    if "$dir/gradlew" -p "$dir" assembleDebug testDebugUnitTest --console=plain > "$dir/build.log" 2>&1; then
        results+=("PASS  $label")
    else
        failed=$((failed + 1))
        results+=("FAIL  $label")
        tail -n 40 "$dir/build.log" >&2
    fi
done

echo
printf '%s\n' "${results[@]}"
if (( failed > 0 )); then
    echo "verify-toolchains: $failed toolchain(s) failed; fix them or drop them from the README's tested list." >&2
    exit 1
fi
echo "verify-toolchains: OK"
