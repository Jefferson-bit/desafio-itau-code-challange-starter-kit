package br.com.itau.challenge.desafio.adapter.output

import br.com.itau.challenge.desafio.domain.Account
import br.com.itau.challenge.desafio.domain.Balance
import br.com.itau.challenge.desafio.domain.Transaction
import br.com.itau.challenge.desafio.domain.TransactionFinance
import br.com.itau.challenge.desafio.domain.exception.DuplicateTransactionException
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement
import software.amazon.awssdk.services.dynamodb.model.KeyType
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TABLE_NAME = "TransactionFinance"

@Testcontainers
class DynamoDbTransactionFinanceIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        private val dynamoDbContainer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("amazon/dynamodb-local:3.3.0"))
                .withExposedPorts(8000)
                .withCommand("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory")

        private lateinit var dynamoDbClient: DynamoDbClient

        @BeforeAll
        @JvmStatic
        fun setUpClientAndTable() {
            dynamoDbClient =
                DynamoDbClient
                    .builder()
                    .endpointOverride(
                        URI.create("http://${dynamoDbContainer.host}:${dynamoDbContainer.getMappedPort(8000)}")
                    )
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                    .build()

            dynamoDbClient.createTable(
                CreateTableRequest
                    .builder()
                    .tableName(TABLE_NAME)
                    .attributeDefinitions(
                        AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
                        AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build(),
                    )
                    .keySchema(
                        KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build(),
                    )
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build(),
            )
        }
    }

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `should save a transaction and read it back from the real table`() {
        val transactionFinance = aTransactionFinance()

        repository.save(transactionFinance)
        val stored = repository.getAllTransactionsAccount(transactionFinance.account.id)

        assertEquals(1, stored.size)
        assertEquals(transactionFinance.transaction.id, stored.single().transaction.id)
        assertEquals(transactionFinance.account.id, stored.single().account.id)
    }

    @Test
    fun `should reject a second insert for the same accountId and transactionId`() {
        val transactionFinance = aTransactionFinance()
        repository.save(transactionFinance)

        assertFailsWith<DuplicateTransactionException> { repository.save(transactionFinance) }
    }

    @Test
    fun `should keep both transactions when the same account receives a new transactionId`() {
        val accountId = UUID.randomUUID()
        val first = aTransactionFinance(accountId = accountId)
        val second = aTransactionFinance(accountId = accountId)

        repository.save(first)
        repository.save(second)
        val stored = repository.getAllTransactionsAccount(accountId)

        assertEquals(2, stored.size)
        assertTrue(stored.any { it.transaction.id == first.transaction.id })
        assertTrue(stored.any { it.transaction.id == second.transaction.id })
    }

    @Test
    fun `should return an empty list for an account that was never stored`() {
        val stored = repository.getAllTransactionsAccount(UUID.randomUUID())

        assertTrue(stored.isEmpty())
    }

    private val repository = DynamoDbTransactionFinance(
        dynamoDbClient = dynamoDbClient,
        objectMapper = objectMapper,
        tableName = TABLE_NAME,
        gsiIndex = "unused-in-this-test",
    )

    private fun aTransactionFinance(
        accountId: UUID = UUID.randomUUID(),
        transactionId: UUID = UUID.randomUUID(),
    ) = TransactionFinance(
        transaction = Transaction(
            id = transactionId,
            type = "CREDIT",
            amount = BigDecimal("97.07"),
            currency = "BRL",
            status = "APPROVED",
            timestamp = 1751641364589998L,
        ),
        account = Account(
            id = accountId,
            owner = UUID.randomUUID(),
            createdAt = 1634874339000000L,
            status = "ENABLED",
            balance = Balance(amount = BigDecimal("183.12"), currency = "BRL"),
        ),
    )
}
