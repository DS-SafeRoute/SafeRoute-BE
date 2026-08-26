package com.saferoute.domain.training.service;

import com.saferoute.global.api.code.ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 프레임 목록의 nextCursor를 만들고 해석한다. 클라이언트에는 불투명한 문자열로만 노출된다.
final class FrameCursor {

    private FrameCursor() {
    }

    static String encode(long capturedAt) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(capturedAt).getBytes(StandardCharsets.UTF_8));
    }

    static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Long.parseLong(decoded);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_INPUT, exception);
        }
    }
}
