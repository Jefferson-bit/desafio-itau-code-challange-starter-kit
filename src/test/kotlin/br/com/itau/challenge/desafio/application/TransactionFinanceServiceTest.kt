package br.com.itau.challenge.desafio.application

import br.com.itau.challenge.desafio.domain.Account
import br.com.itau.challenge.desafio.domain.Balance
import br.com.itau.challenge.desafio.domain.Transaction
import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.port.output.TransactionFinanceTemplateRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
class TransactionFinanceServiceTest {

    @Mock
    private lateinit var repository: TransactionFinanceTemplateRepository

    private lateinit var service: TransactionFinanceService

    @BeforeEach
    fun setUp() {
        service = TransactionFinanceService(repository)
    }

    private fun aTransactionFinance(accountId: UUID = UUID.randomUUID(), timestamp: Long = 1_000L) =
        TransactionFinance(
            transaction = Transaction(
                id = UUID.randomUUID(),
                type = "CREDIT",
                amount = BigDecimal("97.07"),
                currency = "BRL",
                status = "APPROVED",
                timestamp = timestamp,
            ),
            account = Account(
                id = accountId,
                owner = UUID.randomUUID(),
                createdAt = 1_000L,
                status = "ENABLED",
                balance = Balance(amount = BigDecimal("183.12"), currency = "BRL"),
            ),
        )

    @Test
    fun `should delegate save to the repository`() {
        val transactionFinance = aTransactionFinance()

        service.saveTransactionFinance(transactionFinance)

        verify(repository).save(transactionFinance)
    }

    @Test
    fun `should return the transaction with the most recent timestamp among the account's transactions`() {
        val accountId = UUID.randomUUID()
        val older = aTransactionFinance(accountId, timestamp = 100L)
        val newer = aTransactionFinance(accountId, timestamp = 200L)
        given(repository.getAllTransactionsAccount(accountId)).willReturn(listOf(older, newer))

        val result = service.getAccountTransactions(accountId)

        assertEquals(newer.transaction?.id, result?.transaction?.id)
    }

    @Test
    fun `should pick the most recent transaction regardless of list order`() {
        val accountId = UUID.randomUUID()
        val newer = aTransactionFinance(accountId, timestamp = 200L)
        val older = aTransactionFinance(accountId, timestamp = 100L)
        given(repository.getAllTransactionsAccount(accountId)).willReturn(listOf(newer, older))

        val result = service.getAccountTransactions(accountId)

        assertEquals(newer.transaction?.id, result?.transaction?.id)
    }

    @Test
    fun `should return null when the account has no transactions`() {
        val accountId = UUID.randomUUID()
        given(repository.getAllTransactionsAccount(accountId)).willReturn(emptyList())

        val result = service.getAccountTransactions(accountId)

        assertNull(result)
    }
}
