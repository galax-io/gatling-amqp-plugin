#!/usr/bin/env bash
#
# check-linkage.sh — verify the issue ↔ PR ↔ milestone contract (see AGENTS.md "Milestones (ALWAYS)").
# Run `scripts/check-linkage.sh --help` for full usage.

set -euo pipefail

usage() {
  cat <<'EOF'
check-linkage.sh — verify the issue <-> PR <-> milestone contract (see AGENTS.md "Milestones (ALWAYS)").

What each entity owes (this script enforces it):
  Issue      an important task, not a technical one; belongs to exactly one milestone;
             closed only when its fix is on main.
  PR         carries a milestone. One that completes an important task links its
             issue with a real closing link (Closes owner/repo#N); one PR of several
             writes "Part of owner/repo#N". A linked issue sits in the PR's milestone.
             A technical PR (sync, pins, docs, CI) links no issue: its commit is the record.
  Milestone  one release (vX.Y.Z); tag only when every issue is closed, every PR that
             links one is merged, and no PR is still open.
  Feature    an issue in the backlog repository whose milestone says "Release: business"
             on a line of its own. It sits in that business milestone, not in the version
             milestone of the PR that does a part of it, so a link to a feature skips the
             same-milestone rule. The PR still carries its own version milestone.

Usage:
  scripts/check-linkage.sh --pr <N>          # GATE one PR: milestone + any linked issue in the same milestone
  scripts/check-linkage.sh --for-tag vX.Y.Z  # GATE a release: tag-readiness of that version's milestone
  scripts/check-linkage.sh [milestone]       # audit a milestone (default: lowest-numbered open)
  scripts/check-linkage.sh --tag [ms]        # also assert tag-readiness (issues closed, linked PRs merged, none open)
  scripts/check-linkage.sh --help

Env:
  REPO=owner/name          # override repo (default: gh repo view of the current checkout)
  BACKLOG_REPO=owner/name  # the repository whose business milestones hold features
                           # (default: galax-io/galaxio-sdlc-backlog)

Exit: 0 all rules hold | 1 at least one violation | 2 usage/prereq error.
EOF
}

for bin in gh jq; do
  command -v "$bin" >/dev/null 2>&1 || { echo "error: '$bin' not found on PATH" >&2; exit 2; }
done

REPO="${REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)}"
[ -n "$REPO" ] || { echo "error: cannot determine repo — run inside a GitHub checkout or set REPO=owner/name" >&2; exit 2; }

BACKLOG_REPO="${BACKLOG_REPO:-galax-io/galaxio-sdlc-backlog}"

TAG_MODE=0
MS=""
PR_NUM=""
FOR_TAG=""
while [ $# -gt 0 ]; do
  case "$1" in
    --tag)      TAG_MODE=1 ;;
    --pr)       shift; PR_NUM="${1:-}"
                [[ "$PR_NUM" =~ ^[0-9]+$ ]] || { echo "error: --pr needs a numeric PR id" >&2; exit 2; } ;;
    --for-tag)  shift; FOR_TAG="${1:-}"
                [[ "$FOR_TAG" =~ ^v?[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "error: --for-tag needs vX.Y.Z" >&2; exit 2; } ;;
    --help|-h)  usage; exit 0 ;;
    [0-9]*)     MS="$1" ;;
    *)          echo "error: unknown argument '$1'" >&2; usage; exit 2 ;;
  esac
  shift
done

