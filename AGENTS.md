# gatling-amqp-plugin — Agent Guide

AMQP/RabbitMQ protocol plugin for Gatling. Published library — treat all public APIs as compatibility-sensitive.

## Role
Principal Engineer in software development and performance testing. Strong Scala, Java, Kotlin, Gatling plugin, and AMQP/RabbitMQ expertise. Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

**Constitution:** the principles of this repository are in `.specify/memory/constitution.md`; where they conflict with this file, the constitution wins.

## Stack
- Scala 2.13.x, sbt 2.0.x, Gatling 3.13.x, Java 17+
- AMQP/RabbitMQ plugin: publish, request-reply, and consume flows
- Java API facade with Kotlin-compatible usage and tests
- RabbitMQ Java client, Testcontainers RabbitMQ, Codecov, Sonatype

## Commands

```bash
# install   sbt update Test/compile scalafmtCheckAll scalafmtSbtCheck mimaReportBinaryIssues coverage update coverageOff
# test      sbt test
```

Format:
```
sbt scalafmtAll scalafmtSbt
```

Verify:
```
sbt scalafmtCheckAll scalafmtSbtCheck clean compile test
```

## Design Rules

Keep architecture simple: protocol config builds shared AMQP components, actions call the client layer, checks map broker responses back into Gatling sessions.

AMQP interactions are stateful — review connection lifecycle, channel reuse, acknowledgements, publisher confirms, and error propagation carefully. Treat Scala DSL, Java builders, defaults, and plugin semantics as compatibility-sensitive.

```scala
// ✅ — narrow interface, inject dependencies
class AmqpPublisher(channel: AmqpChannelPool, tracker: AmqpResponseTracker)
// ❌ — constructs its own internals, merges concerns
class AmqpPublisher { val channel = new AmqpChannelPool(); ... }
```

## Boundaries

✅ Always:
- Run `sbt scalafmtAll` before committing
- Branch from `main`; keep commits semantic and green
- Preserve backward compatibility for published Scala and Java APIs
- Prefer integration tests against a real RabbitMQ broker for runtime validation
- Treat `build.sbt`, `project/Dependencies.scala`, `project/plugins.sbt` as source of truth

⚠️ Ask first:
- Adding or upgrading dependencies
- Changing public API signatures or DSL behavior
- Editing another repository
- Any change to release or publish workflow

🚫 Never:
- Force-push or commit directly to `main`
- Add merge commits to PR branches (rebase-oriented history)
- Commit knowingly broken code to `main`
- Add opportunistic refactors outside task scope
- Mock RabbitMQ/Gatling internals where a real integration path exists

## PR Workflow
1. Branch from `main`.
2. Run verify commands before commit.
3. Keep commits semantic and green.
4. Prefer rebase-oriented history; avoid merge commits in PR branches.
5. CI in `.github/workflows/ci.yml` checks formatting, compile, tests, and coverage.
6. Releases are manual and tag-driven: push a `vX.Y.Z` tag on `main` or `release/*` and `.github/workflows/release.yml` publishes to Sonatype and cuts the GitHub Release. Merging to `main` does not release. The tag must be pushed by a human — GitHub does not start workflow runs for refs pushed with `GITHUB_TOKEN`, so a CI-created tag would never trigger the release.

