package br.com.itau.challenge.desafio.adapter.input.web

import br.com.itau.challenge.desafio.adapter.input.web.response.AccountResponse
import br.com.itau.challenge.desafio.port.input.TransactionFinanceUseCase
import br.com.itau.challenge.desafio.adapter.input.web.response.Balance
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.*

@RestController
@Tag(name = "Balances", description = "Consulta do saldo mais atual de uma conta")
class BalanceController(
    private val transactionFinanceUseCase: TransactionFinanceUseCase
) {

    @Operation(
        summary = "Consulta o saldo mais atual de uma conta",
        description = "Retorna o saldo mais recente processado para a conta informada, com base na transação de maior timestamp."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Saldo encontrado",
                content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = AccountResponse::class))]
            ),
            ApiResponse(responseCode = "404", description = "Conta não encontrada / sem transações registradas", content = [Content()]),
            ApiResponse(responseCode = "400", description = "accountId não é um UUID válido", content = [Content()]),
        ]
    )
    @GetMapping("/balances/{accountId}")
    fun getBalance(
        @Parameter(description = "Identificador da conta", required = true)
        @PathVariable("accountId") accountId: UUID
    ): ResponseEntity<AccountResponse> {
        val transactionFinance = transactionFinanceUseCase.getAccountTransactions(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Account $accountId not found")
        return ResponseEntity.ok().body(
            AccountResponse(
                id = transactionFinance.account.id,
                owner = transactionFinance.account.owner,
                balance = Balance(
                    amount = transactionFinance.account.balance.amount,
                    currency = transactionFinance.account.balance.currency
                ),
                updatedAt = LocalDateTime.now()
            )
        )
    }
}