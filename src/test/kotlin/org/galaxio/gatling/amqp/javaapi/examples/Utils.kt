package org.galaxio.gatling.amqp.javaapi.examples

import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import java.util.stream.Stream

object Utils {
    private val counter = AtomicInteger(1)
    var idFeeder = Stream.generate(
        Supplier {
            Collections.singletonMap<String, Any>(
                "id",
                counter.getAndIncrement()
            )
        } as Supplier<Map<String, Any>>).iterator()
}