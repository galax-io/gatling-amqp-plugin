package org.galaxio.gatling.amqp.javaapi.request;

import io.gatling.javaapi.core.ActionBuilder;
import org.galaxio.gatling.amqp.javaapi.check.AmqpChecks;

import java.util.Arrays;
import java.util.List;

public class ConsumeDslBuilder implements ActionBuilder {
    private org.galaxio.gatling.amqp.request.ConsumeDslBuilder wrapped;

    public ConsumeDslBuilder(org.galaxio.gatling.amqp.request.ConsumeDslBuilder wrapped) {
        this.wrapped = wrapped;
    }

    public ConsumeDslBuilder timeout(Long millis) {
        this.wrapped = wrapped.timeout(millis);
        return this;
    }

    public ConsumeDslBuilder check(Object... checks) {
        return check(Arrays.asList(checks));
    }

    public ConsumeDslBuilder check(List<Object> checks) {
        this.wrapped = wrapped.check(AmqpChecks.toScalaChecks(checks));
        return this;
    }

    public ConsumeDslBuilder silent() {
        this.wrapped = wrapped.silent();
        return this;
    }

    public io.gatling.core.action.builder.ActionBuilder asScala() {
        return wrapped.build();
    }
}
