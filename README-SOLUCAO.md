# Consulta de Saldo — Solução

Este é o README específico da solução implementada para o desafio técnico **"Consulta de Saldo"** do Itaú Unibanco, construída em cima do starter-kit (hexagonal + Kotlin + Spring Boot) que já existia neste repositório. O `README.md` original continua documentando o starter-kit em si; este arquivo documenta **o que foi implementado** e **como rodar o projeto do zero**.

OBS: README gerado pelo Claude Code

### Modelagem no DynamoDB

Tabela `TransactionFinance`, chave composta:

| Atributo | Tipo | Descrição |
|---|---|---|
| `PK` (hash) | String | `ACCOUNT#{accountId}` |
| `SK` (range) | String | `TRANSACTION#{transactionId}` |
| `transaction_finance` | String (JSON) | serialização do `TransactionFinance` (transação + conta) |

### Resiliência do consumer Kafka

- `DefaultErrorHandler` com backoff exponencial (1s → 2s → 4s → 8s → 10s, 5 tentativas) para erros retryable (ex: timeout de rede).
- Payload malformado (JSON inválido) é classificado como **não-retryable** (`JacksonException`) — descartado imediatamente, sem gastar as 5 tentativas, já que nunca vai se recuperar sozinho.
- Depois de esgotar as tentativas num erro genuíno, o registro vai para a **Dead Letter Topic** `transaction-finance-process-dlt`, em vez de ser perdido.

## Stack adicionada além do starter-kit
- **springdoc-openapi** (Swagger UI / OpenAPI 3)
- **Spring Boot Actuator + Micrometer/Prometheus** (observabilidade)
- **Log5WBuilder** — logging estruturado no padrão 5W (quem, o quê, onde, quando, por quê), ver `br.com.itau.challenge.common.logging`
- **Testcontainers** (DynamoDB Local + Kafka) para os testes de integração

## Como subir o projeto

### Pré-requisitos

- Docker + Docker Compose
- `make` (Linux/macOS nativo; no Windows use WSL2 ou rode os comandos do `Makefile` diretamente via `docker compose`)

### Subir tudo

```bash
make up
# ou, sem make:
docker compose up --build -d
```

Isso sobe `app`, `dynamodb` (+ `dynamodb-admin` em `:8001`), `redpanda` (+ `redpanda-console` em `:8081`) e roda automaticamente os jobs de seed (`dynamodb-seed`, `redpanda-seed`), que:

- criam a tabela `TransactionFinance` no DynamoDB (schema `PK`/`SK` acima)
- criam os tópicos Kafka `transaction-finance-process` **e** `transaction-finance-process-dlt` (o cluster tem `auto_create_topics_enabled=false`, então nada é criado implicitamente na primeira publicação — precisa existir de antemão)
- publicam 5 mensagens de exemplo no tópico principal (ver seção "Seed de dados" abaixo)

A aplicação sobe em **`http://localhost:8080`**.

```bash
make logs   # acompanhar os logs
make stop   # derrubar tudo
```

## Tópico DLT (Dead Letter Topic)

O tópico `transaction-finance-process-dlt` é criado automaticamente pelo seed (ver acima). Se precisar recriá-lo manualmente (ex: rodando `redpanda-seed` isoladamente foi pulado por algum motivo):

```bash
make kafka-topic-create NAME=transaction-finance-process-dlt
```

Pra inspecionar o conteúdo da DLT (mensagens que falharam após esgotar as tentativas de retry):

```bash
make kafka-consume TOPIC=transaction-finance-process-dlt
```

## Seed de dados para o nosso caso de uso

O seed do caso de uso "consulta de saldo" está em `infra/redpanda/seed-transaction-finance.sh` (script) + `infra/redpanda/transaction-finance-seed.jsonl` (5 mensagens de exemplo, schema idêntico ao documentado no desafio). Ele roda automaticamente no `make up` / `make kafka-up`, mas também dá pra rodar isoladamente a qualquer momento:

```bash
make kafka-seed-transaction-finance
```

Esse comando é **idempotente** na criação dos tópicos (pula se já existirem) e sempre republica as 5 mensagens de exemplo — útil pra resetar o estado de teste sem subir tudo de novo.

### Contas de exemplo já seedadas

Depois do seed, essas contas já têm saldo consultável (a app precisa estar rodando e ter consumido as mensagens — leva poucos segundos):

| accountId | Saldo esperado (mais recente) | Observação |
|---|---|---|
| `5b19c8b6-0cc4-4c72-a989-0c2ee15fa975` | R$ 137,62 | 2 transações (crédito 97,07 + débito 45,50) |
| `a1b2c3d4-e5f6-4789-a0b1-c2d3e4f5a6b7` | R$ 2.500,00 | 2 transações (1 `APPROVED`, 1 `DECLINED` mais recente — ver "Limitações conhecidas") |
| `c3d4e5f6-a7b8-4901-c2d3-e4f5a6b7c8d9` | R$ 15.000,00 | 1 transação |

```bash
curl http://localhost:8181/balances/5b19c8b6-0cc4-4c72-a989-0c2ee15fa975
```

### Gerando dados aleatórios (além do seed fixo)

O starter-kit já trazia um gerador de eventos aleatórios, que também serve pro nosso tópico:

