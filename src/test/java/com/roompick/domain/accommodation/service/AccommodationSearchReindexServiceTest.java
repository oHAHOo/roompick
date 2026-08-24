package com.roompick.domain.accommodation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import com.roompick.domain.accommodation.document.AccommodationSearchDocument;
import com.roompick.domain.accommodation.entity.AccommodationStatus;
import com.roompick.domain.accommodation.repository.AccommodationSearchIndexProjection;
import com.roompick.domain.accommodation.repository.AccommodationSearchIndexRepository;

/**
 * Elasticsearch 숙소 검색 전체 재색인 Service 테스트입니다.
 *
 * 기존 인덱스 재생성,
 * MySQL Keyset Pagination,
 * Elasticsearch Bulk 저장,
 * 마지막 refresh 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AccommodationSearchReindexServiceTest {

    @Mock
    private AccommodationSearchIndexRepository
        accommodationSearchIndexRepository;

    @Mock
    private ElasticsearchOperations
        elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    /**
     * 전체 재색인이 여러 배치로 정상 처리되는지 검증합니다.
     *
     * 첫 번째 조회는 ID 0 이후,
     * 두 번째 조회는 첫 배치의 마지막 ID 이후,
     * 마지막 조회는 두 번째 배치의 마지막 ID 이후로 진행되어야 합니다.
     */
    @Test
    void 전체_숙소_검색_인덱스_재생성에_성공한다() {
        // given
        AccommodationSearchIndexProjection first =
            createProjection(
                1L,
                "룸픽 서울 호텔",
                "서울특별시 중구",
                AccommodationStatus.ACTIVE,
                "37.566500",
                "126.978000"
            );

        AccommodationSearchIndexProjection second =
            createProjection(
                2L,
                "룸픽 명동 호텔",
                "서울특별시 중구 명동",
                AccommodationStatus.ACTIVE,
                "37.560900",
                "126.986000"
            );

        AccommodationSearchIndexProjection third =
            createProjection(
                3L,
                "운영 중단 숙소",
                "서울특별시 종로구",
                AccommodationStatus.INACTIVE,
                "37.570000",
                "126.990000"
            );

        given(
            elasticsearchOperations.indexOps(
                AccommodationSearchDocument.class
            )
        ).willReturn(
            indexOperations
        );

        given(
            indexOperations.exists()
        ).willReturn(
            true
        );

        given(
            indexOperations.delete()
        ).willReturn(
            true
        );

        given(
            indexOperations.createWithMapping()
        ).willReturn(
            true
        );

        given(
            accommodationSearchIndexRepository
                .findSearchIndexBatchAfterId(
                    0L,
                    PageRequest.of(
                        0,
                        1000
                    )
                )
        ).willReturn(
            List.of(
                first,
                second
            )
        );

        given(
            accommodationSearchIndexRepository
                .findSearchIndexBatchAfterId(
                    2L,
                    PageRequest.of(
                        0,
                        1000
                    )
                )
        ).willReturn(
            List.of(
                third
            )
        );

        given(
            accommodationSearchIndexRepository
                .findSearchIndexBatchAfterId(
                    3L,
                    PageRequest.of(
                        0,
                        1000
                    )
                )
        ).willReturn(
            List.of()
        );

        // when
        AccommodationSearchReindexService service =
            new AccommodationSearchReindexService(
                accommodationSearchIndexRepository,
                elasticsearchOperations
            );

        long result =
            service.reindexAll();

        // then
        assertThat(result)
            .isEqualTo(3L);

        /*
         * 총 3건을 두 개 배치로 조회했으므로
         * Elasticsearch Bulk 저장은 정확히 2회 실행되어야 합니다.
         */
        then(elasticsearchOperations)
            .should(
                times(2)
            )
            .bulkIndex(
                anyList(),
                org.mockito.ArgumentMatchers.eq(
                    AccommodationSearchDocument.class
                )
            );

        /*
         * Keyset Pagination이 각 배치의 마지막 ID를
         * 다음 조회 시작점으로 사용하는지 확인합니다.
         */
        then(accommodationSearchIndexRepository)
            .should()
            .findSearchIndexBatchAfterId(
                0L,
                PageRequest.of(
                    0,
                    1000
                )
            );

        then(accommodationSearchIndexRepository)
            .should()
            .findSearchIndexBatchAfterId(
                2L,
                PageRequest.of(
                    0,
                    1000
                )
            );

        then(accommodationSearchIndexRepository)
            .should()
            .findSearchIndexBatchAfterId(
                3L,
                PageRequest.of(
                    0,
                    1000
                )
            );

        /*
         * 전체 Bulk 저장이 끝난 뒤
         * refresh는 한 번만 실행되어야 합니다.
         */
        then(indexOperations)
            .should()
            .refresh();
    }

    /**
     * 기존 Elasticsearch 인덱스가 존재하는 경우
     * 삭제 후 새 매핑으로 다시 생성하는지 검증합니다.
     */
    @Test
    void 기존_인덱스가_있으면_삭제한_뒤_다시_생성한다() {
        // given
        given(
            elasticsearchOperations.indexOps(
                AccommodationSearchDocument.class
            )
        ).willReturn(
            indexOperations
        );

        given(
            indexOperations.exists()
        ).willReturn(
            true
        );

        given(
            indexOperations.delete()
        ).willReturn(
            true
        );

        given(
            indexOperations.createWithMapping()
        ).willReturn(
            true
        );

        given(
            accommodationSearchIndexRepository
                .findSearchIndexBatchAfterId(
                    0L,
                    PageRequest.of(
                        0,
                        1000
                    )
                )
        ).willReturn(
            List.of()
        );

        AccommodationSearchReindexService service =
            new AccommodationSearchReindexService(
                accommodationSearchIndexRepository,
                elasticsearchOperations
            );

        // when
        long result =
            service.reindexAll();

        // then
        assertThat(result)
            .isZero();

        /*
         * 인덱스 존재 여부 확인 → 삭제 → 생성 순서를 보장합니다.
         */
        InOrder inOrder =
            org.mockito.Mockito.inOrder(
                indexOperations
            );

        inOrder.verify(
            indexOperations
        ).exists();

        inOrder.verify(
            indexOperations
        ).delete();

        inOrder.verify(
            indexOperations
        ).createWithMapping();

        inOrder.verify(
            indexOperations
        ).refresh();

        then(elasticsearchOperations)
            .should(never())
            .bulkIndex(
                anyList(),
                org.mockito.ArgumentMatchers.eq(
                    AccommodationSearchDocument.class
                )
            );
    }

    /**
     * 기존 인덱스가 없는 경우에는
     * 삭제하지 않고 바로 새 인덱스를 생성합니다.
     */
    @Test
    void 기존_인덱스가_없으면_삭제하지_않고_생성한다() {
        // given
        given(
            elasticsearchOperations.indexOps(
                AccommodationSearchDocument.class
            )
        ).willReturn(
            indexOperations
        );

        given(
            indexOperations.exists()
        ).willReturn(
            false
        );

        given(
            indexOperations.createWithMapping()
        ).willReturn(
            true
        );

        given(
            accommodationSearchIndexRepository
                .findSearchIndexBatchAfterId(
                    0L,
                    PageRequest.of(
                        0,
                        1000
                    )
                )
        ).willReturn(
            List.of()
        );

        AccommodationSearchReindexService service =
            new AccommodationSearchReindexService(
                accommodationSearchIndexRepository,
                elasticsearchOperations
            );

        // when
        long result =
            service.reindexAll();

        // then
        assertThat(result)
            .isZero();

        then(indexOperations)
            .should(never())
            .delete();

        then(indexOperations)
            .should()
            .createWithMapping();

        then(indexOperations)
            .should()
            .refresh();
    }

    /**
     * 기존 인덱스 삭제에 실패하면
     * 데이터 조회나 Bulk 저장을 진행하지 않아야 합니다.
     */
    @Test
    void 기존_인덱스_삭제에_실패하면_예외가_발생한다() {
        // given
        given(
            elasticsearchOperations.indexOps(
                AccommodationSearchDocument.class
            )
        ).willReturn(
            indexOperations
        );

        given(
            indexOperations.exists()
        ).willReturn(
            true
        );

        given(
            indexOperations.delete()
        ).willReturn(
            false
        );

        AccommodationSearchReindexService service =
            new AccommodationSearchReindexService(
                accommodationSearchIndexRepository,
                elasticsearchOperations
            );

        // when & then
        assertThatThrownBy(
            service::reindexAll
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "기존 Elasticsearch 숙소 검색 인덱스를 삭제하지 못했습니다."
            );

        then(indexOperations)
            .should(never())
            .createWithMapping();

        then(accommodationSearchIndexRepository)
            .shouldHaveNoInteractions();

        then(elasticsearchOperations)
            .should(never())
            .bulkIndex(
                anyList(),
                org.mockito.ArgumentMatchers.eq(
                    AccommodationSearchDocument.class
                )
            );
    }

    /**
     * 새 Elasticsearch 인덱스 생성에 실패하면
     * 재색인 데이터 조회를 시작하지 않아야 합니다.
     */
    @Test
    void 새_인덱스_생성에_실패하면_예외가_발생한다() {
        // given
        given(
            elasticsearchOperations.indexOps(
                AccommodationSearchDocument.class
            )
        ).willReturn(
            indexOperations
        );

        given(
            indexOperations.exists()
        ).willReturn(
            false
        );

        given(
            indexOperations.createWithMapping()
        ).willReturn(
            false
        );

        AccommodationSearchReindexService service =
            new AccommodationSearchReindexService(
                accommodationSearchIndexRepository,
                elasticsearchOperations
            );

        // when & then
        assertThatThrownBy(
            service::reindexAll
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "Elasticsearch 숙소 검색 인덱스를 생성하지 못했습니다."
            );

        then(accommodationSearchIndexRepository)
            .shouldHaveNoInteractions();

        then(elasticsearchOperations)
            .should(never())
            .bulkIndex(
                anyList(),
                org.mockito.ArgumentMatchers.eq(
                    AccommodationSearchDocument.class
                )
            );

        then(indexOperations)
            .should(never())
            .refresh();
    }

    /**
     * 테스트용 재색인 Projection을 생성합니다.
     */
    private AccommodationSearchIndexProjection createProjection(
        Long accommodationId,
        String name,
        String address,
        AccommodationStatus status,
        String latitude,
        String longitude
    ) {
        return new AccommodationSearchIndexProjection() {

            @Override
            public Long getAccommodationId() {
                return accommodationId;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getAddress() {
                return address;
            }

            @Override
            public AccommodationStatus getStatus() {
                return status;
            }

            @Override
            public BigDecimal getLatitude() {
                return new BigDecimal(
                    latitude
                );
            }

            @Override
            public BigDecimal getLongitude() {
                return new BigDecimal(
                    longitude
                );
            }

            @Override
            public String getImageUrl() {
                return null;
            }
        };
    }
}
