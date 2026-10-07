#!/bin/sh
# Releases Safanoria (README, "Releasing Safanoria"): the version in gradle.properties.
#
#   ./release.sh            # release it, then start the next minor version
#   ./release.sh 1.0.0      # ... or start this version instead
#   ./release.sh -y         # don't ask before starting
#
# On main, with nothing uncommitted: runs the tests, stamps this repository's tickets, moves the
# README's pinned version, commits "Release Safanoria X.Y.Z" and tags it vX.Y.Z; sets the next version,
# commits "Start X.Y.Z"; pushes main and the tag. The tag makes the `release` workflow build
# and publish the binaries. Nothing is pushed until every step before has worked.
set -eu

cd "$(dirname "$0")"

yes=
next=
for arg in "$@"; do
  case "$arg" in
    -y|--yes) yes=1 ;;
    -h|--help) sed -n '2,11s/^# \{0,1\}//p' "$0"; exit 0 ;;
    -*) echo "release: unknown option $arg" >&2; exit 2 ;;
    *) next="$arg" ;;
  esac
done

fail() { echo "release: $*" >&2; exit 1; }
is_version() { echo "$1" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$'; }

version=$(sed -n 's/^version=//p' gradle.properties)
is_version "$version" || fail "gradle.properties version '$version' is not MAJOR.MINOR.PATCH"
if [ -z "$next" ]; then
  major=${version%%.*}; minor=${version#*.}; minor=${minor%%.*}
  next="$major.$((minor + 1)).0"
fi
is_version "$next" || fail "next version '$next' is not MAJOR.MINOR.PATCH"
[ "$next" != "$version" ] || fail "the next version is the one being released ($version)"
tag="v$version"

command -v safanoria-cli >/dev/null 2>&1 || fail "safanoria-cli is not installed (make install-cli)"
branch=$(git symbolic-ref --short -q HEAD || echo HEAD)
[ "$branch" = main ] || fail "on $branch, not main"
[ -z "$(git status --porcelain)" ] || fail "there are uncommitted changes"
git fetch --quiet --tags origin main
git merge-base --is-ancestor origin/main HEAD || fail "main is behind origin/main: pull first"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  fail "tag $tag exists: set gradle.properties version to the release"
fi

echo "Release Safanoria $version, then start $next:"
safanoria-cli release safanoria --dry-run
echo "pushes $(git rev-list --count origin/main..HEAD) commit(s) already on main, the two new ones and $tag to origin"
if [ -z "$yes" ]; then
  printf 'Go ahead? [y/N] '
  read -r answer
  case "$answer" in y|Y|yes) ;; *) fail "cancelled, nothing changed" ;; esac
fi

./gradlew allTests

# Until the push, a failure leaves only local changes: git status shows them.
safanoria-cli release safanoria
# The Action example and the line after it pin a version: the one now released.
sed -i.bak -E \
  -e "s/@v[0-9]+\.[0-9]+\.[0-9]+/@v$version/g" \
  -e "s/(with: version: )[0-9]+\.[0-9]+\.[0-9]+/\1$version/g" README.md
rm README.md.bak
git commit --quiet -am "Release Safanoria $version"
git tag "$tag"

sed -i.bak "s/^version=.*/version=$next/" gradle.properties
rm gradle.properties.bak
git commit --quiet -am "Start $next"

git push --atomic origin main "$tag"
echo "released $version: https://github.com/mateuy-dev/safanoria/actions/workflows/release.yml builds and publishes it"
