package br.com.itau.challenge.desafio.port.input

import br.com.itau.challenge.desafio.domain.TransactionFinance
import java.util.UUID

interface TransactionFinanceUseCase {
    fun saveTransactionFinance(transactionFinance: TransactionFinance)
    fun getAccountTransactions(accountId: UUID): TransactionFinance?
}
