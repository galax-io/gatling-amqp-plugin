# Gatling AMQP Plugin 

![Build](https://github.com/galax-io/gatling-amqp-plugin/workflows/Build/badge.svg) 
[![Maven Central](https://img.shields.io/maven-central/v/org.galaxio/gatling-amqp-plugin_2.13.svg?color=success)](https://search.maven.org/search?q=org.galaxio.gatling-amqp-plugin) 
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![codecov.io](https://codecov.io/github/galax-io/gatling-amqp-plugin/coverage.svg?branch=master)](https://codecov.io/github/galax-io/gatling-amqp-plugin?branch=master)

Plugin for support performance testing with AMQP in Gatling(3.9.x)

# Usage

## Getting Started
Plugin is currently available for Scala 2.13, Java 17, Kotlin.

You may add plugin as dependency in project with your tests. 

### Scala

Write this to your build.sbt: 

``` scala
libraryDependencies += "org.galaxio" %% "gatling-amqp-plugin" % <version> % Test
``` 

### Java

Write this to your dependencies block in build.gradle:

```java
gatling "org.galaxio:gatling-amqp-plugin_2.13:<version>"
```

### Kotlin

Write this to your dependencies block in build.gradle:

```kotlin
gatling("org.galaxio:gatling-amqp-plugin_2.13:<version>")
```

## Example Scenarios

### Scala 

* Example scenario for [publishing](src/test/scala/org/galaxio/gatling/amqp/examples/PublishExample.scala)
* Example scenario for [Publish And Reply](src/test/scala/org/galaxio/gatling/amqp/examples/RequestReplyExample.scala)
* Example scenario for [Publish and Reply on different message-brokers](src/test/scala/org/galaxio/gatling/amqp/examples/RequestReplyTwoBrokerExample.scala)

### Java

* Example scenario for [publishing](src/test/java/org/galaxio/gatling/amqp/javaapi/examples/PublishExample.java)
* Example scenario for [Publish And Reply](src/test/java/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyExample.java)
* Example scenario for [Publish and Reply on different message-brokers](src/test/java/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyTwoBrokerExample.java)

### Kotlin

* Example scenario for [publishing](src/test/kotlin/org/galaxio/gatling/amqp/javaapi/examples/PublishExample.kt)
* Example scenario for [Publish And Reply](src/test/kotlin/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyExample.kt)
* Example scenario for [Publish and Reply on different message-brokers](src/test/kotlin/org/galaxio/gatling/amqp/javaapi/examples/RequestReplyTwoBrokerExample.kt)