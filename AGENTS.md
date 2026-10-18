# gatling-amqp-plugin — Agent Guide

AMQP/RabbitMQ protocol plugin for Gatling. Published library — treat all public APIs as compatibility-sensitive.

## Role
Principal Engineer in software development and performance testing. Strong Scala, Java, Kotlin, Gatling plugin, and AMQP/RabbitMQ expertise. Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

## Stack
- Scala 2.13.x, sbt 2.0.x, Gatling 3.13.x, Java 17+
- AMQP/RabbitMQ plugin: publish, request-reply, and consume flows
- Java API facade with Kotlin-compatible usage and tests
- RabbitMQ Java client, Testcontainers RabbitMQ, Codecov, Sonatype

## Commands

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

A feature is an issue in `galax-io/galaxio-sdlc-backlog`. It belongs to exactly one backlog milestone whose description has the line `Release: business`. Backlog milestones without that line (the backlog's own tooling and documents) are not releases and follow "Milestones" above.

- **Feature issue.** Lists the work in every repository it touches as a checklist, one line per issue or PR: `- [ ] galax-io/<repo>#<N> → <repo milestone>`. A line is checked when that work is merged and the repository milestone it names has been released, so its tag exists.
- **Repository work.** Every issue and PR of a feature carries the version milestone of its own repository and writes `Part of galax-io/galaxio-sdlc-backlog#<feature>`. A repository ships its own version when its milestone is complete, as under "Release Process". `scripts/check-linkage.sh` skips the same-milestone rule for a link to a feature; the PR still needs its own milestone.
- **Every PR links a feature.** No PR of a product repository merges without `Part of galax-io/galaxio-sdlc-backlog#<feature>` (or a closing link to a feature); `scripts/check-linkage.sh --pr` fails it. A bot's PR (a bot author, or a `dependabot/` or `update/` branch) and the backlog's own PRs are exempt. The backlog is private, so the CI job reads it with `BACKLOG_READ_TOKEN`, a read-only token for that repository (Issues and Metadata) set as an organization secret; without it the gate fails and says it cannot read the backlog. Maintenance and technical PRs with no feature of their own link the standing feature of the active business milestone (the issue with the label `standing`); every business milestone has one.
- **Work enters through the backlog.** A bug, an audit finding or a request is not worked on until it belongs to a feature: grouped into one or given one. An issue from outside stays in its repository, which is public, and carries the label `needs-feature` until it is linked to a feature in the backlog.
- **Feature done.** A feature issue is closed only when every line of its checklist is checked.
- **Release.** When every feature of a business milestone is closed, close the milestone and publish a GitHub Release in `galax-io/galaxio-sdlc-backlog` that lists each feature with the repository versions that carry it. Name the release and its tag by the milestone's id, the title up to its first em dash, lowercased (`M1 — first run` → `m1`).
- **Published releases are not edited.** A revert after the release is a new feature in the next business milestone.
- **Never** close a business milestone with an open feature, release a feature whose repository version is not tagged, or leave a repository version out of the release text.

