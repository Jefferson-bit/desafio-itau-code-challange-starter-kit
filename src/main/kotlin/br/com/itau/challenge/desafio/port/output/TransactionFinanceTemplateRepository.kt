package br.com.itau.challenge.desafio.port.output

import br.com.itau.challenge.desafio.domain.TransactionFinance
import java.util.*

interface TransactionFinanceTemplateRepository {
    fun save(transactionFinance: TransactionFinance)
    fun getAllTransactionsAccount(accountId: UUID): List<TransactionFinance>
}
