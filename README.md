# Gatling AMQP Plugin

[![CI](https://github.com/galax-io/gatling-amqp-plugin/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/galax-io/gatling-amqp-plugin/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.galaxio/gatling-amqp-plugin_2.13.svg?color=success)](https://search.maven.org/search?q=org.galaxio.gatling-amqp-plugin)
[![codecov](https://codecov.io/github/galax-io/gatling-amqp-plugin/coverage.svg?branch=main)](https://codecov.io/github/galax-io/gatling-amqp-plugin?branch=main)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

AMQP protocol plugin for [Gatling](https://gatling.io/) load testing framework. Supports RabbitMQ with publish, request-reply, and consume patterns with connection pooling.

## Table of Contents

- [Compatibility](#compatibility)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Protocol Configuration](#protocol-configuration)
- [Actions](#actions)
- [Message Properties](#message-properties)
- [Checks](#checks)
- [Silent Mode](#silent-mode)
- [Publisher Confirms](#publisher-confirms)
- [Examples](#examples)
- [Contributing](#contributing)
- [License](#license)

## Compatibility

| Plugin Version | Gatling | Scala | Java |
|---|---|---|---|
| 0.x.y-latest | 3.13.x | 2.13 | 17+ |
| 0.x.y | 3.11.x | 2.13 | 17+ |

> **Branch strategy:** `main` targets Gatling 3.11.x, `latest/gatling` targets Gatling 3.13.x.

## Installation

### Scala (sbt)

```scala
libraryDependencies += "org.galaxio" %% "gatling-amqp-plugin" % "<version>" % Test
```

### Java / Kotlin (Gradle Kotlin DSL)

```kotlin
gatling("org.galaxio:gatling-amqp-plugin_2.13:<version>")
```

### Maven

```xml
<dependency>
  <groupId>org.galaxio</groupId>
  <artifactId>gatling-amqp-plugin_2.13</artifactId>
  <version>${version}</version>
  <scope>test</scope>
</dependency>
```

## Quick Start

### Docker (local RabbitMQ)

```bash
docker run -d --name gatling-rabbit \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

### Minimal Scenario — Scala

```scala
import org.galaxio.gatling.amqp.Predef._
import io.gatling.core.Predef._

class AmqpSimulation extends Simulation {
  val amqpConf = amqp
    .connectionFactory(
      rabbitmq
        .host("localhost")
        .port(5672)
        .username("guest")
        .password("guest")
        .vhost("/")
    )
    .usePersistentDeliveryMode
    .matchByMessageId

  val scn = scenario("AMQP Publish")
    .exec(
      amqp("publish").publish
        .queueExchange("test-queue")
        .textMessage("""{"msg": "hello"}""")
        .contentType("application/json")
    )

  setUp(scn.inject(atOnceUsers(1))).protocols(amqpConf)
}
```

### Minimal Scenario — Java

```java
import static org.galaxio.gatling.amqp.javaapi.AmqpDsl.*;
import static io.gatling.javaapi.core.CoreDsl.*;

public class AmqpSimulation extends Simulation {
  var amqpConf = amqp()
    .connectionFactory(
      rabbitmq().host("localhost").port(5672).username("guest").password("guest").build()
    )
    .usePersistentDeliveryMode()
    .matchByMessageId();

  var scn = scenario("AMQP Publish")
    .exec(
      amqp("publish").publish()
        .queueExchange("test-queue")
        .textMessage("{\"msg\": \"hello\"}")
        .contentType("application/json")
    );

  { setUp(scn.injectOpen(atOnceUsers(1)).protocols(amqpConf)); }
}
```

### Minimal Scenario — Kotlin

```kotlin
import org.galaxio.gatling.amqp.javaapi.AmqpDsl.*
import io.gatling.javaapi.core.CoreDsl.*

class AmqpSimulation : Simulation() {
  val amqpConf = amqp()
    .connectionFactory(
      rabbitmq().host("localhost").port(5672).username("guest").password("guest").build()
    )
    .usePersistentDeliveryMode()
    .matchByMessageId()

  val scn = scenario("AMQP Publish")
    .exec(
      amqp("publish").publish()
        .queueExchange("test-queue")
        .textMessage("""{"msg": "hello"}""")
        .contentType("application/json")
    )

  init { setUp(scn.injectOpen(atOnceUsers(1)).protocols(amqpConf)) }
}
```

## Protocol Configuration

### Connection Factory

```scala
val cf = rabbitmq
  .host("localhost")
  .port(5672)
  .username("guest")
  .password("guest")
  .vhost("/")
```

### Advanced Connection Settings

```scala
val cf = rabbitmq
  .host("localhost")
  .port(5672)
  .username("guest")
  .password("guest")
  .automaticRecovery(true)
  .networkRecoveryInterval(5000)
  .topologyRecovery(true)
  .connectionTimeout(10000)
  .requestedHeartbeat(60)
  .requestedChannelMax(0)
  .useSslProtocol()
```

### Protocol Options

```scala
val amqpConf = amqp
  .connectionFactory(publishCf)                  // single connection factory
  .connectionFactory(publishCf, replyCf)         // separate publish/reply connections

  .usePersistentDeliveryMode                     // delivery mode 2
  .useNonPersistentDeliveryMode                  // delivery mode 1 (default)

  .matchByMessageId                              // match replies by messageId (default)
  .matchByCorrelationId                          // match replies by correlationId
  .matchByMessage(msg => msg.messageId)          // custom match extraction

  .replyTimeout(30000)                           // reply timeout in ms
  .consumerThreadsCount(4)                       // consumer threads per pool
  .channelPoolSize(32)                           // max pooled channels (default: 16)
  .usePublisherConfirms                          // enable publisher confirms

  .responseTransform(msg => msg)                 // transform reply messages before checks
```

### Queue and Exchange Declarations

```scala
val amqpConf = amqp
  .connectionFactory(cf)
  .declare(queue("test-queue", durable = true, exclusive = false, autoDelete = false))
  .declare(exchange("test-exchange", BuiltinExchangeType.TOPIC, durable = true))
  .bindQueue(
    queue("test-queue", durable = true),
    exchange("test-exchange", BuiltinExchangeType.TOPIC),
    "routing.key"
  )
```

## Actions

### Publish

```scala
// Queue exchange
amqp("publish").publish
  .queueExchange("my-queue")
  .textMessage("""{"data": "value"}""")

// Topic exchange
amqp("publish").publish
  .topicExchange("my-exchange", "routing.key")
  .textMessage("""{"data": "value"}""")

// Direct exchange
amqp("publish").publish
  .directExchange("my-exchange", "routing.key")
  .bytesMessage(myBytes)

// With EL expressions
amqp("publish-#{id}").publish
  .queueExchange("queue-#{name}")
  .textMessage("""{"id": "#{id}"}""")
```

### Request-Reply

```scala
amqp("request-reply").requestReply
  .topicExchange("request-exchange", "request.key")
  .replyExchange("reply-queue")
  .textMessage("""{"query": "data"}""")
  .messageId("#{msgId}")
  .check(jsonPath("$.result").is("ok"))
```

The request destination supports `queueExchange`, `directExchange`, and `topicExchange`. The reply destination (`.replyExchange(name)`) specifies the queue where the plugin listens for replies. Reply routing is always queue-based.

Multi-broker setup (publish to one broker, consume reply from another):

```scala
val amqpConf = amqp
  .connectionFactory(publisherCf, replyConsumerCf)
  .matchByCorrelationId
  .replyTimeout(10000)
```

### Consume

Polls the queue for a message up to the specified timeout (default 5000ms). If no message arrives within the timeout, the action reports failure.

```scala
amqp("consume").consume
  .queue("my-queue")
  .timeout(5000)
  .check(bodyString.is("expected"))
```

## Message Properties

All message properties support Gatling Expression Language:

```scala
amqp("publish").publish
  .queueExchange("my-queue")
  .textMessage("hello")
  .messageId("#{msgId}")
  .correlationId("#{corrId}")
  .contentType("application/json")
  .contentEncoding("UTF-8")
  .priority(5)
  .replyTo("reply-queue")
  .expiration("60000")
  .amqpType("my.type")
  .userId("guest")
  .appId("my-app")
  .header("X-Custom", "value")
  .headers("X-One" -> "1", "X-Two" -> "2")
```

## Checks

Checks are supported on request-reply and consume actions:

```scala
import org.galaxio.gatling.amqp.Predef._

// JSON checks
.check(jsonPath("$.status").is("ok"))
.check(jsonPath("$.items[*].id").findAll.saveAs("ids"))
.check(jmesPath("status").is("ok"))

// XML checks
.check(xpath("//status").is("ok"))

// Body checks
.check(bodyString.is("expected"))
.check(bodyBytes.exists)
.check(substring("success").exists)

// Response code
.check(responseCode.notIn("500", "503"))

// Simple custom check
.check(simpleCheck(msg => msg.payload.nonEmpty))

// Conditional check
.check(checkIf("#{useCheck}")(jsonPath("$.data").exists))
```

## Silent Mode

Mark requests as silent to suppress statistics logging:

```scala
amqp("setup-publish").publish
  .queueExchange("setup-queue")
  .textMessage("init")
  .silent

amqp("setup-rpc").requestReply
  .queueExchange("setup-queue")
  .replyExchange("reply-queue")
  .textMessage("init")
  .silent
```

## Publisher Confirms

Enable publisher confirms for guaranteed delivery:

```scala
val amqpConf = amqp
  .connectionFactory(cf)
  .usePublisherConfirms
```

## Examples

### Scala

- [Publish](src/test/scala/org/galaxio/gatling/amqp/examples/PublishExample.scala)
- [Request-Reply](src/test/scala/org/galaxio/gatling/amqp/examples/RequestReplyExample.scala)
- [Two Brokers](src/test/scala/org/galaxio/gatling/amqp/examples/RequestReplyTwoBrokerExample.scala)
- [Custom Matching](src/test/scala/org/galaxio/gatling/amqp/examples/RequestReplyWithOwnMatchingExample.scala)

### Java

- [Publish](src/test/java/org/galaxio/gatling/amqp/javaapi/examples/PublishExample.java)
- [Request-Reply](src/test/java/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyExample.java)
- [Two Brokers](src/test/java/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyTwoBrokerExample.java)

### Kotlin

> **Note:** Kotlin examples use the Java API facade and are provided as reference. They are not compiled by CI (no Kotlin compiler plugin is configured in the build).

- [Publish](src/test/kotlin/org/galaxio/gatling/amqp/javaapi/examples/PublishExample.kt)
- [Request-Reply](src/test/kotlin/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyExample.kt)
- [Custom Matching](src/test/kotlin/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyWithOwnMatchingExample.kt)
- [Two Brokers](src/test/kotlin/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyTwoBrokerExample.kt)

## Contributing

```bash
# Build
sbt compile

# Run unit tests
sbt test

# Run integration tests (requires RabbitMQ)
docker-compose up -d
sbt "Gatling / testOnly org.galaxio.gatling.amqp.examples.AmqpGatlingTest"

# Check formatting
sbt scalafmtCheckAll

# Format code
sbt scalafmtAll
```

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
