package com.saferoute.domain.training.service;

import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 프레임 목록의 nextCursor를 만들고 해석한다. 클라이언트에는 불투명한 문자열로만 노출된다.
// capturedAt이 같은 프레임이 페이지 경계에 있어도 eventId를 함께 인코딩해 페이지 재개 지점을
// 정확히 가리키도록 한다 (ObservationItem.buildGsi1Sk 참고).
final class FrameCursor {

    private static final String SEPARATOR = "|";

    private FrameCursor() {
    }

    record Position(long capturedAt, String eventId) {
    }

    static String encode(long capturedAt, String eventId) {
        String raw = capturedAt + SEPARATOR + eventId;
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
            long capturedAt = Long.parseLong(decoded.substring(0, separatorIndex));
            String eventId = decoded.substring(separatorIndex + 1);
            return new Position(capturedAt, eventId);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
