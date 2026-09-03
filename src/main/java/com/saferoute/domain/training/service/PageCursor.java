package com.saferoute.domain.training.service;

import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 최신순 커서 페이지네이션(프레임 목록, 이벤트 타임라인)의 nextCursor를 만들고 해석한다.
// 클라이언트에는 불투명한 문자열로만 노출된다. timestamp가 같은 항목이 페이지 경계에 있어도
// id를 함께 인코딩해 페이지 재개 지점을 정확히 가리키도록 한다
// (ObservationItem.buildGsi1Sk, CongestionEventItem.buildGsi1Sk 참고).
final class PageCursor {

    private static final String SEPARATOR = "|";

    private PageCursor() {
    }

    record Position(long timestamp, String id) {
    }

    static String encode(long timestamp, String id) {
        String raw = timestamp + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separatorIndex = decoded.indexOf(SEPARATOR);
            if (separatorIndex < 0 || separatorIndex == decoded.length() - 1) {
                throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다.");
            }
            long timestamp = Long.parseLong(decoded.substring(0, separatorIndex));
            String id = decoded.substring(separatorIndex + 1);
            return new Position(timestamp, id);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
