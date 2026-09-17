package br.com.itau.challenge.desafio.domain

import java.util.*

data class Account(
    val id: UUID,
    val owner: UUID,
    val createdAt: Long,
    val status: String,
    val balance: Balance,
)
