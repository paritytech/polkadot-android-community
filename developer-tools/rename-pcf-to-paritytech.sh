#!/usr/bin/env bash
#
# Renames the application package from io.pcf.polkadotapp -> io.paritytech.polkadotapp
# across the entire repository so that "pcf" is no longer referenced anywhere.
#
# Designed to be run repeatedly (e.g. after merging upstream that still uses io.pcf):
#   - idempotent: a second run with nothing to do changes nothing and exits 0
#   - merge-safe: files are moved one-by-one with `git mv` (history preserved); if a
#     destination already exists the move is skipped with a warning instead of clobbering
#   - self-excluding: this script keeps its own io.pcf literals so future runs still work
#
# Usage:
#   developer-tools/rename-pcf-to-paritytech.sh            # apply the rename
#   developer-tools/rename-pcf-to-paritytech.sh --dry-run  # show what would change, touch nothing
#   developer-tools/rename-pcf-to-paritytech.sh --check     # verify no pcf remains (exit 1 if it does)
#
# Only git-tracked files are touched (build/, .gradle/ and other ignored paths are skipped),
# so run this after committing or merging the work you want renamed.

set -euo pipefail

OLD_DOT="io.pcf.polkadotapp"
NEW_DOT="io.paritytech.polkadotapp"
OLD_SLASH="io/pcf/polkadotapp"
NEW_SLASH="io/paritytech/polkadotapp"
# Underscore form is how the package appears in JNI symbol names (Java_io_pcf_polkadotapp_...)
# inside the Rust bindings; it must track the Kotlin package or native calls fail to link.
OLD_UNDER="io_pcf_polkadotapp"
NEW_UNDER="io_paritytech_polkadotapp"
# Keystore self-signed certificate subject CN (a stray "pcf" literal, not package-derived).
OLD_CN="CN=pcf"
NEW_CN="CN=paritytech"

DRY_RUN=0
CHECK_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    --check)   CHECK_ONLY=1 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *) echo "Unknown argument: $arg" >&2; exit 2 ;;
  esac
done

cd "$(git rev-parse --show-toplevel)"

# Repo-relative path of this script, so we never rewrite our own literals.
SELF_REL="$(git ls-files --full-name -- "$0" 2>/dev/null || true)"
# Pathspec that excludes this script (empty when the script isn't tracked yet).
EXCLUDE_SELF=()
[ -n "$SELF_REL" ] && EXCLUDE_SELF=(":!${SELF_REL}")

say()  { printf '%s\n' "$*"; }
run()  { if [ "$DRY_RUN" -eq 1 ]; then printf '  [dry-run] %s\n' "$*"; else eval "$@"; fi; }

# ---------------------------------------------------------------------------
# --check: report-only, used by CI / pre-flight. Does not modify anything.
# ---------------------------------------------------------------------------
if [ "$CHECK_ONLY" -eq 1 ]; then
  remaining="$(git grep -lIF -e "$OLD_DOT" -e "$OLD_SLASH" -e "$OLD_UNDER" -e "$OLD_CN" -- "${EXCLUDE_SELF[@]+"${EXCLUDE_SELF[@]}"}" || true)"
  remaining_paths="$(git ls-files -- "*${OLD_SLASH}*" "*io.pcf.polkadotapp*" || true)"
  if [ -n "$remaining" ] || [ -n "$remaining_paths" ]; then
    say "pcf references still present:"
    [ -n "$remaining" ]       && say "$remaining"
    [ -n "$remaining_paths" ] && say "$remaining_paths"
    exit 1
  fi
  say "Clean: no io.pcf.polkadotapp references found."
  exit 0
fi

# ---------------------------------------------------------------------------
# 1. Move files out of   .../io/pcf/polkadotapp/...   into   .../io/paritytech/polkadotapp/...
#    (per-file git mv keeps history and is safe when paritytech/ already exists)
# ---------------------------------------------------------------------------
moved=0
skipped=0
while IFS= read -r -d '' f; do
  dest="${f/$OLD_SLASH/$NEW_SLASH}"
  [ "$dest" = "$f" ] && continue
  if [ -e "$dest" ]; then
    say "  WARN: destination exists, skipping (resolve by hand): $f -> $dest"
    skipped=$((skipped + 1))
    continue
  fi
  run "mkdir -p \"\$(dirname '$dest')\""
  run "git mv '$f' '$dest'"
  moved=$((moved + 1))
