package com.saferoute.infrastructure.s3.controller;

import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.S3SuccessCode;
import com.saferoute.infrastructure.s3.dto.S3UploadResponse;
import com.saferoute.infrastructure.s3.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "S3 파일 업로드", description = "도면 이미지 등 S3 업로드 관련 API")
@RestController
@RequestMapping("/api/v1/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    @Operation(
            summary = "파일 업로드",
            description = """
                    multipart/form-data로 전달된 파일을 S3의 "floor-plans/" 경로 하위에
                    원본 그대로 업로드하고, 저장된 bucket, key, S3 URI 등을 반환합니다.

                    이 API는 파일을 영구 저장하는 업로드 전용 API이며 presigned URL을
                    발급하지 않습니다. 응답의 s3Uri("s3://...")는 브라우저에서 직접 접근할
                    수 없으므로, 업로드한 이미지를 화면에 표시하려면 key를 이용해 별도의
                    조회(presigned GET URL 발급) API를 호출해야 합니다.

                    key는 원본 파일명에서 경로를 제거하고 영숫자 및 일부 특수문자 외의
                    문자를 '_'로 치환한 뒤 "floor-plans/{UUID}-파일명" 형태로 생성되므로,
                    같은 파일명을 여러 번 업로드해도 서로 다른 key가 생성되어 기존 파일을
                    덮어쓰지 않습니다.

                    빈 파일을 업로드하면 400 EMPTY_FILE 오류가 발생합니다. 파일 크기나
                    확장자 제한은 서버에서 별도로 검사하지 않으므로, 필요한 제약은
                    프론트에서 업로드 전에 확인해야 합니다. content-type을 지정하지
                    않으면 application/octet-stream으로 저장됩니다.

                    로그인 사용자 중 MANAGER 권한을 가진 사용자만 호출할 수 있습니다.
                    """
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<S3UploadResponse>> upload(
            @RequestPart("file") MultipartFile file
    ) {
        S3UploadResponse response = s3Service.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(S3SuccessCode.S3_FILE_UPLOADED, response));
    }
}
