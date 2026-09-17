package br.com.itau.challenge.desafio.adapter.input.kafka

import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.domain.exception.DuplicateTransactionException
import br.com.itau.challenge.desafio.port.input.TransactionFinanceUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.support.Acknowledgment
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

@ExtendWith(MockitoExtension::class)
class TransactionFinanceConsumerTest {

    @Mock
    private lateinit var transactionFinanceUseCase: TransactionFinanceUseCase

    @Mock
    private lateinit var acknowledgment: Acknowledgment

    private val objectMapper = jacksonObjectMapper()

    private lateinit var consumer: TransactionFinanceConsumer

    @BeforeEach
    fun setUp() {
        consumer = TransactionFinanceConsumer(transactionFinanceUseCase, objectMapper)
    }

    @Test
    fun `should map the payload forward it to the use case and acknowledge`() {
        val accountId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()

        consumer.consume(validPayload(accountId, transactionId), acknowledgment)

        val captor = ArgumentCaptor.forClass(TransactionFinance::class.java)
        verify(transactionFinanceUseCase).saveTransactionFinance(captor.capture() ?: uninitialized())
        assertEquals(accountId, captor.value.account.id)
        assertEquals(transactionId, captor.value.transaction.id)
        assertEquals("CREDIT", captor.value.transaction.type)
        assertEquals("ENABLED", captor.value.account.status)
        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `should acknowledge without propagating when the transaction was already processed`() {
        willThrow(DuplicateTransactionException("already processed"))
            .given(transactionFinanceUseCase)
            .saveTransactionFinance(any(TransactionFinance::class.java) ?: uninitialized())

        consumer.consume(validPayload(UUID.randomUUID(), UUID.randomUUID()), acknowledgment)

        verify(acknowledgment).acknowledge()
    }

    @Test
    fun `should let a malformed payload exception propagate without acknowledging`() {
        assertFailsWith<JacksonException> {
            consumer.consume("this is not valid json", acknowledgment)
        }

        verify(acknowledgment, never()).acknowledge()
    }

    private fun validPayload(accountId: UUID, transactionId: UUID, ownerId: UUID = UUID.randomUUID()) =
        """
        {
          "transaction": {
            "id": "$transactionId",
            "type": "CREDIT",
            "amount": 97.07,
            "currency": "BRL",
            "status": "APPROVED",
            "timestamp": 1751641364589998
          },
          "account": {
            "id": "$accountId",
            "owner": "$ownerId",
            "created_at": 1634874339000000,
            "status": "ENABLED",
            "balance": {
              "amount": 183.12,
              "currency": "BRL"
            }
          }
        }
        """.trimIndent()
}
