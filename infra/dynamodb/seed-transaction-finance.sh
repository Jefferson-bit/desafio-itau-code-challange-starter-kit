#!/bin/bash
set -euo pipefail

ENDPOINT_URL="${DYNAMODB_ENDPOINT_URL:-http://dynamodb:8000}"
TABLE_NAME="${TRANSACTION_FINANCE_TABLE_NAME:-TransactionFinance}"
REGION="${AWS_DEFAULT_REGION:-us-east-1}"

echo "Waiting for DynamoDB Local at ${ENDPOINT_URL}..."
until aws dynamodb list-tables --endpoint-url "${ENDPOINT_URL}" --region "${REGION}" >/dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done
echo "DynamoDB Local is ready."

if aws dynamodb describe-table --table-name "${TABLE_NAME}" --endpoint-url "${ENDPOINT_URL}" --region "${REGION}" >/dev/null 2>&1; then
  echo "Table '${TABLE_NAME}' already exists, skipping creation."
else
  echo "Creating table '${TABLE_NAME}'..."
  aws dynamodb create-table \
    --table-name "${TABLE_NAME}" \
    --attribute-definitions AttributeName=PK,AttributeType=S AttributeName=SK,AttributeType=S \
    --key-schema AttributeName=PK,KeyType=HASH AttributeName=SK,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST \
    --endpoint-url "${ENDPOINT_URL}" \
    --region "${REGION}" >/dev/null
  aws dynamodb wait table-exists \
    --table-name "${TABLE_NAME}" \
    --endpoint-url "${ENDPOINT_URL}" \
    --region "${REGION}"
  echo "Table '${TABLE_NAME}' created."
fi
