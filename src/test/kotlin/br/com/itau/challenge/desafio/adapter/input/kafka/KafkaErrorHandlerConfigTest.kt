package br.com.itau.challenge.desafio.adapter.input.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import tools.jackson.core.exc.StreamReadException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class KafkaErrorHandlerConfigTest {

    @Mock
    private lateinit var deadLetterRecoverer: ConsumerRecordRecoverer

    private val config = KafkaErrorHandlerConfig()

    private val record = ConsumerRecord("transaction-finance-process", 0, 42L, "key", "payload")

    @Test
    fun `should discard a malformed payload without delegating to the dead-letter recoverer`() {
        val recoverer = config.recoverer(deadLetterRecoverer)

        recoverer.accept(record, StreamReadException("bad json"))

        verify(deadLetterRecoverer, never()).accept(any(), any())
    }

    @Test
    fun `should delegate a non-malformed-payload failure to the dead-letter recoverer`() {
        val recoverer = config.recoverer(deadLetterRecoverer)
        val exception = RuntimeException("connection refused")

        recoverer.accept(record, exception)

        verify(deadLetterRecoverer).accept(record, exception)
    }

    @Test
    fun `should classify a JacksonException nested as a cause as a malformed payload too`() {
        assertTrue(config.isMalformedPayload(RuntimeException("wrapper", StreamReadException("bad json"))))
    }

    @Test
    fun `should not classify a plain exception as a malformed payload`() {
        assertFalse(config.isMalformedPayload(RuntimeException("connection refused")))
    }

    @Test
    fun `should not classify a null cause as a malformed payload`() {
        assertFalse(config.isMalformedPayload(null))
    }
}
