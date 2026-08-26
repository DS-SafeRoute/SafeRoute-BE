package com.saferoute.infrastructure.s3.service;

import com.saferoute.global.api.error.S3ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.config.S3Properties;
import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import com.saferoute.infrastructure.s3.dto.PresignedPutUrl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@Service
@RequiredArgsConstructor
public class S3PresignedUrlService {

    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public PresignedPutUrl createPutUrl(String objectKey, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignedUrlExpiration())
                .putObjectRequest(putObjectRequest)
                .build();

        try {
            PresignedPutObjectRequest presignedRequest =
                    s3Presigner.presignPutObject(presignRequest);
            return new PresignedPutUrl(
                    presignedRequest.url().toString(),
                    presignedRequest.expiration()
            );
        } catch (SdkException exception) {
            throw new ApiException(S3ErrorCode.PRESIGNED_URL_GENERATION_FAILED, exception);
        }
    }

    // 관리자 화면이 S3 object key만으로는 이미지를 열람할 수 없어(버킷은 Public이 아님) 조회용으로 발급한다.
    public PresignedGetUrl createGetUrl(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(s3Properties.presignedGetUrlExpiration())
                .getObjectRequest(getObjectRequest)
                .build();

        try {
            PresignedGetObjectRequest presignedRequest =
                    s3Presigner.presignGetObject(presignRequest);
            return new PresignedGetUrl(
                    presignedRequest.url().toString(),
                    presignedRequest.expiration()
            );
        } catch (SdkException exception) {
            throw new ApiException(S3ErrorCode.PRESIGNED_GET_URL_GENERATION_FAILED, exception);
        }
    }
}
