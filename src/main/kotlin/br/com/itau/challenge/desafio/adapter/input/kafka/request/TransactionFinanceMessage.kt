package br.com.itau.challenge.desafio.adapter.input.kafka.request

import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.kafka.shaded.com.google.protobuf.Timestamp
import java.math.BigDecimal
import java.util.UUID

data class TransactionFinanceMessage(val transaction: TransactionMessage, val account: AccountMessage){}

data class TransactionMessage(
    val id: UUID,
    val type: String,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val timestamp: Long
)

data class AccountMessage(
    val id: UUID,
    val owner: UUID,
    @JsonProperty("created_at")
    val createdAt: Long,
    val status: String,
    val balance: BalanceMessage
)

data class BalanceMessage(
    val amount: BigDecimal,
    val currency: String
)