```bash
make kafka-produce-transactions-events TOPIC=transaction-finance-process COUNT=50
```

## Endpoint REST

### `GET /balances/{accountId}`

| Parâmetro | Local | Tipo | Descrição |
|---|---|---|---|
| `accountId` | Path | UUID | Identificador da conta |

**Respostas:**

| Status | Quando |
|---|---|
| `200` | Conta encontrada, retorna `{id, owner, balance: {amount, currency}, updatedAt}` |
| `404` | Conta sem nenhuma transação registrada |
| `400` | `accountId` não é um UUID válido |

```bash
curl http://localhost:8181/balances/5b19c8b6-0cc4-4c72-a989-0c2ee15fa975
```

### Swagger / OpenAPI

- UI: `http://localhost:8181/swagger-ui/index.html`
- JSON: `http://localhost:8181/v3/api-docs`

## Observabilidade

- Health: `http://localhost:8181/actuator/health`
- Métricas Prometheus: `http://localhost:8181/actuator/prometheus`
- Logs estruturados no padrão 5W (`who/what/where/when/why`), ex:
  ```
  who=system what="Transaction Finance saved with success" where=DynamoDbTransactionFinance when=... why="<accountId>, <transactionId>"
  ```

## Testes

```bash
./gradlew check              # testes unitários + gate de cobertura (mínimo 90% de instrução)
./gradlew integrationTest    # testes de integração (Testcontainers — sobe DynamoDB Local e Kafka sozinho, não precisa de `make db-up`/`kafka-up` antes)
```

- **Unitários** (`src/test`): `BalanceControllerTest` (`@WebMvcTest`, sem subir o contexto todo), `TransactionFinanceServiceTest`, `Log5WBuilderTest`.
- **Integração** (`src/integrationTest`, Testcontainers): `DynamoDbTransactionFinanceIntegrationTest` (DynamoDB Local real), `TransactionFinanceConsumerIntegrationTest` (broker Kafka real, só o repositório é mockado).

## Limitações conhecidas 

As regras de negócio e caso de uso não são tão claras olhando somente o README enviado. Sem isso não da pra ter uma boa noção
de como modelar a tabela do Dynamo. 

O payload de referência que chega no kafka não diz muito sobre as informações dos campos. Pergunta que faria para a pessoa
que solicito a demanda:

* O account, seu id e owner é sempre unico? Quais são os campos que sofrem atualizações? Status? Balance?
* todas as transações são unicas? Os dados não se repete?
* No dynamo o account ele tem q ser único? Só podemos ter 1 Account na tabela es ele pode ter N transações? Ou tem que aceitar duplicação?(
aqui eu tratei pra não aceitar duplicação e ter um histórico de transações pra uma unica account)

"Cada mensagem representa uma transação de crédito ou débito (aprovada ou rejeitada) e inclui o saldo mais atual do cliente com timestamp em microssegundos."

* Existe alguma regra em relação as transações de credito ou debito?
* Transações aprovada ou rejeitada, o que fazer? rejeitada não atualiza o account? Exclui a msg do kafka nesse cenário de rejeitado?

Dependendo da resposta isso muda a modelagem do dynamo. uma tabela com account e o transaction seria um document?
Ou duas tabela, tanto pra Account quanto pra Transaction?

Cheguei a implementa um update considerando race condition, visto que update normalmente fazemos os passo
Leitura -> Alteração -> Atualiza

```kotlin
    override fun update(transactionFinance: TransactionFinance) {
        val currentVersion = transactionFinance.account.currentVersion ?: 0L
        val newVersion = currentVersion + 1
        transactionFinance.account.currentVersion = newVersion
        transactionFinance.account.updatedAt = LocalDateTime.now()

        val updateRequest =
            PutItemRequest
                .builder()
                .tableName(tableName)
                .item(putItem(transactionFinance))
                .conditionExpression("$VERSION = :currentVersion")
                .expressionAttributeValues(
                    mapOf(":currentVersion" to AttributeValue.builder().n(currentVersion.toString()).build())
                )
                .build()

        try {
            dynamoDbClient.putItem(updateRequest)
            log.info(
                what = "Transaction Finance updated with success (optimistic lock)",
                why = "${transactionFinance.account.id}, ${transactionFinance.transaction.id}, version=$newVersion"
            )
        } catch (ex: ConditionalCheckFailedException) {
            log.error(
                what = "Optimistic lock conflict updating Transaction Finance. version changed concurrently.",
                who = "${transactionFinance.account.id}, ${transactionFinance.transaction.id}",
                throwable = ex
            )
            throw ConcurrentModificationException(
                "Transaction ${transactionFinance.transaction.id} for account ${transactionFinance.account.id} " +
                        "was modified concurrently; expected version $currentVersion"
            )
        }
    }
```
Acabei desconsiderando pq tinha bastante dúvida e isso implicaria na modelagem e tomada de decisão. Então só fico o save


### Fontes utilizada
* https://docs.aws.amazon.com/pt_br/amazondynamodb/latest/developerguide/DynamoDBMapper.OptimisticLocking.html
* https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html#default-eh
* https://dynobase.dev/dynamodb-locking/
* https://medium.com/@databackendtech/spring-boot-java-framework-how-to-guarantee-delivery-once-for-kafka-consumer-e727d7dc425e