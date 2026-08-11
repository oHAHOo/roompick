package com.roompick.global.common.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.s3.S3Properties;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3ImageUploaderTest {

    private static final byte[] JPEG_BYTES =
        {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
    private static final byte[] PNG_BYTES =
        {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] WEBP_BYTES =
        {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};

    @Mock
    private S3Client s3Client;

    @Mock
    private CloudFrontClient cloudFrontClient;

    private final S3Properties properties =
        new S3Properties("test-bucket", "ap-northeast-2", "access-key", "secret-key", null, null);

    private final S3Properties cdnProperties =
        new S3Properties("test-bucket", "ap-northeast-2", "access-key", "secret-key",
            "images.roompick.ina3700.click", null);

    private final S3Properties cdnWithInvalidationProperties =
        new S3Properties("test-bucket", "ap-northeast-2", "access-key", "secret-key",
            "images.roompick.ina3700.click", "E3PAC8MQVLGDZQ");

    private S3ImageUploader sut;

    @Test
    void 이미지를_업로드하면_S3_URL을_반환한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file =
            new MockMultipartFile("file", "room.jpg", "image/jpeg", JPEG_BYTES);

        String url = sut.upload(file, "rooms");

        assertThat(url).startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/rooms/");
        assertThat(url).endsWith(".jpg");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void CDN_도메인이_설정되어_있으면_CDN_URL을_반환한다() {
        sut = new S3ImageUploader(s3Client, cdnProperties, cloudFrontClient);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file =
            new MockMultipartFile("file", "room.jpg", "image/jpeg", JPEG_BYTES);

        String url = sut.upload(file, "rooms");

        assertThat(url).startsWith("https://images.roompick.ina3700.click/rooms/");
        assertThat(url).endsWith(".jpg");
    }

    @Test
    void 여러_이미지를_업로드하면_URL_목록을_반환한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file1 =
            new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);
        MockMultipartFile file2 =
            new MockMultipartFile("file", "b.webp", "image/webp", WEBP_BYTES);

        List<String> urls = sut.uploadAll(List.of(file1, file2), "accommodations");

        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).endsWith(".png");
        assertThat(urls.get(1)).endsWith(".webp");
    }

    @Test
    void 빈_파일이면_예외가_발생한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        MockMultipartFile emptyFile =
            new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> sut.upload(emptyFile, "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 지원하지_않는_형식이면_예외가_발생한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        MockMultipartFile file =
            new MockMultipartFile("file", "doc.gif", "image/gif", "bytes".getBytes());

        assertThatThrownBy(() -> sut.upload(file, "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 용량_제한을_초과하면_예외가_발생한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        byte[] tooLarge = new byte[11 * 1024 * 1024];
        MockMultipartFile file =
            new MockMultipartFile("file", "big.jpg", "image/jpeg", tooLarge);

        assertThatThrownBy(() -> sut.upload(file, "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_SIZE_EXCEEDED);
    }

    @Test
    void 선언한_형식과_실제_파일_시그니처가_다르면_예외가_발생한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        MockMultipartFile file =
            new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThatThrownBy(() -> sut.upload(file, "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 최대_개수를_초과하면_예외가_발생하고_업로드를_시도하지_않는다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        List<MockMultipartFile> files = java.util.stream.IntStream.range(0, 11)
            .mapToObj(i -> new MockMultipartFile("file", i + ".jpg", "image/jpeg", JPEG_BYTES))
            .map(MockMultipartFile.class::cast)
            .toList();

        assertThatThrownBy(() -> sut.uploadAll(List.copyOf(files), "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_COUNT_EXCEEDED);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 뒤쪽_파일_검증에_실패하면_어떤_파일도_업로드하지_않는다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        MockMultipartFile validFile =
            new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG_BYTES);
        MockMultipartFile invalidFile =
            new MockMultipartFile("file", "b.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThatThrownBy(() -> sut.uploadAll(List.of(validFile, invalidFile), "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_IMAGE_TYPE);

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void 업로드_도중_실패하면_이미_업로드된_파일을_정리한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);
        MockMultipartFile file1 =
            new MockMultipartFile("file", "a.jpg", "image/jpeg", JPEG_BYTES);
        MockMultipartFile file2 =
            new MockMultipartFile("file", "b.jpg", "image/jpeg", JPEG_BYTES);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build())
            .thenThrow(SdkClientException.create("network error"));

        assertThatThrownBy(() -> sut.uploadAll(List.of(file1, file2), "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_UPLOAD_FAILED);

        verify(s3Client, times(2))
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(s3Client).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void delete는_S3_객체를_삭제한다() {
        sut = new S3ImageUploader(s3Client, properties, cloudFrontClient);

        sut.delete("https://test-bucket.s3.ap-northeast-2.amazonaws.com/rooms/a.jpg");

        verify(s3Client).deleteObject(
            any(java.util.function.Consumer.class)
        );
    }

    @Test
    void CDN_도메인_설정_시_기존_S3_원본_URL도_삭제할_수_있다() {
        sut = new S3ImageUploader(s3Client, cdnProperties, cloudFrontClient);

        sut.delete("https://test-bucket.s3.ap-northeast-2.amazonaws.com/rooms/legacy.jpg");
        sut.delete("https://images.roompick.ina3700.click/rooms/new.jpg");

        verify(s3Client, times(2)).deleteObject(
            any(java.util.function.Consumer.class)
        );
    }

    @Test
    void 배포_ID가_설정되어_있지_않으면_캐시_무효화를_하지_않는다() {
        sut = new S3ImageUploader(s3Client, cdnProperties, cloudFrontClient);

        sut.delete("https://images.roompick.ina3700.click/rooms/a.jpg");

        verify(cloudFrontClient, never()).createInvalidation(any(CreateInvalidationRequest.class));
    }

    @Test
    void 삭제_시_배포_ID가_설정되어_있으면_캐시를_무효화한다() {
        sut = new S3ImageUploader(s3Client, cdnWithInvalidationProperties, cloudFrontClient);
        when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
            .thenReturn(CreateInvalidationResponse.builder().build());

        sut.delete("https://images.roompick.ina3700.click/rooms/a.jpg");

        ArgumentCaptor<CreateInvalidationRequest> captor = ArgumentCaptor.forClass(CreateInvalidationRequest.class);
        verify(cloudFrontClient).createInvalidation(captor.capture());
        assertThat(captor.getValue().distributionId()).isEqualTo("E3PAC8MQVLGDZQ");
        assertThat(captor.getValue().invalidationBatch().paths().items()).containsExactly("/rooms/a.jpg");
    }

    @Test
    void 일괄_삭제_시_CDN_캐시도_한번에_무효화한다() {
        sut = new S3ImageUploader(s3Client, cdnWithInvalidationProperties, cloudFrontClient);
        when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
            .thenReturn(CreateInvalidationResponse.builder().build());

        sut.deleteAll(List.of(
            "https://images.roompick.ina3700.click/rooms/a.jpg",
            "https://images.roompick.ina3700.click/rooms/b.jpg"
        ));

        ArgumentCaptor<CreateInvalidationRequest> captor = ArgumentCaptor.forClass(CreateInvalidationRequest.class);
        verify(cloudFrontClient).createInvalidation(captor.capture());
        assertThat(captor.getValue().invalidationBatch().paths().items())
            .containsExactly("/rooms/a.jpg", "/rooms/b.jpg");
    }
}
