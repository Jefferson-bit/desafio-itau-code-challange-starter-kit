package br.com.itau.challenge.desafio.adapter.output

import br.com.itau.challenge.common.logging.Log5WBuilder
import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.domain.exception.DuplicateTransactionException
import br.com.itau.challenge.desafio.port.output.TransactionFinanceTemplateRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import tools.jackson.databind.ObjectMapper
import java.util.*

private const val PK = "PK"
private const val SK = "SK"
private const val PREFIX_ACCOUNT = "ACCOUNT#"
private const val PREFIX_TRANSACTION = "TRANSACTION#"

@Component
class DynamoDbTransactionFinance(
    private val dynamoDbClient: DynamoDbClient,
    private val objectMapper: ObjectMapper,
    @Value("\${dynamodb.table-name}")
    private val tableName: String,
    @Value("\${dynamodb.gsi-index}")
    private val gsiIndex: String,
) : TransactionFinanceTemplateRepository {

    private val log = Log5WBuilder.of(DynamoDbTransactionFinance::class.java)

    override fun save(transactionFinance: TransactionFinance) {
        val request =
            PutItemRequest
                .builder()
                .tableName(tableName)
                .item(putItem(transactionFinance))
                .conditionExpression("attribute_not_exists($PK) AND attribute_not_exists($SK)")
                .build()

        try {
            dynamoDbClient.putItem(request)
            log.info(
                what = "Transaction Finance saved with success",
                why = "${transactionFinance.account.id}, ${transactionFinance.transaction.id}"
            )
        } catch (ex: ConditionalCheckFailedException) {
            log.error(
                what = "Condition expression violated. PK and SK already exists.",
                who = "${transactionFinance.account.id}, ${transactionFinance.transaction.id}",
                throwable = ex
            )
            throw DuplicateTransactionException(
                "Transaction ${transactionFinance.transaction.id} already stored for account ${transactionFinance.account.id}"
            )
        }
    }

    override fun getAllTransactionsAccount(accountId: UUID): List<TransactionFinance> {
        val request =
            QueryRequest
                .builder()
                .tableName(tableName)
                .keyConditionExpression("PK =:account AND begins_with(SK, :transaction)")
                .expressionAttributeValues(
                    mapOf(
                        ":account" to AttributeValue.builder().s("${PREFIX_ACCOUNT}${accountId}").build(),
                        ":transaction" to AttributeValue.builder().s(PREFIX_TRANSACTION).build()
                    )
                )
                .build()

        return dynamoDbClient.query(request).items()
            .map { item ->
                objectMapper.readValue(
                    item.getValue("transaction_finance").s(), TransactionFinance::class.java
                )
            }
    }

    private fun putItem(transactionFinance: TransactionFinance) =
        mapOf(
            PK to AttributeValue.builder().s("${PREFIX_ACCOUNT}${transactionFinance.account.id}").build(),
            SK to AttributeValue.builder().s("${PREFIX_TRANSACTION}${transactionFinance.transaction.id}").build(),
            "transaction_finance" to AttributeValue.builder().s(objectMapper.writeValueAsString(transactionFinance))
                .build(),
        )
}
