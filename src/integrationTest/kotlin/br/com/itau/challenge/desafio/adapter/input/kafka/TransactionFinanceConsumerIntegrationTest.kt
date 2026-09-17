package br.com.itau.challenge.desafio.adapter.input.kafka

import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.domain.exception.DuplicateTransactionException
import br.com.itau.challenge.desafio.port.input.TransactionFinanceUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.util.*
import kotlin.test.assertEquals

private const val TOPIC = "transaction-finance-process"

@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

@Testcontainers
@SpringBootTest
class TransactionFinanceConsumerIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        private val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
    }

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @MockitoBean
    private lateinit var transactionFinanceUseCase: TransactionFinanceUseCase

    @BeforeEach
    fun resetMock() {
        Mockito.reset(transactionFinanceUseCase)
    }

    @Test
    fun `should consume a valid message and forward it correctly mapped to the use case`() {
        val accountId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()

        kafkaTemplate.send(TOPIC, validPayload(accountId, transactionId))

        val captor = ArgumentCaptor.forClass(TransactionFinance::class.java)
        verify(transactionFinanceUseCase, timeout(10_000)).saveTransactionFinance(captor.capture() ?: uninitialized())

        val captured = captor.value
        assertEquals(accountId, captured.account.id)
        assertEquals(transactionId, captured.transaction?.id)
        assertEquals("CREDIT", captured.transaction?.type)
        assertEquals("ENABLED", captured.account.status)
        assertEquals(0, java.math.BigDecimal("183.12").compareTo(captured.account.balance.amount))
    }

    @Test
    fun `should acknowledge and keep consuming when the transaction was already processed`() {
        willThrow(DuplicateTransactionException("already processed"))
            .given(transactionFinanceUseCase).saveTransactionFinance(any(TransactionFinance::class.java) ?: uninitialized())

        kafkaTemplate.send(TOPIC, validPayload(UUID.randomUUID(), UUID.randomUUID()))
        verify(transactionFinanceUseCase, timeout(10_000)).saveTransactionFinance(any(TransactionFinance::class.java) ?: uninitialized())

        Mockito.reset(transactionFinanceUseCase)
        val accountId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        kafkaTemplate.send(TOPIC, validPayload(accountId, transactionId))

        val captor = ArgumentCaptor.forClass(TransactionFinance::class.java)
        verify(transactionFinanceUseCase, timeout(10_000)).saveTransactionFinance(captor.capture() ?: uninitialized())
        assertEquals(accountId, captor.value.account.id)
    }

    @Test
    fun `should discard a malformed payload without ever calling the use case`() {
        kafkaTemplate.send(TOPIC, "this is not valid json")

        val accountId = UUID.randomUUID()
        val transactionId = UUID.randomUUID()
        kafkaTemplate.send(TOPIC, validPayload(accountId, transactionId))

        val captor = ArgumentCaptor.forClass(TransactionFinance::class.java)
        verify(transactionFinanceUseCase, timeout(10_000)).saveTransactionFinance(captor.capture() ?: uninitialized())
        assertEquals(accountId, captor.value.account.id)

        verify(transactionFinanceUseCase, Mockito.times(1)).saveTransactionFinance(any(TransactionFinance::class.java) ?: uninitialized())
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
