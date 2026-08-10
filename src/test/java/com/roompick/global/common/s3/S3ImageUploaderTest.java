package com.roompick.global.common.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.s3.S3Properties;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3ImageUploaderTest {

    @Mock
    private S3Client s3Client;

    private final S3Properties properties =
        new S3Properties("test-bucket", "ap-northeast-2", "access-key", "secret-key");

    private S3ImageUploader sut;

    @Test
    void 이미지를_업로드하면_S3_URL을_반환한다() {
        sut = new S3ImageUploader(s3Client, properties);
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file =
            new MockMultipartFile("file", "room.jpg", "image/jpeg", "image-bytes".getBytes());

        String url = sut.upload(file, "rooms");

        assertThat(url).startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/rooms/");
        assertThat(url).endsWith(".jpg");
        verify(s3Client).putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void 여러_이미지를_업로드하면_URL_목록을_반환한다() {
        sut = new S3ImageUploader(s3Client, properties);
        when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
            .thenReturn(PutObjectResponse.builder().build());

        MockMultipartFile file1 =
            new MockMultipartFile("file", "a.png", "image/png", "a".getBytes());
        MockMultipartFile file2 =
            new MockMultipartFile("file", "b.webp", "image/webp", "b".getBytes());

        List<String> urls = sut.uploadAll(List.of(file1, file2), "accommodations");

        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).endsWith(".png");
        assertThat(urls.get(1)).endsWith(".webp");
    }

    @Test
    void 빈_파일이면_예외가_발생한다() {
        sut = new S3ImageUploader(s3Client, properties);
        MockMultipartFile emptyFile =
            new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> sut.upload(emptyFile, "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void 지원하지_않는_형식이면_예외가_발생한다() {
        sut = new S3ImageUploader(s3Client, properties);
        MockMultipartFile file =
            new MockMultipartFile("file", "doc.gif", "image/gif", "bytes".getBytes());

        assertThatThrownBy(() -> sut.upload(file, "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 용량_제한을_초과하면_예외가_발생한다() {
        sut = new S3ImageUploader(s3Client, properties);
        byte[] tooLarge = new byte[11 * 1024 * 1024];
        MockMultipartFile file =
            new MockMultipartFile("file", "big.jpg", "image/jpeg", tooLarge);

        assertThatThrownBy(() -> sut.upload(file, "rooms"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.IMAGE_SIZE_EXCEEDED);
    }
}
