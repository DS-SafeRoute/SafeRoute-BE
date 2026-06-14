package com.saferoute.global.response;

/**
 * 공통 응답 포맷
 * {
 *   "success": true,
 *   "message": "건물 생성 성공",
 *   "data": { ... }
 * }
 */

public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("요청이 성공했습니다.", data);
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}