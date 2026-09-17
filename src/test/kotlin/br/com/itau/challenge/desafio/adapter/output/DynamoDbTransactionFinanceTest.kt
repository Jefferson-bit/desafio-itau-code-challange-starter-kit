package br.com.itau.challenge.desafio.adapter.output

import br.com.itau.challenge.desafio.domain.Account
import br.com.itau.challenge.desafio.domain.Balance
import br.com.itau.challenge.desafio.domain.Transaction
import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.domain.exception.DuplicateTransactionException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import software.amazon.awssdk.services.dynamodb.model.QueryRequest
import software.amazon.awssdk.services.dynamodb.model.QueryResponse
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
class DynamoDbTransactionFinanceTest {

    @Mock
    private lateinit var dynamoDbClient: DynamoDbClient

    private val objectMapper = jacksonObjectMapper()

    private lateinit var repository: DynamoDbTransactionFinance

    @BeforeEach
    fun setUp() {
        repository = DynamoDbTransactionFinance(
            dynamoDbClient = dynamoDbClient,
            objectMapper = objectMapper,
            tableName = "TransactionFinance",
            gsiIndex = "unused-in-this-test",
        )
    }

    @Test
    fun `should put an item keyed by ACCOUNT# and TRANSACTION# with the serialized payload as a single field`() {
        val transactionFinance = aTransactionFinance()

        repository.save(transactionFinance)

        val captor = ArgumentCaptor.forClass(PutItemRequest::class.java)
        verify(dynamoDbClient).putItem(captor.capture())

        val item = captor.value.item()
        assertEquals("ACCOUNT#${transactionFinance.account.id}", item["PK"]?.s())
        assertEquals("TRANSACTION#${transactionFinance.transaction.id}", item["SK"]?.s())
        val stored = objectMapper.readValue(item["transaction_finance"]?.s(), TransactionFinance::class.java)
        assertEquals(transactionFinance.transaction.id, stored.transaction.id)
        assertEquals(transactionFinance.account.id, stored.account.id)
    }

    @Test
    fun `should include a condition expression that rejects an existing PK and SK`() {
        repository.save(aTransactionFinance())

        val captor = ArgumentCaptor.forClass(PutItemRequest::class.java)
        verify(dynamoDbClient).putItem(captor.capture())

        assertTrue(captor.value.conditionExpression()!!.contains("attribute_not_exists"))
    }

    @Test
    fun `should translate a ConditionalCheckFailedException into a DuplicateTransactionException`() {
        given(dynamoDbClient.putItem(any(PutItemRequest::class.java)))
            .willThrow(ConditionalCheckFailedException.builder().message("The conditional request failed").build())

        assertFailsWith<DuplicateTransactionException> {
            repository.save(aTransactionFinance())
        }
    }

    @Test
    fun `should map queried items back into TransactionFinance objects`() {
        val transactionFinance = aTransactionFinance()
        val storedJson = objectMapper.writeValueAsString(transactionFinance)
        val item = mapOf("transaction_finance" to AttributeValue.builder().s(storedJson).build())
        given(dynamoDbClient.query(any(QueryRequest::class.java)))
            .willReturn(QueryResponse.builder().items(listOf(item)).build())

        val result = repository.getAllTransactionsAccount(transactionFinance.account.id)

        assertEquals(1, result.size)
        assertEquals(transactionFinance.transaction.id, result.single().transaction.id)
        assertEquals(transactionFinance.account.id, result.single().account.id)
    }

    @Test
    fun `should query using begins_with on SK scoped to the given accountId`() {
        val accountId = UUID.randomUUID()
        given(dynamoDbClient.query(any(QueryRequest::class.java)))
            .willReturn(QueryResponse.builder().items(emptyList<Map<String, AttributeValue>>()).build())

        repository.getAllTransactionsAccount(accountId)

        val captor = ArgumentCaptor.forClass(QueryRequest::class.java)
        verify(dynamoDbClient).query(captor.capture())
        assertEquals("PK =:account AND begins_with(SK, :transaction)", captor.value.keyConditionExpression())
        assertEquals("ACCOUNT#$accountId", captor.value.expressionAttributeValues()[":account"]?.s())
        assertEquals("TRANSACTION#", captor.value.expressionAttributeValues()[":transaction"]?.s())
    }

    @Test
    fun `should return an empty list when the query has no items`() {
        given(dynamoDbClient.query(any(QueryRequest::class.java)))
            .willReturn(QueryResponse.builder().items(emptyList<Map<String, AttributeValue>>()).build())

        val result = repository.getAllTransactionsAccount(UUID.randomUUID())

        assertTrue(result.isEmpty())
    }

    private fun aTransactionFinance() = TransactionFinance(
        transaction = Transaction(
            id = UUID.randomUUID(),
            type = "CREDIT",
            amount = BigDecimal("97.07"),
            currency = "BRL",
            status = "APPROVED",
            timestamp = 1751641364589998L,
        ),
        account = Account(
            id = UUID.randomUUID(),
            owner = UUID.randomUUID(),
            createdAt = 1634874339000000L,
            status = "ENABLED",
            balance = Balance(amount = BigDecimal("183.12"), currency = "BRL"),
        ),
    )
}
