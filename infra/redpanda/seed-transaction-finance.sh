#!/bin/bash
set -euo pipefail

BROKERS="${REDPANDA_BROKERS:-redpanda:9092}"
TOPIC_NAME="${TRANSACTION_FINANCE_PROCESS_TOPIC:-transaction-finance-process}"
DLT_TOPIC_NAME="${TOPIC_NAME}-dlt"
SEED_FILE="/redpanda-seed/transaction-finance-seed.jsonl"

echo "Waiting for Redpanda broker at ${BROKERS}..."
until rpk cluster info --brokers "${BROKERS}" >/dev/null 2>&1; do
  echo "  not ready yet, retrying in 2s..."
  sleep 2
done
echo "Redpanda broker is ready."

for topic in "${TOPIC_NAME}" "${DLT_TOPIC_NAME}"; do
  if rpk topic describe "${topic}" --brokers "${BROKERS}" >/dev/null 2>&1; then
    echo "Topic '${topic}' already exists, skipping creation."
  else
    echo "Creating topic '${topic}'..."
    rpk topic create "${topic}" --brokers "${BROKERS}" --partitions 1 --replicas 1
  fi
done

echo "Publishing seed messages from ${SEED_FILE} to '${TOPIC_NAME}'..."
rpk topic produce "${TOPIC_NAME}" --brokers "${BROKERS}" -f '%v\n' < "${SEED_FILE}"

COUNT=$(wc -l < "${SEED_FILE}" | tr -d ' ')
echo "Seed complete. Published ${COUNT} message(s) to '${TOPIC_NAME}'. Dead-letter topic '${DLT_TOPIC_NAME}' is ready."
