# Telemetry DynamoDB table configuration

Telemetry data continues to use the existing `saferoute-telemetry` table. The
application assumes that its primary key attributes remain `pk` and `sk`.

## Required GSI

Create one sparse global secondary index. Existing items without these
attributes are not projected into the index.

| Setting | Value |
|---|---|
| Index name | `GSI1` |
| Partition key | `GSI1_PK` (`String`) |
| Sort key | `GSI1_SK` (`String`) |
| Projection | `ALL` |

The index supports both access patterns:

- observation history: `SESSION#{sessionId}#CCTV#{cctvCode}` ordered by `TIME#{capturedAt}`
- congestion events: `SESSION#{sessionId}` ordered by `EVENT#{detectedAt}#{eventId}`

## TTL

Enable DynamoDB TTL with `expiresAt` as its attribute. Only observation items
contain `expiresAt`; congestion-event and current-state items intentionally do
not contain that attribute.

## Apply to an existing table

Run this once with AWS credentials that can describe and update the table:

```bash
AWS_REGION=us-east-2 \
DYNAMODB_TABLE=saferoute-telemetry \
bash scripts/dynamodb/configure-telemetry-table.sh
```

The script creates `GSI1` only when missing, validates an existing index's key
schema, and enables TTL only when needed. It stops instead of replacing an
incompatible existing index or TTL configuration.

For a provisioned-capacity table, the script creates the GSI with 5 read and 5
write capacity units by default. Override those values with
`DYNAMODB_GSI_READ_CAPACITY` and `DYNAMODB_GSI_WRITE_CAPACITY`.

The application property `aws.dynamodb.gsi1-name` defaults to `GSI1` and can be
overridden with `DYNAMODB_GSI1_NAME` if the deployed index uses another name.
