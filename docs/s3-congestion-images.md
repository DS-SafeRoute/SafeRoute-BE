# S3 혼잡 이미지 버킷 설정

애플리케이션은 AWS SDK의 기본 자격 증명 체인을 사용한다. 운영 환경에서는 Access Key를
환경 변수나 설정 파일에 넣지 않고 인스턴스 또는 컨테이너의 IAM Role에 다음 최소 권한을
부여한다.

```json
{
  "Effect": "Allow",
  "Action": "s3:PutObject",
  "Resource": "arn:aws:s3:::BUCKET_NAME/training/*"
}
```

버킷은 Block Public Access를 모두 활성화하고 기본 암호화를 SSE-S3 또는 SSE-KMS로
설정한다. Presigned PUT 요청에는 `Content-Type: image/jpeg` 헤더가 반드시 포함되어야
한다.

## Lifecycle

현재 Object Key는 `training/{sessionId}/{imageType}/...` 형식이다. S3 Lifecycle의 Prefix
필터는 와일드카드를 지원하지 않으므로 `training/*/monitoring/` 같은 규칙으로 모니터링과
이벤트 이미지를 구분할 수 없다.

보관 기간을 모니터링 7일, 이벤트 90일로 분리하려면 다음 중 하나를 인프라에 적용한다.

- S3 Object Created 이벤트로 Lambda를 실행해 경로에 따라 `retention=monitoring` 또는
  `retention=event` 태그를 붙이고, Lifecycle을 태그 기준으로 설정한다.
- Object Key를 `training/monitoring/{sessionId}/...` 및
  `training/events/{sessionId}/...` 형식으로 변경한 뒤 Prefix 기준 Lifecycle을 설정한다.

API 계약의 Object Key를 유지하기 위해 현재 구현은 첫 번째 방식을 전제로 한다.