done < <(git ls-files -z -- "*${OLD_SLASH}*")
say "Slash-form file moves: $moved moved, $skipped skipped."

# ---------------------------------------------------------------------------
# 2. Move paths whose NAME carries the dotted package (e.g. the Room schema dir
#    database/schemas/io.pcf.polkadotapp.database.AppDatabase/).
# ---------------------------------------------------------------------------
dmoved=0
dskipped=0
while IFS= read -r -d '' f; do
  dest="${f/$OLD_DOT/$NEW_DOT}"
  [ "$dest" = "$f" ] && continue
  if [ -e "$dest" ]; then
    say "  WARN: destination exists, skipping (resolve by hand): $f -> $dest"
    dskipped=$((dskipped + 1))
    continue
  fi
  run "mkdir -p \"\$(dirname '$dest')\""
  run "git mv '$f' '$dest'"
  dmoved=$((dmoved + 1))
done < <(git ls-files -z -- "*io.pcf.polkadotapp*")
say "Dotted-name path moves: $dmoved moved, $dskipped skipped."

# ---------------------------------------------------------------------------
# 3. Rewrite file CONTENT across every tracked text file (both forms),
#    excluding this script so its literals survive for future runs.
# ---------------------------------------------------------------------------
content_files="$(git grep -lIF -e "$OLD_DOT" -e "$OLD_SLASH" -e "$OLD_UNDER" -e "$OLD_CN" -- "${EXCLUDE_SELF[@]+"${EXCLUDE_SELF[@]}"}" || true)"
if [ -n "$content_files" ]; then
  count="$(printf '%s\n' "$content_files" | wc -l | tr -d ' ')"
  say "Rewriting content in $count files."
  if [ "$DRY_RUN" -eq 1 ]; then
    printf '%s\n' "$content_files" | sed 's/^/  [dry-run] edit /'
  else
    # NUL-delimited list straight into xargs so filenames with spaces/newlines survive.
    git grep -lIzF -e "$OLD_DOT" -e "$OLD_SLASH" -e "$OLD_UNDER" -e "$OLD_CN" -- "${EXCLUDE_SELF[@]+"${EXCLUDE_SELF[@]}"}" \
      | xargs -0 perl -pi -e \
        's{\Qio.pcf.polkadotapp\E}{io.paritytech.polkadotapp}g; s{\Qio/pcf/polkadotapp\E}{io/paritytech/polkadotapp}g; s{\Qio_pcf_polkadotapp\E}{io_paritytech_polkadotapp}g; s{\QCN=pcf\E}{CN=paritytech}g;'
  fi
else
  say "No file content to rewrite."
fi

# ---------------------------------------------------------------------------
# 4. Drop now-empty io/pcf directories left behind by the moves.
# ---------------------------------------------------------------------------
if [ "$DRY_RUN" -eq 0 ]; then
  find . -type d -name pcf -path '*/io/pcf' -not -path '*/.git/*' -empty -delete 2>/dev/null || true
  # also remove an io/pcf that only contained the (now-moved) polkadotapp subtree
  find . -type d -name pcf -path '*/io/pcf' -not -path '*/.git/*' 2>/dev/null | while read -r d; do
    rmdir "$d" 2>/dev/null || true
  done
fi

# ---------------------------------------------------------------------------
# 5. Verify.
# ---------------------------------------------------------------------------
if [ "$DRY_RUN" -eq 1 ]; then
  say "Dry run complete. Nothing was modified."
  exit 0
fi

leftover="$(git grep -lIF -e "$OLD_DOT" -e "$OLD_SLASH" -e "$OLD_UNDER" -e "$OLD_CN" -- "${EXCLUDE_SELF[@]+"${EXCLUDE_SELF[@]}"}" || true)"
leftover_paths="$(git ls-files -- "*${OLD_SLASH}*" "*io.pcf.polkadotapp*" || true)"
if [ -n "$leftover" ] || [ -n "$leftover_paths" ]; then
  say ""
  say "WARNING: pcf references still remain (likely destination collisions above):"
  [ -n "$leftover" ]       && say "$leftover"
  [ -n "$leftover_paths" ] && say "$leftover_paths"
  exit 1
fi

say ""
say "Done. No io.pcf.polkadotapp references remain (this script excepted)."
say "Review with: git status   then   git diff --staged"
