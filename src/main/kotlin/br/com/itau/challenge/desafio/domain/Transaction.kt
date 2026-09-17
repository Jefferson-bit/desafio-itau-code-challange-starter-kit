package br.com.itau.challenge.desafio.domain

import java.math.BigDecimal
import java.util.*

class Transaction(
    val id: UUID,
    val type: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val timestamp: Long

) {
}