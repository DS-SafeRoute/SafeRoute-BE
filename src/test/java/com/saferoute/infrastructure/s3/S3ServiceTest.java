package com.saferoute.infrastructure.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saferoute.global.api.error.S3ErrorCode;
import com.saferoute.global.api.exception.ApiException;
import com.saferoute.infrastructure.s3.config.S3Properties;
import com.saferoute.infrastructure.s3.dto.S3UploadResponse;
import java.time.Duration;

import com.saferoute.infrastructure.s3.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
                "test-bucket",
                Duration.ofMinutes(1),
                Duration.ofMinutes(5)
        );
        s3Service = new S3Service(s3Client, properties);
    }

    @Test
    void uploadsFileToTestPrefix() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample image.png",
                "image/png",
                "test-data".getBytes()
        );
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        S3UploadResponse response = s3Service.upload(file);

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key())
                .startsWith("floor-plans/")
                .endsWith("-sample_image.png");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.contentLength()).isEqualTo(file.getSize());

        assertThat(response.bucket()).isEqualTo("test-bucket");
        assertThat(response.key()).isEqualTo(request.key());
        assertThat(response.s3Uri()).isEqualTo("s3://test-bucket/" + request.key());
        assertThat(response.size()).isEqualTo(file.getSize());
        assertThat(response.contentType()).isEqualTo("image/png");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertThatThrownBy(() -> s3Service.upload(file))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(S3ErrorCode.EMPTY_FILE);

        verify(s3Client, never())
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void findsExistingObjectWithHeadRequest() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(s3Service.objectExists("training/session/events/CCTV_001/event.jpg")).isTrue();

        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3Client).headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key())
                .isEqualTo("training/session/events/CCTV_001/event.jpg");
    }

    @Test
    void returnsFalseWhenObjectDoesNotExist() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertThat(s3Service.objectExists("missing.jpg")).isFalse();
    }

    @Test
    void mapsS3ServerErrorToObjectCheckFailed() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("unavailable").build());

        assertThatThrownBy(() -> s3Service.objectExists("event.jpg"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", S3ErrorCode.OBJECT_CHECK_FAILED);
    }

    @Test
    void mapsSdkFailureToObjectCheckFailed() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("network error").build());

        assertThatThrownBy(() -> s3Service.objectExists("event.jpg"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", S3ErrorCode.OBJECT_CHECK_FAILED);
    }
}
