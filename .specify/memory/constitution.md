<!--
Sync Impact Report
- Version change: (template) -> 1.0.0
- Bump rationale: MAJOR, first ratified constitution of this repository.
- Sections: Precedence; Core Principles (I-VIII); Runtime and Build Constraints;
  Public Contracts and Consumers; Release and Security; Development Workflow and
  Quality Gates; Governance.
- Templates checked: .specify/templates/plan-template.md (Constitution Check
  reads these principles as gates; no change needed),
  .specify/templates/spec-template.md, .specify/templates/tasks-template.md
  (no change needed). The kit under .specify/ is owned by
  spec-kit-galaxio-bootstrap and is not edited here.
- Follow-up TODOs: none.
-->

# gatling-amqp-plugin Constitution

`gatling-amqp-plugin` is a published library: the AMQP/RabbitMQ protocol for
Gatling (publish, request-reply and consume), released to Maven Central as
`org.galaxio:gatling-amqp-plugin_2.13`. Its users are load-test projects outside
this repository, so every principle below protects what they compile and run
against.

## Precedence

1. The maintainer's direct instruction.
2. The ecosystem constitution (`galax-io/galaxio-sdlc-backlog`,
   `.specify/memory/constitution.md`).
3. This constitution.
4. This repository's `AGENTS.md` (the working guide: commands, structure).

This constitution MAY tighten the ecosystem principles and MUST NOT contradict
them. Where `AGENTS.md` conflicts with this file, this file wins.

## Core Principles

### I. Binary Compatibility Is Gated

- A change MUST pass `sbt mimaReportBinaryIssues` against the previous
  artifact named in `mimaPreviousArtifacts` (`build.sbt`, currently `1.3.0`);
  this is a blocking step of `.github/workflows/ci.yml`.
- A change MUST NOT add MiMa filters or lower `mimaPreviousArtifacts` to make a
  break pass. A deliberate break MUST be approved by the maintainer and ship in a
  new MAJOR version (`versionScheme := Some("semver-spec")` in `publish.sbt`).
- After a release, `mimaPreviousArtifacts` MUST move to that released version,
  never to an unpublished tag.

Rationale: users upgrade the plugin inside existing simulations; MiMa is the
only mechanical proof that their compiled code still links.

### II. Scala DSL and Java API Are One Contract

- The public surface is the Scala DSL (`org.galaxio.gatling.amqp.Predef`,
  `AmqpDsl`, and the `protocol`, `request`, `checks` builders) and the Java facade
  (`src/main/java/org/galaxio/gatling/amqp/javaapi/`), which Kotlin users also
  call. Changing a public signature, builder method, or DSL behavior in either
  MUST be approved by the maintainer (`AGENTS.md`, "Ask first").
- The Java facade MUST stay a thin delegation to the Scala implementation (as
  `javaapi/AmqpDsl.java` does); it MUST NOT reimplement protocol logic.
- A new user-facing DSL capability MUST be reachable from both the Scala DSL and
  the Java facade in the same change, or the spec MUST state why one is left out.

Rationale: Java and Kotlin users get the plugin only through the facade; logic
in two places drifts.

### III. Defaults and Semantics Are Observable Behavior

- The defaults documented in `README.md` (non-persistent delivery mode, matching
  replies by `messageId`, the channel pool size, the consume timeout, the reply
  timeout) MUST NOT change without the maintainer's approval and a README update
  in the same change.
- Reply routing stays queue-based, and the request destinations stay
  `queueExchange`, `directExchange` and `topicExchange`, unless a spec approved by
  the maintainer changes them.

Rationale: a changed default silently alters the load a test produces and the
results it reports, without any compile error.

### IV. Every Broker Outcome Reaches Gatling

- A publish failure, a failed check, a reply timeout or a consume timeout MUST
  end the request as a KO in Gatling statistics (unless the user marked the
  request `.silent`); it MUST NOT be swallowed or only logged.
- Changes in `client/` (connection pool, channel factory, message tracker,
  publisher) and `action/` MUST preserve connection and channel lifecycle:
  channels returned to the pool are usable, closed channels are invalidated, and
  pools, tracker channels and connections are closed on Gatling termination
  (`registerOnTermination` in `AmqpProtocol.scala`).

Rationale: AMQP interactions are stateful; a lost error or a leaked channel
shows up only under load, as wrong numbers in a report.

### V. Real Broker over Mocks, Tests First

- Runtime behavior MUST be proven against a real RabbitMQ: Testcontainers specs
  tagged `DockerTest` (`src/test/scala/org/galaxio/gatling/amqp/integration/`) or
  the Gatling example run `AmqpGatlingTest` against the CI broker service.
- RabbitMQ client or Gatling internals MUST NOT be mocked where such a real
  path exists (`AGENTS.md`, "Never").
- A bug fix or new behavior MUST come with a test that fails before the change
  and passes after it.

Rationale: the plugin's value is correct behavior against a real broker; mocks
of the AMQP client hide exactly the failures users hit.

