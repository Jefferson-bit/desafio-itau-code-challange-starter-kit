package br.com.itau.challenge.desafio.domain

data class TransactionFinance(
    val transaction: Transaction,
    val account: Account,
)