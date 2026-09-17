package br.com.itau.challenge.desafio.adapter.input.kafka

import br.com.itau.challenge.common.logging.Log5WBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import tools.jackson.core.JacksonException

@Configuration
class KafkaErrorHandlerConfig {

    private val log = Log5WBuilder.of(KafkaErrorHandlerConfig::class.java)

    @Bean
    fun kafkaErrorHandler(kafkaTemplate: KafkaTemplate<String, String>): DefaultErrorHandler {
        val backOff = ExponentialBackOffWithMaxRetries(5).apply {
            initialInterval = 1_000
            multiplier = 2.0
            maxInterval = 10_000
        }

        return DefaultErrorHandler(recoverer(DeadLetterPublishingRecoverer(kafkaTemplate)), backOff).apply {
            addNotRetryableExceptions(JacksonException::class.java)
        }
    }

    internal fun recoverer(deadLetterRecoverer: ConsumerRecordRecoverer): ConsumerRecordRecoverer =
        ConsumerRecordRecoverer { record, ex ->
            if (isMalformedPayload(ex)) {
                log.warn(
                    what = "Discarding malformed payload",
                    why = "${record.topic()}-${record.partition()}@${record.offset()}: ${ex.message}"
                )
            } else {
                log.error(
                    what = "Sending record to dead-letter topic after exhausting retries",
                    why = "${record.topic()}-${record.partition()}@${record.offset()}",
                    throwable = ex
                )
                deadLetterRecoverer.accept(record, ex)
            }
        }

    internal final tailrec fun isMalformedPayload(ex: Throwable?): Boolean =
        when (ex) {
            null -> false
            is JacksonException -> true
            else -> isMalformedPayload(ex.cause)
        }
}
