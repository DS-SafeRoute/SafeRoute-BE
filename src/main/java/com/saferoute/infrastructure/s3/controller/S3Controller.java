package com.saferoute.infrastructure.s3.controller;

import com.saferoute.global.api.response.ApiResponse;
import com.saferoute.global.api.response.S3SuccessCode;
import com.saferoute.infrastructure.s3.dto.S3UploadResponse;
import com.saferoute.infrastructure.s3.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/test/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<S3UploadResponse>> upload(
            @RequestPart("file") MultipartFile file
    ) {
        S3UploadResponse response = s3Service.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(S3SuccessCode.S3_FILE_UPLOADED, response));
    }
}
