package com.saferoute.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.saferoute.global.api.error.S3ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.config.S3Properties;
import com.saferoute.infrastructure.s3.dto.PresignedGetUrl;
import com.saferoute.infrastructure.s3.dto.PresignedPutUrl;
import com.saferoute.infrastructure.s3.service.S3PresignedUrlService;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3PresignedUrlServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedRequest;

    @Mock
    private PresignedGetObjectRequest presignedGetRequest;

    private S3PresignedUrlService service;

    @BeforeEach
    void setUp() {
        service = new S3PresignedUrlService(
                s3Presigner,
                new S3Properties("test-bucket", Duration.ofMinutes(1), Duration.ofMinutes(5))
        );
    }

    @Test
    void createsOneMinuteJpegPutUrlForServerGeneratedKey() throws Exception {
        String key = "training/session-id/monitoring/CCTV_001/1000.jpg";
        Instant expiration = Instant.parse("2026-08-20T00:01:00Z");
        given(presignedRequest.url()).willReturn(URI.create("https://example.com/upload").toURL());
        given(presignedRequest.expiration()).willReturn(expiration);
        given(s3Presigner.presignPutObject(org.mockito.ArgumentMatchers.any(
                PutObjectPresignRequest.class))).willReturn(presignedRequest);

        PresignedPutUrl result = service.createPutUrl(key, "image/jpeg");

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectPresignRequest request = captor.getValue();
        PutObjectRequest putObjectRequest = request.putObjectRequest();

        assertThat(request.signatureDuration()).isEqualTo(Duration.ofMinutes(1));
        assertThat(putObjectRequest.bucket()).isEqualTo("test-bucket");
        assertThat(putObjectRequest.key()).isEqualTo(key);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/jpeg");
        assertThat(result.uploadUrl()).isEqualTo("https://example.com/upload");
        assertThat(result.expiresAt()).isEqualTo(expiration);
    }

    @Test
    void mapsAwsFailureToApiError() {
        given(s3Presigner.presignPutObject(org.mockito.ArgumentMatchers.any(
                PutObjectPresignRequest.class)))
                .willThrow(SdkClientException.create("presign failed"));

        assertThatThrownBy(() -> service.createPutUrl("key", "image/jpeg"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        S3ErrorCode.PRESIGNED_URL_GENERATION_FAILED
                );
    }

    @Test
    void createsFiveMinuteGetUrlForObjectKey() throws Exception {
        String key = "training/session-id/events/CCTV_001/event-id.jpg";
        Instant expiration = Instant.parse("2026-08-20T00:05:00Z");
        given(presignedGetRequest.url()).willReturn(URI.create("https://example.com/view").toURL());
        given(presignedGetRequest.expiration()).willReturn(expiration);
        given(s3Presigner.presignGetObject(org.mockito.ArgumentMatchers.any(
                GetObjectPresignRequest.class))).willReturn(presignedGetRequest);

        PresignedGetUrl result = service.createGetUrl(key);

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        GetObjectPresignRequest request = captor.getValue();
        GetObjectRequest getObjectRequest = request.getObjectRequest();

        assertThat(request.signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(getObjectRequest.bucket()).isEqualTo("test-bucket");
        assertThat(getObjectRequest.key()).isEqualTo(key);
        assertThat(result.viewUrl()).isEqualTo("https://example.com/view");
        assertThat(result.expiresAt()).isEqualTo(expiration);
    }

    @Test
    void mapsAwsFailureToApiErrorForGetUrl() {
        given(s3Presigner.presignGetObject(org.mockito.ArgumentMatchers.any(
                GetObjectPresignRequest.class)))
                .willThrow(SdkClientException.create("presign failed"));

        assertThatThrownBy(() -> service.createGetUrl("key"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue(
                        "errorCode",
                        S3ErrorCode.PRESIGNED_GET_URL_GENERATION_FAILED
                );
    }
}
