# WebSocket 이벤트 계약 (혼잡도)

연결 endpoint: `/ws`
구독 topic: `/topic/training-sessions/{sessionId}`

## 발행 단위 원칙

- CCTV 1 Observation = `CONGESTION_UPDATED` 1건
- 즉시 혼잡 상태 전환 1건 = `CONGESTION_EVENT_RECEIVED` 1건
- 영향받는 Edge 개수만큼 같은 메시지를 반복 발행하지 않는다
- 영향받는 Edge는 `affectedEdgeIds` 배열로 한 번에 전달한다 (Edge가 없으면 빈 배열, `null`과 혼용하지 않는다)
- Edge별 경로 재탐색 계산(`RouteRecalculationService.trigger`)은 발행 단위와 무관하게 영향받는 Edge마다 개별 실행한다

## `CONGESTION_UPDATED`

`CongestionObservationService.reportObservation()` 처리 후 발행된다.

```json
{
  "type": "CONGESTION_UPDATED",
  "sessionId": "d669294e-55e1-4c00-bf67-229d89b76948",
  "data": {
    "eventId": "3c9f7e2a-3b39-4f0a-9f0a-6a2b6b1f5a11",
    "affectedEdgeIds": [
      "4d5e3a7f-9154-4afb-9967-a552003994cf"
    ],
    "cctvCode": "CCTV_001",
    "avgHeadcount": 8.6,
    "peakHeadcount": 12,
    "density": 0.42,
    "congestionLevel": "CROWDED",
    "windowStart": 1787722090000,
    "windowEnd": 1787722095000,
    "capturedAt": 1787722095000,
    "configVersion": 3,
    "hasMonitoringImage": true
  }
}
```

## `CONGESTION_EVENT_RECEIVED`

`CongestionEventService.reportCongestionEvent()` 처리 후 발행된다 (즉시 혼잡 진입/상승/종료 이벤트).

```json
{
  "type": "CONGESTION_EVENT_RECEIVED",
  "sessionId": "d669294e-55e1-4c00-bf67-229d89b76948",
  "data": {
    "eventId": "3c9f7e2a-3b39-4f0a-9f0a-6a2b6b1f5a11",
    "affectedEdgeIds": [
      "4d5e3a7f-9154-4afb-9967-a552003994cf"
    ],
    "cctvCode": "CCTV_001",
    "eventType": "CONGESTION_STARTED",
    "density": 3.4,
    "congestionLevel": "CROWDED",
    "detectedAt": 1787722095000,
    "imageUploadStatus": "PENDING"
  }
}
```

## REST와 WebSocket 필드 대응

| REST `current-states` | WebSocket | 의미 |
|---|---|---|
| `cctvCode` | `cctvCode` | CCTV 식별자 |
| `avgHeadcount` | `avgHeadcount` | 최근 분석 구간 평균 인원 |
| `peakHeadcount` | `peakHeadcount` | 최근 분석 구간 최대 인원 |
| `density` | `density` | BE가 계산한 밀집도 |
| `congestionLevel` | `congestionLevel` | BE가 판정한 최종 혼잡 단계 |
| `lastDetectedAt` | `capturedAt` | 해당 상태의 대표 관측 시각 |
| `configVersion` | `configVersion` | 적용된 혼잡 설정 버전 |

기존 REST 호환성을 위해 `lastDetectedAt`과 `capturedAt`의 필드명 자체는 통일하지 않고 대응 관계만 문서화한다.

## 이미지 처리 정책

- WebSocket payload에는 이미지의 presigned URL을 포함하지 않는다 (만료 시간이 있는 URL을 별도 갱신 없이 오래 들고 있을 수 없기 때문).
- `hasMonitoringImage=true`인 이벤트를 받으면 프론트가 카메라 또는 프레임 조회 REST API를 재호출해서 새 presigned URL을 받아야 한다.
- REST 응답의 `frameId`와 이 payload의 `eventId`를 비교하면 같은 프레임인지 확인할 수 있다.
- 이미지 URL이 만료되었거나 403이 반환되면 REST API를 다시 호출한다.

## 중복 방어

백엔드가 CCTV/이벤트당 1회 발행을 보장하더라도, 프론트는 `eventId` 기준 중복 제거를 유지해야 한다. REST 최초 조회와 WebSocket 연결 사이의 경합, WebSocket 재연결, 메시지 재수신, 화면 재진입 등으로 같은 이벤트가 다시 보일 수 있기 때문이다.
