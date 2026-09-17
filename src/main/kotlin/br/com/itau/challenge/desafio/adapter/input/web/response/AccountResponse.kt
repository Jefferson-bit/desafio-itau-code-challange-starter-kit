package br.com.itau.challenge.desafio.adapter.input.web.response

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class AccountResponse(
    val id: UUID,
    val owner: UUID,
    val balance: Balance,
    val updatedAt: LocalDateTime
)

data class Balance(
    val amount: BigDecimal,
    val currency: String
)