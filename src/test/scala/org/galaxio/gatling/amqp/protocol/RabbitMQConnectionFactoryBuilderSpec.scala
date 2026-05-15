package org.galaxio.gatling.amqp.protocol

import com.rabbitmq.client.ConnectionFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RabbitMQConnectionFactoryBuilderSpec extends AnyFlatSpec with Matchers {

  "RabbitMQConnectionFactoryBuilder" should "produce a ConnectionFactory with default settings when no options set" in {
    val builder = RabbitMQConnectionFactoryBuilder()
    val cf      = builder.build

    cf shouldBe a[ConnectionFactory]
    cf.getHost shouldBe ConnectionFactory.DEFAULT_HOST
    cf.getPort shouldBe ConnectionFactory.DEFAULT_AMQP_PORT
    cf.getUsername shouldBe ConnectionFactory.DEFAULT_USER
    cf.getPassword shouldBe ConnectionFactory.DEFAULT_PASS
    cf.getVirtualHost shouldBe ConnectionFactory.DEFAULT_VHOST
  }

  it should "set the host when host is provided" in {
    val cf = RabbitMQConnectionFactoryBuilder(host = Some("my-rabbit-host")).build

    cf.getHost shouldBe "my-rabbit-host"
  }

  it should "set the port when port is provided" in {
    val cf = RabbitMQConnectionFactoryBuilder(port = Some(15672)).build

    cf.getPort shouldBe 15672
  }

  it should "set the username when username is provided" in {
    val cf = RabbitMQConnectionFactoryBuilder().username("testuser").build

    cf.getUsername shouldBe "testuser"
  }

  it should "set the password when password is provided" in {
    val cf = RabbitMQConnectionFactoryBuilder().password("secret").build

    cf.getPassword shouldBe "secret"
  }

  it should "set the virtual host when vhost is provided" in {
    val cf = RabbitMQConnectionFactoryBuilder().vhost("/my-vhost").build

    cf.getVirtualHost shouldBe "/my-vhost"
  }

  it should "set multiple properties together" in {
    val cf = RabbitMQConnectionFactoryBuilder(host = Some("broker.example.com"))
      .port(5673)
      .username("admin")
      .password("admin-pass")
      .vhost("/production")
      .build

    cf.getHost shouldBe "broker.example.com"
    cf.getPort shouldBe 5673
    cf.getUsername shouldBe "admin"
    cf.getPassword shouldBe "admin-pass"
    cf.getVirtualHost shouldBe "/production"
  }

  it should "be immutable - builder methods return new instances" in {
    val original = RabbitMQConnectionFactoryBuilder()
    val modified = original.username("changed")

    original.username shouldBe None
    modified.username shouldBe Some("changed")
    original should not be theSameInstanceAs(modified)
  }

  it should "be created via RabbitMQConnectionFactoryBuilderBase.host" in {
    val builder = RabbitMQConnectionFactoryBuilderBase.host("rabbit-server")

    builder.host shouldBe Some("rabbit-server")
  }

  it should "be created via RabbitMQConnectionFactoryBuilderBase.default with no options" in {
    val builder = RabbitMQConnectionFactoryBuilderBase.default

    builder.host shouldBe None
    builder.port shouldBe None
    builder.username shouldBe None
    builder.password shouldBe None
    builder.virtualHost shouldBe None
  }

  it should "not modify the ConnectionFactory defaults when optional fields are None" in {
    val defaultCf = new ConnectionFactory()
    val builtCf   = RabbitMQConnectionFactoryBuilder().build

    builtCf.getHost shouldBe defaultCf.getHost
    builtCf.getPort shouldBe defaultCf.getPort
    builtCf.getUsername shouldBe defaultCf.getUsername
    builtCf.getPassword shouldBe defaultCf.getPassword
    builtCf.getVirtualHost shouldBe defaultCf.getVirtualHost
  }

  it should "allow chaining multiple builder method calls" in {
    val builder = RabbitMQConnectionFactoryBuilder()
      .username("user1")
      .password("pass1")
      .vhost("/test")

    builder.username shouldBe Some("user1")
    builder.password shouldBe Some("pass1")
    builder.virtualHost shouldBe Some("/test")
  }
}
