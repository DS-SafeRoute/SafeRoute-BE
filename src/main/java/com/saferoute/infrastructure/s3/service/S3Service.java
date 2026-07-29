package com.saferoute.infrastructure.s3.service;

import com.saferoute.global.api.error.S3ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.config.S3Properties;
import com.saferoute.infrastructure.s3.dto.S3UploadResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3Service {

    private static final String FLOOR_PLAN_PREFIX = "floor-plans/";

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3Service(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    public S3UploadResponse upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(S3ErrorCode.EMPTY_FILE);
        }

        String key = createObjectKey(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException | SdkException exception) {
            throw new ApiException(S3ErrorCode.UPLOAD_FAILED, exception);
        }

        return new S3UploadResponse(
                s3Properties.bucket(),
                key,
                "s3://" + s3Properties.bucket() + "/" + key,
                file.getSize(),
                contentType
        );
    }

    private String createObjectKey(String originalFilename) {
        String filename = originalFilename == null ? "file" : originalFilename;
        filename = filename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        filename = filename.replaceAll("[^\\p{L}\\p{N}._-]", "_");

        if (filename.isBlank()) {
            filename = "file";
        }

        return FLOOR_PLAN_PREFIX + UUID.randomUUID() + "-" + filename;
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return contentType;
    }
}
