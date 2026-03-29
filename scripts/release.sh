#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLE_FILE="app/build.gradle.kts"
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

fail() {
  echo "error: $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: ./scripts/release.sh [version]

Without an argument, the script releases the version already set in app/build.gradle.kts.
With a version argument, the script updates versionName, increments versionCode by 1,
commits that change, builds app-debug.apk, pushes the current branch, creates tag v<version>,
and publishes a GitHub release with app-debug.apk attached.
EOF
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

read_gradle_value() {
  local pattern="$1"
  perl -ne "print \"\$1\n\" if /${pattern}/" "$GRADLE_FILE" | head -n 1
}

ensure_clean_tree() {
  local status
  status="$(git status --porcelain)"
  [[ -z "$status" ]] || fail "git working tree must be clean before releasing"
}

update_version() {
  local old_code="$1"
  local old_name="$2"
  local new_code="$3"
  local new_name="$4"

  perl -0pi -e \
    "s/versionCode = ${old_code}/versionCode = ${new_code}/; s/versionName = \"\Q${old_name}\E\"/versionName = \"${new_name}\"/;" \
    "$GRADLE_FILE"
}

main() {
  if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
  fi

  require_command git
  require_command gh
  require_command perl

  [[ -f "$GRADLE_FILE" ]] || fail "missing $GRADLE_FILE"
  [[ -x "./gradlew" ]] || fail "missing executable ./gradlew"

  ensure_clean_tree

  local current_version_code current_version_name target_version_name target_version_code
  current_version_code="$(read_gradle_value 'versionCode = ([0-9]+)')"
  current_version_name="$(read_gradle_value 'versionName = "([^"]+)"')"

  [[ -n "$current_version_code" ]] || fail "could not read versionCode from $GRADLE_FILE"
  [[ -n "$current_version_name" ]] || fail "could not read versionName from $GRADLE_FILE"

  target_version_name="${1:-$current_version_name}"
  target_version_code="$current_version_code"

  if [[ "$target_version_name" != "$current_version_name" ]]; then
    target_version_code="$((current_version_code + 1))"
    update_version "$current_version_code" "$current_version_name" "$target_version_code" "$target_version_name"
    git add "$GRADLE_FILE"
    git commit -m "Release v${target_version_name}"
  fi

  local branch tag release_url
  branch="$(git rev-parse --abbrev-ref HEAD)"
  [[ "$branch" != "HEAD" ]] || fail "detached HEAD is not supported"

  tag="v${target_version_name}"

  git rev-parse --verify "$tag" >/dev/null 2>&1 && fail "local tag already exists: $tag"
  git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1 && fail "remote tag already exists: $tag"
  gh release view "$tag" >/dev/null 2>&1 && fail "GitHub release already exists: $tag"

  ./gradlew assembleDebug
  [[ -f "$APK_PATH" ]] || fail "missing built APK at $APK_PATH"

  git push origin "$branch"
  git tag -a "$tag" -m "Release $tag"
  git push origin "$tag"

  gh release create "$tag" "$APK_PATH" --title "$tag" --generate-notes >/dev/null
  release_url="$(gh release view "$tag" --json url --jq '.url')"

  cat <<EOF
Released $tag
versionCode: $target_version_code
versionName: $target_version_name
asset: $APK_PATH
url: $release_url
EOF
}

main "$@"