### VI. Gatling Is the Host, Its Version Is Deliberate

- `gatling-core` and `gatling-core-java` MUST stay `Provided`
  (`project/Dependencies.scala`): the user's Gatling supplies them.
- The Gatling version (`gatlingVersion`) and the Scala 2 to Scala 3 move MUST
  change only as a deliberate, maintainer-approved upgrade; the ignore rules in
  `.scala-steward.conf` that keep bots off them MUST NOT be removed otherwise.
- A new runtime dependency or a dependency upgrade MUST be approved by the
  maintainer (`AGENTS.md`, "Ask first"); every runtime dependency becomes a
  transitive dependency of every user.

Rationale: the plugin runs inside the user's Gatling; a version clash breaks
their test at startup.

### VII. Releases Are Immutable and Human-Triggered

- A release MUST be a `vX.Y.Z` tag pushed by a human on `main` or a `release/*`
  branch; `.github/workflows/release.yml` refuses other tags. Merging to `main`
  MUST NOT publish anything.
- A published version MUST NOT be republished or retagged. The tags `v0.12.1`,
  `v0.13.0-latest` and `v1.0.5` have no artifact and MUST NOT be republished or
  treated as installable (`AGENTS.md`, "Repo Notes").
- The Gatling and GatlingIt configuration artifacts MUST stay unpublished
  (`publishArtifact := false` in `build.sbt`).

Rationale: Maven Central is immutable; a wrong release cannot be taken back.

### VIII. The Build Is Reproducible from Its Files

- `build.sbt`, `publish.sbt`, `project/Dependencies.scala`, `project/plugins.sbt`
  and `project/build.properties` are the source of truth for the build; versions
  MUST NOT be overridden elsewhere.
- CI MUST NOT restore the sbt 2 task cache or `target/` (the `disk-cache: false`
  and "Discard sbt 2 task cache" steps), so no test is skipped incrementally.

Rationale: sbt 2 caches task results; a restored cache can report green tests
that never ran.

## Runtime and Build Constraints

- Published for Scala 2.13 (`scalaVersion` in `build.sbt`); runs on Java 17 or
  newer, and CI tests on Java 17 and 21.
- Builds on sbt 2 at the exact version pinned in `project/build.properties`,
  with `--server` in `.sbtopts`.
- Formatting is `scalafmt` with `.scalafmt.conf`; CI fails on unformatted code.

## Public Contracts and Consumers

- Consumers: external Gatling projects that depend on
  `org.galaxio:gatling-amqp-plugin_2.13`. `catalog-info.yaml` lists no
  organization component that depends on this one.
- Dependencies: `catalog-info.yaml` declares
  `component:default/spec-kit-galaxio-bootstrap` (process: the Spec Kit kit and
  the gates arrive by `copier update`). The kit under `.claude/skills`,
  `.agents/skills` and `.specify/` (except `.specify/memory/`) MUST NOT be edited
  here; `scripts/check-speckit.sh` fails a PR that does.
- A change to `catalog-info.yaml` relations or annotations is a compatibility
  change for the ecosystem and MUST be stated in the PR.

## Release and Security

- Signing and Sonatype credentials (`PGP_SECRET`, `PGP_PASSPHRASE`,
  `SONATYPE_USERNAME`, `SONATYPE_PASSWORD`) live only as GitHub secrets used by
  `release.yml`; they MUST NOT be committed or used by other workflows.
- `BACKLOG_READ_TOKEN` is used only by `.github/workflows/linkage.yml`, a
  workflow that runs no build code; it MUST NOT be passed to a job that runs sbt.
- Workflows keep least privilege (`permissions: contents: read` unless a job
  needs more).

## Development Workflow and Quality Gates

- Work enters through the backlog: every PR carries a milestone of this
  repository and `Part of galax-io/galaxio-sdlc-backlog#N`, checked by
  `scripts/check-linkage.sh --pr` in `linkage.yml`.
- Gates a PR MUST pass: scalafmt check, `sbt clean compile`, MiMa, unit and
  integration tests with coverage, the `AmqpGatlingTest` run, dependency review,
  the sbt upgrade parity check, `check-linkage.sh` and `check-speckit.sh`.
- Branch from `main`, rebase only, no force-push to or commits on `main`; merge
  is the maintainer's decision.
- A plan's Constitution Check answers yes or no for each principle above; a
  "no" needs an entry in the plan's Complexity Tracking.

## Governance

- This constitution is amended only by a pull request to this repository with
  the maintainer's explicit approval, carrying an updated Sync Impact Report.
- Versioning: MAJOR when a principle is removed or redefined, MINOR when a
  principle or section is added or materially expanded, PATCH for wording that
  changes no rule.
- An exception to a principle MUST name an owner and a removal date, and is
  recorded in the plan's Complexity Tracking.
- Reviewers and the review critic check every spec, plan and PR against this
  file.

**Version**: 1.0.0 | **Ratified**: 2026-10-20 | **Last Amended**: 2026-10-20
