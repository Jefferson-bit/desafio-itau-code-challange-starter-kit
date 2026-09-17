package br.com.itau.challenge.desafio.adapter.input.kafka.request

import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TransactionFinanceMessageTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `should deserialize the documented Kafka payload shape, including snake_case created_at`() {
        val transactionId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        val payload = """
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

        val message = objectMapper.readValue(payload, TransactionFinanceMessage::class.java)

        assertEquals(transactionId, message.transaction.id)
        assertEquals("CREDIT", message.transaction.type)
        assertEquals(BigDecimal("97.07"), message.transaction.amount)
        assertEquals("BRL", message.transaction.currency)
        assertEquals("APPROVED", message.transaction.status)
        assertEquals(1751641364589998L, message.transaction.timestamp)

        assertEquals(accountId, message.account.id)
        assertEquals(ownerId, message.account.owner)
        assertEquals(1634874339000000L, message.account.createdAt)
        assertEquals("ENABLED", message.account.status)
        assertEquals(BigDecimal("183.12"), message.account.balance.amount)
        assertEquals("BRL", message.account.balance.currency)
    }

    @Test
    fun `should expose equal messages built with the same values as equal`() {
        val transaction = TransactionMessage(
            id = UUID.randomUUID(),
            type = "DEBIT",
            amount = BigDecimal("10.00"),
            currency = "BRL",
            status = "APPROVED",
            timestamp = 1L,
        )
        val account = AccountMessage(
            id = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            createdAt = 1L,
            status = "ENABLED",
            balance = BalanceMessage(amount = BigDecimal("10.00"), currency = "BRL"),
        )

        val first = TransactionFinanceMessage(transaction, account)
        val second = TransactionFinanceMessage(transaction, account)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