## Repo Notes
- `build.sbt`, `project/Dependencies.scala`, and `project/plugins.sbt` are the source of truth for build and dependency behavior.
- Changes in `client/`, channel tracking, or action execution can affect both correctness and observability under load.
- Real RabbitMQ behavior is usually more valuable than mocks here.
- Three git tags were never published to Maven Central and have no corresponding
  artifact: `v0.12.1`, `v0.13.0-latest`, `v1.0.5`. Each is superseded by a real
  published version immediately after it in sequence (`v0.14.0`, `v0.13.0`, and
  `v1.0.6` respectively -- `v1.0.6` shares `v1.0.5`'s exact source commit).
  These are historical gaps from the old main-push-triggered release mechanism
  and the tag/publish split it was later replaced with (see #76); do not treat
  them as installable versions, and do not attempt to republish them -- Maven
  Central publishes are immutable, and republishing against a year-plus-old
  commit risks an artifact built against stale dependency resolutions.

## Feature milestones (ALWAYS)

A feature is an issue in `galax-io/galaxio-sdlc-backlog`. It belongs to exactly one backlog milestone whose description has the line `Release: business`: that milestone is its release.

- **Trunk-based.** `main` is the trunk: every PR, a feature's, a fix, a bot's or CI, is opened into `main` from a short-lived branch. Unfinished behaviour is not merged or stays behind a flag. The PR of a feature writes `Part of galax-io/galaxio-sdlc-backlog#<feature>`, which records it in the feature.
- **Work enters through the backlog.** A bug, an audit finding or a request is not worked on until it belongs to a feature: grouped into one or given one. An issue from outside stays in its repository and carries the label `status:needs-feature` until it is linked to a feature in the backlog.
- **Release.** The backlog's `scripts/release-build.sh <release>` gives this repository the next minor, `vX.Y.0`, when `main` has commits after its last tag, and runs the phase `prepare` in every such repository: `release/X.Y.0` is cut from `main` and this repository's checks run on it. Only when every repository is green does it run `publish` everywhere: the tag is set on the head `prepare` checked and the GitHub Release is made; `main` is not touched and the branch stays. A red check in one repository publishes nothing anywhere. The backlog's release of the milestone lists each repository's version and links its GitHub Release.
- **Patch.** A fix of a released version is a PR into `main` first, then `git cherry-pick -x` onto `release/X.Y.0` by a PR into that branch; `prepare` and `publish` with `vX.Y.Z` tag it there. A release branch is never merged into `main`.
- **Feature done.** The feature's documents PR in the backlog writes `Closes galax-io/galaxio-sdlc-backlog#<feature>` and is merged last; the work's PRs here write `Part of`.
- **Published releases are not edited.** A fix after the release is a patch on the release branch or the next release; a tag is never moved.

## Labels (ALWAYS)

One label scheme for every repository of the organization: `scripts/labels.json` in `galax-io/galaxio-sdlc-backlog`. A label says what the milestone and the feature link do not: the kind of work, its topic, how bad it is, where it stands.

- **Names.** Every label is `group:value`. Labels a tool owns (GitHub's `good first issue` and `help wanted`, Dependabot's, and those code reads, listed as `reserved`) are left alone.
- **Exactly one `type:*` on every open issue:** `bug`, `enhancement`, `docs`, `maintenance`, `question`; in the backlog `type:feature` (an issue of a business milestone) or `type:standing` (the standing feature of an older milestone). An issue without a type is not triaged.
- **At most one each:** `severity:*` (`critical`, `high`, `medium`, `low`: the impact, not the order of work), `status:*` (`needs-triage`, `needs-info`, `needs-feature`, `ready-for-agent`, `ready-for-human`, `blocked`), `tier:*`.
- **Any number:** `area:*` (topics shared by every repository), `module:*` (parts of one repository, declared in the scheme), `source:external`, `source:audit`.
- **Not labels.** The order of work is the milestone: no `priority:*`. The feature is the `Part of` link: no `feature:*`. A rejected or duplicate issue is closed with that reason and a comment: no `wontfix`, `invalid`, `duplicate`.
- **The engine's labels** `sdlc:*` live in the backlog: only the engine sets `sdlc:gate`, `sdlc:done` and `sdlc:failed`. The label of a run's workflow, `workflow:<pipeline name>`, is the maintainer's to set, at most one on an intent; the engine never sets or removes it.
- **A new label is a change to the scheme**, a PR to `scripts/labels.json`, not a label made in one repository. `scripts/sync-labels.sh` applies the scheme; `--check` lists what breaks it.

## Task number (ALWAYS)

A task of the backlog has one number across the organization: `BCKLG-<N>`, where `<N>` is its issue number in `galax-io/galaxio-sdlc-backlog`. It is the only number a spec gets, and it names the task wherever `#N` alone would be read as an issue or PR of another repository.

- **Spec.** Its directory is `specs/BCKLG-<N>-<name>/`, `<N>` its main task. Its `spec.md`, `plan.md` and `tasks.md` carry the line `**Задачи**: BCKLG-<N>[, BCKLG-<M>…]` under the title, or `**Tasks**: …` in a repository whose files are English only, read alike: every task it carries, the main one first. A task has one spec: before writing one, find the spec whose tasks line names the task, check it against the task as its issue says now, and update it rather than write another. A spec directory from before the number keeps its name.
- **Branch.** A task's documents branch in the backlog is `spec/BCKLG-<N>-<name>`, whether made by hand or by the engine, which works on it and opens no other branch in the backlog; the engine's run is `BCKLG-<N>-<time>`, and its branches in the code repositories are `sdlc/BCKLG-<N>-<time>`.
- **Commit.** A commit that does a task's work carries the trailer `Refs: BCKLG-<N>`; its subject stays a Conventional Commit.
- **PR.** Its body carries the line `Refs: BCKLG-<N>` beside `Part of galax-io/galaxio-sdlc-backlog#<N>`, or beside `Closes galax-io/galaxio-sdlc-backlog#<N>` in the task's documents PR.
- **Checked.** The backlog's `scripts/check-linkage.sh --specs` checks every spec there.

## Agents & Models (ALWAYS)

Route work by the reasoning it needs, not by habit; parallelize only where agents would not rebuild the same context. Every model is the newest of its class that the accounts can run; an older one is never used.

| Tier | Claude (effort) | Codex (effort) | Google (effort) | Work |
|---|---|---|---|---|
| deep | `claude-opus-5-5` (xhigh) | `gpt-6.2-sol` (xhigh) | `gemini-4-neon` (xhigh) | complex code, review and verification (every critic), research, root-cause analysis, specs/plans, architecture |
| light | `claude-sonnet-5-5` (high) | `gpt-6.1-terra` (high) | `gemini-4-hydrogen` (high) | simpler work a plan already spells out: task/issue generation, mechanical infra (CI config, version pins, renames, doc fixes) |
| trivial | `claude-haiku-5-5` (high) | `gpt-6-luna` (high) | `gemini-4-hydrogen` (low) | very simple edits, gathering context, finding files, bookkeeping |
| exceptional | `claude-fable-5-2` (medium) | `gpt-6.2-astra` (xhigh) | `gemini-4-carbon` (xhigh) | only when the maintainer names the chosen model for a task; no automatic selection or vendor fallback |

- **Newest only.**
  - When a newer model of a class appears, this table and every pin of it move to it in the same change: `.claude/settings.json`, `.claude/agents/`, `setup-speckit.sh` (in spec-kit-galaxio-bootstrap), and any code that routes models.
  - Terra stays at 6.1 and Luna at 6 because Codex refuses `gpt-6.2-terra` and `gpt-6.2-luna` to a ChatGPT account.
  - `gpt-6.2-sol` and `gpt-6.2-astra` need Codex CLI 0.167.2 or later (0.165 refuses them); `gpt-6.1-terra` needs 0.159 or later.
  - Claude Code's own aliases and its background model are pinned to the table in `.claude/settings.json` (`env`: `ANTHROPIC_DEFAULT_FABLE_MODEL`, `ANTHROPIC_DEFAULT_OPUS_MODEL`, `…_SONNET_MODEL`, `…_HAIKU_MODEL`, `ANTHROPIC_SMALL_FAST_MODEL`). Without that pin its `haiku` alias means an older Haiku.
- **Codex models by tier.** Deep work runs Sol on xhigh effort, light work runs Terra on high effort, and trivial work runs Luna on high effort. A stage keeps its tier through a vendor fallback; task labels do not switch the model inside its executor session.
- **Google models by tier.** Gemini CLI (`gemini`) and Antigravity (`agy`) follow the same tiers: deep runs `gemini-4-neon` at xhigh, light runs `gemini-4-hydrogen` at high, trivial runs `gemini-4-hydrogen` at low, and `gemini-4-carbon` at xhigh is exceptional (explicit maintainer choice only). Select the model explicitly with `--model <id>` (e.g. `agy --model gemini-4-neon` / `gemini --model gemini-4-neon`) and matching thinking depth.
- **Exceptional is explicit.** Fable, Astra and Carbon run only on their own vendor's harness, with no cross-vendor fallback. Token cost keeps them opt-in: the maintainer names the model on the chosen stage, never as defaultModel. The engine runs Fable at medium; interactive Claude Code uses `claude --model claude-fable-5-2 --effort medium`. Raising effort needs an explicit instruction.
- **Reviews are deep.** Every critic and reviewer runs at the deep tier, on the other vendor than the work it judges.
- **Research is adversarial.** Deep research gets a second opinion from the other vendor (`deep-researcher` does this via `codex exec --sandbox read-only`). A claim only one model supports is unverified, not a fact.
- **Every task carries its tier.** `tasks.md` lines add `[tier:deep]`, `[tier:light]` or `[tier:trivial]` next to `[P]`, and issues carry the matching `tier:*` label where the project labels them. When unsure, it is deep.
- **Parallelize by context, not by file.**
  - Every sub-agent builds its own context from scratch. Agents that must read the same code, spec, or repository repeat that cost and leave integration work behind.
  - `[tier:deep]` work that shares a context runs in one agent, task after task: the main session or a single deep sub-agent. This holds even when `[P]` marks the tasks as parallelizable.
  - Fan deep work out only where each agent's context differs: separate repositories, or subsystems an agent can learn without the others.
  - `[tier:light]` mechanical work (`infra-worker`) and `[tier:trivial]` work (`trivial-worker`) may fan out freely.
  - Dependent tasks stay sequential.
- **Pinned, not remembered.**
  - The Spec Kit kit (`.claude/skills/speckit-*`, `.agents/skills/speckit-*`, `.specify/` but `.specify/memory/`) is the same in every repository: `scripts/build-speckit-kit.sh` of spec-kit-galaxio-bootstrap generates it, pins the Claude Code skills to their tier (override with `SPECKIT_DEEP_MODEL` / `SPECKIT_LIGHT_MODEL`), and it arrives with `copier update` (or `scripts/adopt.sh --speckit`). `scripts/check-speckit.sh` fails when this repository's kit differs; do not edit it here and do not run `specify extension`, `preset` or `integration` commands here. The constitution (`.specify/memory/constitution.md`) is this repository's own.
  - `.claude/agents/` defines the tiered sub-agents, each with its tier's model and `effort`.
  - Where a project routes models in code, it routes by this table: a task or stage names its tier, and each harness runs its own vendor's model of that tier. A model outside the table is an error.
