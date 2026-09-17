package br.com.itau.challenge.desafio.adapter.input.kafka

import br.com.itau.challenge.common.logging.Log5WBuilder
import br.com.itau.challenge.desafio.adapter.input.kafka.request.TransactionFinanceMessage
import br.com.itau.challenge.desafio.domain.Account
import br.com.itau.challenge.desafio.domain.Balance
import br.com.itau.challenge.desafio.domain.Transaction
import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.domain.exception.DuplicateTransactionException
import br.com.itau.challenge.desafio.port.input.TransactionFinanceUseCase
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class TransactionFinanceConsumer(
    private val transactionFinanceUseCase: TransactionFinanceUseCase,
    private val objectMapper: ObjectMapper,
) {

    private val log = Log5WBuilder.of(TransactionFinanceConsumer::class.java)

    @KafkaListener(topics = ["\${transaction-finance-process.topic-name}"])
    fun consume(payload: String, acknowledged: Acknowledgment) {
        try {
            val message = objectMapper.readValue(payload, TransactionFinanceMessage::class.java)
            transactionFinanceUseCase.saveTransactionFinance(
                TransactionFinance(
                    transaction = Transaction(
                        id = message.transaction.id,
                        type = message.transaction.type,
                        amount = message.transaction.amount,
                        currency = message.transaction.currency,
                        status = message.transaction.status,
                        timestamp = message.transaction.timestamp
                    ),
                    account = Account(
                        id = message.account.id,
                        owner = message.account.owner,
                        createdAt = message.account.createdAt,
                        status = message.account.status,
                        balance = Balance(
                            amount = message.account.balance.amount,
                            currency = message.account.balance.currency,
                        )
                    )
                )
            )
            acknowledged.acknowledge()
            log.info(
                what = "Transaction stored",
                who = message.account.id.toString(),
                why = "transactionId=${message.transaction.id}, type=${message.transaction.type}"
            )
        } catch (ex: DuplicateTransactionException) {
            acknowledged.acknowledge()
            log.warn(what = "Duplicate transaction ignored", why = ex.message)
        }

    }
}