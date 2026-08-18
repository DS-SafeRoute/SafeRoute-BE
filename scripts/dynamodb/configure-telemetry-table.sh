#!/usr/bin/env bash
set -euo pipefail

table_name="${DYNAMODB_TABLE:-saferoute-telemetry}"
gsi_name="${DYNAMODB_GSI1_NAME:-GSI1}"
aws_region="${AWS_REGION:-us-east-2}"

if [[ ! "$table_name" =~ ^[a-zA-Z0-9_.-]{3,255}$ ]]; then
  echo "Invalid DynamoDB table name: $table_name" >&2
  exit 1
fi
if [[ ! "$gsi_name" =~ ^[a-zA-Z0-9_.-]{3,255}$ ]]; then
  echo "Invalid DynamoDB GSI name: $gsi_name" >&2
  exit 1
fi

aws_args=(--region "$aws_region")
table_status="$(
  aws dynamodb describe-table \
    "${aws_args[@]}" \
    --table-name "$table_name" \
    --query 'Table.TableStatus' \
    --output text
)"

if [[ "$table_status" != "ACTIVE" ]]; then
  echo "Table must be ACTIVE before configuration: $table_status" >&2
  exit 1
fi

existing_gsi="$(
  aws dynamodb describe-table \
    "${aws_args[@]}" \
    --table-name "$table_name" \
    --query "Table.GlobalSecondaryIndexes[?IndexName=='$gsi_name'].IndexName | [0]" \
    --output text
)"

if [[ "$existing_gsi" == "None" ]]; then
  billing_mode="$(
    aws dynamodb describe-table \
      "${aws_args[@]}" \
      --table-name "$table_name" \
      --query 'Table.BillingModeSummary.BillingMode' \
      --output text
  )"
  gsi_create="{\"IndexName\":\"$gsi_name\",\"KeySchema\":[{\"AttributeName\":\"GSI1_PK\",\"KeyType\":\"HASH\"},{\"AttributeName\":\"GSI1_SK\",\"KeyType\":\"RANGE\"}],\"Projection\":{\"ProjectionType\":\"ALL\"}}"
  if [[ "$billing_mode" == "None" || "$billing_mode" == "PROVISIONED" ]]; then
    read_capacity="${DYNAMODB_GSI_READ_CAPACITY:-5}"
    write_capacity="${DYNAMODB_GSI_WRITE_CAPACITY:-5}"
    if [[ ! "$read_capacity" =~ ^[1-9][0-9]*$ || ! "$write_capacity" =~ ^[1-9][0-9]*$ ]]; then
      echo "GSI provisioned capacities must be positive integers" >&2
      exit 1
    fi
    gsi_create="${gsi_create%?},\"ProvisionedThroughput\":{\"ReadCapacityUnits\":$read_capacity,\"WriteCapacityUnits\":$write_capacity}}"
  fi

  aws dynamodb update-table \
    "${aws_args[@]}" \
    --table-name "$table_name" \
    --attribute-definitions \
      AttributeName=GSI1_PK,AttributeType=S \
      AttributeName=GSI1_SK,AttributeType=S \
    --global-secondary-index-updates \
      "[{\"Create\":$gsi_create}]"
  aws dynamodb wait table-exists "${aws_args[@]}" --table-name "$table_name"
else
  hash_key="$(
    aws dynamodb describe-table \
      "${aws_args[@]}" \
      --table-name "$table_name" \
      --query "Table.GlobalSecondaryIndexes[?IndexName=='$gsi_name'].KeySchema[?KeyType=='HASH'].AttributeName | [0]" \
      --output text
  )"
  range_key="$(
    aws dynamodb describe-table \
      "${aws_args[@]}" \
      --table-name "$table_name" \
      --query "Table.GlobalSecondaryIndexes[?IndexName=='$gsi_name'].KeySchema[?KeyType=='RANGE'].AttributeName | [0]" \
      --output text
  )"
  if [[ "$hash_key" != "GSI1_PK" || "$range_key" != "GSI1_SK" ]]; then
    echo "Existing $gsi_name key schema is incompatible: HASH=$hash_key RANGE=$range_key" >&2
    exit 1
  fi
fi

ttl_status="$(
  aws dynamodb describe-time-to-live \
    "${aws_args[@]}" \
    --table-name "$table_name" \
    --query 'TimeToLiveDescription.TimeToLiveStatus' \
    --output text
)"
ttl_attribute="$(
  aws dynamodb describe-time-to-live \
    "${aws_args[@]}" \
    --table-name "$table_name" \
    --query 'TimeToLiveDescription.AttributeName' \
    --output text
)"

case "$ttl_status" in
  DISABLED)
    aws dynamodb update-time-to-live \
      "${aws_args[@]}" \
      --table-name "$table_name" \
      --time-to-live-specification Enabled=true,AttributeName=expiresAt
    ;;
  ENABLED|ENABLING)
    if [[ "$ttl_attribute" != "expiresAt" ]]; then
      echo "TTL is configured with a different attribute: $ttl_attribute" >&2
      exit 1
    fi
    ;;
  *)
    echo "TTL is not ready to be configured: $ttl_status" >&2
    exit 1
    ;;
esac

echo "Configured table=$table_name region=$aws_region gsi=$gsi_name ttl=expiresAt"
