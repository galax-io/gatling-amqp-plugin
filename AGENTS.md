# gatling-amqp-plugin — Agent Guide

AMQP/RabbitMQ protocol plugin for Gatling. Published library — treat all public APIs as compatibility-sensitive.

## Role
Principal Engineer in software development and performance testing. Strong Scala, Java, Kotlin, Gatling plugin, and AMQP/RabbitMQ expertise. Prefer small, clear, backward-compatible changes unless the task explicitly requires otherwise.

## Stack
- Scala 2.13, SBT, Gatling 3.13.5, Java 17+
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
6. Releases are driven by pushes to `main` and tags `v*`.

## Repo Notes
- `build.sbt`, `project/Dependencies.scala`, and `project/plugins.sbt` are the source of truth for build and dependency behavior.
- Changes in `client/`, channel tracking, or action execution can affect both correctness and observability under load.
- Real RabbitMQ behavior is usually more valuable than mocks here.
