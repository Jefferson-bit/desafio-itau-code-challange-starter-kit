package br.com.itau.challenge.desafio.application

import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.port.input.TransactionFinanceUseCase
import br.com.itau.challenge.desafio.port.output.TransactionFinanceTemplateRepository
import org.springframework.stereotype.Service
import java.util.*

@Service
class TransactionFinanceService(
    private val transactionFinanceRepository: TransactionFinanceTemplateRepository
) : TransactionFinanceUseCase {

    override fun saveTransactionFinance(transactionFinance: TransactionFinance) {
        transactionFinanceRepository.save(transactionFinance)
    }

    override fun getAccountTransactions(accountId: UUID) =
        transactionFinanceRepository.getAllTransactionsAccount(accountId = accountId)
            .maxByOrNull { it.transaction.timestamp }
}