# Issues may live in another repository (the SDLC backlog), so every reference is
# owner/repo#N and is looked up in its own repository. A bare #N means this one.
closing_refs() { # pr-json -> registered GitHub closing links
  jq -r '.closingIssuesReferences[]? | "\(.repository.owner.login)/\(.repository.name)#\(.number)"' <<<"$1"
}
body_refs() { # pr-json, regex of what precedes a link -> "[owner/repo]#N" links in the body
  jq -r '.body // ""' <<<"$1" \
    | grep -oiE "$2([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)?#[0-9]+" \
    | grep -oE '([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)?#[0-9]+$' \
    | sed "s|^#|$REPO#|" || true
}
issue_ms() { # owner/repo#N -> the issue's milestone title
  gh issue view "${1##*#}" --repo "${1%#*}" --json milestone -q '.milestone.title // ""' 2>/dev/null || echo ""
}
feature_ms() { # owner/repo#N -> the business milestone's title when the issue is a feature of the backlog
  [ "${1%#*}" = "$BACKLOG_REPO" ] || return 0
  gh issue view "${1##*#}" --repo "${1%#*}" --json milestone -q '
    select(.milestone != null and ((.milestone.description // "")
      | test("(^|\n)[ \t]*Release:[ \t]*business[ \t]*\r?(\n|$)"; "i"))) | .milestone.title' 2>/dev/null || true
}
pr_links() { # pr-json -> its issue links: registered closing links, else "Part of" lines
  local refs
  refs=$(closing_refs "$1")
  [ -n "$refs" ] || refs=$(body_refs "$1" "$PART_OF")
  printf '%s' "$refs"
}
# "Part of owner/repo#N" marks one PR per repository of a multi-repository change. It is
# this project's convention, not a GitHub keyword, so it counts only at the start of a
# line: prose such as "part of #190's follow-up" links nothing.
PART_OF='^[[:space:]]*part of +'
# GitHub's closing keywords start a word and may take a colon: "prefix #5" closes nothing.
CLOSES='(^|[^[:alnum:]_])(close[sd]?|fix(e[sd])?|resolve[sd]?):? +'

# Gate one PR (the merge gate). Fails if the PR is missing a milestone or links an
# issue in a different milestone. A PR that links no issue passes: a technical PR
# needs none. A link is a registered GitHub closing link, or "Part of owner/repo#N"
# for one PR of several. A Closes in the body of a PR that does not target the
# default branch still fails: GitHub links closing keywords only there, so merging
# would not close that issue. On the default branch, a Closes GitHub did not link
# names no issue (a pull request, say) and is not a link (the lenient text
# fallback is audit-mode only).
if [ -n "$PR_NUM" ]; then
  pj=$(gh pr view "$PR_NUM" --repo "$REPO" --json number,title,state,milestone,closingIssuesReferences,body,baseRefName) \
    || { echo "error: PR #$PR_NUM not found in $REPO" >&2; exit 2; }
  p_title=$(jq -r '.title' <<<"$pj")
  p_ms=$(jq -r '.milestone.title // ""' <<<"$pj")
  p_links=$(pr_links "$pj")
  e=0
  printf 'PR #%s  %s\n' "$PR_NUM" "$p_title"
  if [ -z "$p_ms" ]; then printf '  ✗ no milestone — assign one (gh pr edit %s --milestone "…")\n' "$PR_NUM"; e=1
  else printf '  ✓ milestone: %s\n' "$p_ms"; fi
  if [ -z "$p_links" ]; then
    p_text=$(body_refs "$pj" "$CLOSES")
    p_base=$(jq -r '.baseRefName' <<<"$pj")
    default=$p_base
    if [ -n "$p_text" ]; then
      default=$(gh api "repos/$REPO" --jq '.default_branch') \
        || { echo "error: cannot read the default branch of $REPO" >&2; exit 2; }
    fi
    if [ "$p_base" != "$default" ]; then
      printf '  ✗ the body closes %s, but the PR targets %s, not %s, and GitHub links closing keywords only on the default branch, so merging would not close it — retarget the PR, or write "Part of …"\n' \
        "$(paste -sd, - <<<"$p_text")" "$p_base" "$default"; e=1
    else
      printf '  ✓ links no issue (a technical PR needs none)\n'
    fi
  else
    for i in $p_links; do
      i_feature=$(feature_ms "$i")
      i_ms=$(issue_ms "$i")
      if [ -n "$i_feature" ]; then printf '  ✓ links %s (feature of business milestone "%s")\n' "$i" "$i_feature"
      elif [ -z "$p_ms" ]; then printf '  - links %s (its milestone is "%s")\n' "$i" "${i_ms:-none}"
      elif [ "$i_ms" = "$p_ms" ]; then printf '  ✓ links %s (same milestone)\n' "$i"
      else printf '  ✗ links %s but its milestone is "%s", not "%s"\n' "$i" "${i_ms:-none}" "$p_ms"; e=1; fi
    done
  fi
  if [ "$e" = 0 ]; then printf 'PASS: PR #%s is well-formed.\n' "$PR_NUM"; exit 0; fi
  printf 'FAIL: PR #%s is malformed — fix the above before merge.\n' "$PR_NUM"; exit 1
fi

# Map a release version (vX.Y.Z) to its milestone, then assert tag-readiness on it.
# Patch versions map to the vX.Y.0 milestone (e.g. v1.23.1 -> "v1.23.0 …").
if [ -n "$FOR_TAG" ]; then
  v="${FOR_TAG#v}"; minor="v${v%.*}.0"
  MS=$(gh api "repos/$REPO/milestones?state=all&per_page=100" \
        | jq -r --arg t "$minor" 'map(select(.title | startswith($t))) | .[0].number // empty')
  [ -n "$MS" ] || { echo "error: no milestone whose title starts with '$minor' for tag $FOR_TAG" >&2; exit 2; }
  TAG_MODE=1
fi

# Default to the lowest-numbered open milestone (the "active" one).
if [ -z "$MS" ]; then
  MS=$(gh api "repos/$REPO/milestones?state=open&per_page=100" --jq 'sort_by(.number) | .[0].number // empty')
  [ -n "$MS" ] || { echo "error: no open milestone found in $REPO" >&2; exit 2; }
fi

ms_json=$(gh api "repos/$REPO/milestones/$MS") || { echo "error: milestone #$MS not found in $REPO" >&2; exit 2; }
ms_title=$(jq -r '.title' <<<"$ms_json")
ms_state=$(jq -r '.state' <<<"$ms_json")

errors=0
warns=0
err()  { printf '  ✗ %s\n' "$1"; errors=$((errors + 1)); }
warn() { printf '  ! %s\n' "$1"; warns=$((warns + 1)); }
ok()   { printf '  ✓ %s\n' "$1"; }

printf 'Repo:      %s\n' "$REPO"
printf 'Milestone: #%s  %s  (%s)\n' "$MS" "$ms_title" "$ms_state"
[ "$TAG_MODE" = 1 ] && printf 'Mode:      tag-readiness\n'
printf '\n'

# All issues + PRs carrying this milestone (REST returns both; PRs carry a .pull_request key).
items=$(gh api --paginate --slurp "repos/$REPO/issues?milestone=$MS&state=all&per_page=100" | jq 'add')

pr_numbers=$(jq -r '.[] | select(.pull_request != null) | .number' <<<"$items")
issue_numbers=$(jq -r '.[] | select(.pull_request == null) | .number' <<<"$items")

linked_issues=" "   # space-delimited set of issue numbers a PR points at

printf 'Pull requests\n'
if [ -z "$pr_numbers" ]; then
  warn "no PRs carry milestone #$MS yet"
fi
for pr in $pr_numbers; do
  pr_json=$(gh pr view "$pr" --repo "$REPO" --json number,title,state,milestone,closingIssuesReferences,body)
  pr_state=$(jq -r '.state' <<<"$pr_json")
  pr_title=$(jq -r '.title' <<<"$pr_json")

  # The PR's links, else a text fallback for Closes/Fixes/Resolves [owner/repo]#N
  # in the body.
  ref_nums=$(pr_links "$pr_json")
  if [ -z "$ref_nums" ]; then
    ref_nums=$(body_refs "$pr_json" "$CLOSES")
    [ -n "$ref_nums" ] && warn "PR #$pr links via body text only (not a registered GitHub closing link): $pr_title"
  fi

  # A technical PR (sync, pins, docs, CI — a bot's too) links no issue and needs
  # none. Before a tag it must still be merged or closed.
  if [ -z "$ref_nums" ]; then
    if [ "$TAG_MODE" = 1 ] && [ "$pr_state" = OPEN ]; then
      err "PR #$pr is OPEN — merge or close it before tagging: $pr_title"
    else
      ok "PR #$pr ($pr_state) → no issue (technical)"
    fi
    continue
  fi

  pr_ok=1
  for ri in $ref_nums; do
    linked_issues="$linked_issues$ri "
    [ -z "$(feature_ms "$ri")" ] || continue   # a feature sits in its business milestone
    ri_ms=$(issue_ms "$ri")
    if [ "$ri_ms" != "$ms_title" ]; then
      err "PR #$pr links issue $ri but that issue's milestone is '${ri_ms:-none}', not '$ms_title'"
      pr_ok=0
    fi
  done

  if [ "$TAG_MODE" = 1 ] && [ "$pr_state" != "MERGED" ]; then
    err "PR #$pr is $pr_state — must be MERGED before tagging: $pr_title"
  elif [ "$pr_ok" = 1 ]; then
    ok "PR #$pr ($pr_state) → $(echo "$ref_nums" | paste -sd, -)"
  fi
done

printf '\nIssues\n'
if [ -z "$issue_numbers" ]; then
  warn "no issues carry milestone #$MS"
fi
for is in $issue_numbers; do
  is_state=$(jq -r --argjson n "$is" '.[] | select(.number == $n) | .state' <<<"$items")
  case "$linked_issues" in
    *" $REPO#$is "*) linked="linked" ;;
    *)         linked="" ;;
  esac

  if [ "$TAG_MODE" = 1 ] && [ "$is_state" != "closed" ]; then
    err "issue #$is is $is_state — must be closed before tagging"
  elif [ -z "$linked" ]; then
    warn "issue #$is ($is_state) has no PR closing it"
  else
    ok "issue #$is ($is_state) ← linked"
  fi
done

printf '\n'
if [ "$errors" -gt 0 ]; then
  printf 'FAIL: %d error(s), %d warning(s).\n' "$errors" "$warns"
  exit 1
fi
printf 'PASS: 0 errors, %d warning(s).\n' "$warns"
[ "$TAG_MODE" = 1 ] && printf 'Milestone #%s is tag-ready.\n' "$MS"
exit 0
