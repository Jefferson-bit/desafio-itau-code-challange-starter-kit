package br.com.itau.challenge.desafio.adapter.input.web

import br.com.itau.challenge.desafio.domain.Account
import br.com.itau.challenge.desafio.domain.Balance
import br.com.itau.challenge.desafio.domain.Transaction
import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.port.input.TransactionFinanceUseCase
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.util.UUID

@WebMvcTest(BalanceController::class)
class BalanceControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @MockitoBean
    private lateinit var transactionFinanceUseCase: TransactionFinanceUseCase

    @Test
    fun `should return the current balance when the account has transactions`() {
        val accountId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()

        given(transactionFinanceUseCase.getAccountTransactions(accountId)).willReturn(
            TransactionFinance(
                transaction = Transaction(
                    id = UUID.randomUUID(),
                    type = "CREDIT",
                    amount = BigDecimal("97.07"),
                    currency = "BRL",
                    status = "APPROVED",
                    timestamp = 1751641364589998L,
                ),
                account = Account(
                    id = accountId,
                    owner = ownerId,
                    createdAt = 1634874339000000L,
                    status = "ENABLED",
                    balance = Balance(amount = BigDecimal("183.12"), currency = "BRL"),
                ),
            )
        )

        mockMvc.get("/balances/$accountId").andExpect {
            status { isOk() }
            content { contentType(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value(accountId.toString()) }
            jsonPath("$.owner") { value(ownerId.toString()) }
            jsonPath("$.balance.amount") { value(183.12) }
            jsonPath("$.balance.currency") { value("BRL") }
        }
    }

    @Test
    fun `should return 404 when the account does not exist`() {
        val accountId = UUID.randomUUID()

        given(transactionFinanceUseCase.getAccountTransactions(accountId)).willReturn(null)

        mockMvc.get("/balances/$accountId").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `should return 400 when the accountId path variable is not a valid UUID`() {
        mockMvc.get("/balances/not-a-uuid").andExpect {
            status { isBadRequest() }
        }
    }
}
