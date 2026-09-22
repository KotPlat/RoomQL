#!/usr/bin/env bash
#
# Verifies that RoomQL's published artifacts contain no reflection and derive nothing
# from class or member names at runtime.
#
# This is what makes RoomQL's "R8/ProGuard needs no extra keep rules" claim true, so it
# is checked rather than asserted. The claim holds for a specific reason: the KSP
# processor bakes column and table names into generated code as *string literals*
# (`Column("created_at", "users")`), so R8 renaming a class or field can never corrupt
# the SQL the DSL emits. The moment something here calls Class.getSimpleName() or looks
# a member up reflectively, that stops being true and consumers need keep rules — this
# script fails the build at that moment instead of letting it ship.
#
# Scans the real published artifacts rather than build output, so it checks what users
# actually resolve.
#
# Usage: scripts/verify-no-reflection.sh [version]     (default: the latest X.Y.Z release tag)

set -euo pipefail

VERSION="${1:-$("$(dirname "$0")/latest-release-version.sh")}"
if [[ -z "$VERSION" ]]; then
    echo "error: no version given and no X.Y.Z release tag found in history" >&2
    exit 1
fi
REPO="${HOME}/.m2/repository/io/github/kotplat/roomql"

RUNTIME_JAR="${REPO}/runtime/${VERSION}/runtime-${VERSION}.jar"
ANDROID_AAR="${REPO}/runtime-android/${VERSION}/runtime-android-${VERSION}.aar"

for artifact in "$RUNTIME_JAR" "$ANDROID_AAR"; do
    if [[ ! -f "$artifact" ]]; then
        echo "error: missing $artifact" >&2
        echo "hint: run 'VERSION=${VERSION} ./gradlew publishToMavenLocal' first" >&2
        exit 1
    fi
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

unzip -q "$RUNTIME_JAR" -d "$WORK/runtime"
unzip -q "$ANDROID_AAR" -d "$WORK/aar"
unzip -q "$WORK/aar/classes.jar" -d "$WORK/runtime-android"

# Anything that reads a name off a class/member at runtime, or loads a type by name.
# These are exactly the constructs that break under obfuscation, or that oblige a
# library to ship keep rules on its consumers' behalf.
FORBIDDEN='java/lang/reflect/|kotlin/reflect/|kotlin/jvm/internal/Reflection|java/lang/Class\.forName|java/lang/Class\.getName|java/lang/Class\.getSimpleName|java/lang/Class\.getCanonicalName|getDeclaredField|getDeclaredMethod|getDeclaredConstructor|java/util/ServiceLoader|java/lang/ClassLoader\.loadClass'

violations=0
scanned=0

while IFS= read -r class_file; do
    scanned=$((scanned + 1))
    hits="$(javap -p -c "$class_file" 2>/dev/null | grep -oE "$FORBIDDEN" | sort -u || true)"
    if [[ -n "$hits" ]]; then
        violations=$((violations + 1))
        rel="${class_file#"$WORK"/}"
        echo "FAIL $rel"
        echo "$hits" | sed 's/^/       /'
    fi
done < <(find "$WORK/runtime" "$WORK/runtime-android" -name '*.class' | sort)

echo
if (( violations > 0 )); then
    echo "verify-no-reflection: $violations of $scanned classes use reflection or name derivation." >&2
    echo "RoomQL's no-keep-rules guarantee does not hold for this build." >&2
    echo "Either remove the usage, or ship the necessary rules in" >&2
    echo "runtime-android/consumer-rules.pro and correct the claim in README.md." >&2
    exit 1
fi

echo "verify-no-reflection: OK — $scanned classes scanned across runtime and"
echo "runtime-android ${VERSION}; no reflection, no name derivation."
echo "Consumers need no RoomQL-specific keep rules."
