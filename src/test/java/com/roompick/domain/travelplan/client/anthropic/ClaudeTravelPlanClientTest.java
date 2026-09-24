package com.roompick.domain.travelplan.client.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roompick.domain.travelplan.model.TravelPlanCandidate;
import com.roompick.domain.travelplan.model.TravelPlanLlmRequest;
import com.roompick.domain.travelplan.model.TravelPlanLlmResult;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.travelplan.TravelPlanLlmProperties;

/**
 * Claude 응답을 제공자 중립 결과로 변환하는 Client의 동작을 검증합니다.
 *
 * 실제 Claude API를 호출하지 않고 SDK 클라이언트를 대역으로 대체합니다.
 */
@ExtendWith(MockitoExtension.class)
class ClaudeTravelPlanClientTest {

    @Mock
    private AnthropicClient anthropicClient;

    @Mock
    private MessageService messageService;

    private ClaudeTravelPlanClient claudeTravelPlanClient;

    @BeforeEach
    void setUp() {
        claudeTravelPlanClient =
            new ClaudeTravelPlanClient(
                anthropicClient,
                new ObjectMapper(),
                new TravelPlanLlmProperties(
                    "test-anthropic-api-key",
                    "claude-opus-5",
                    Duration.ofSeconds(60)
                )
            );
    }

    @Test
    @DisplayName("Claude가 반환한 JSON 일정과 추천을 결과 모델로 변환한다")
    void convertClaudeJsonToResult() {
        // given
        givenClaudeResponse(
            """
            {
              "itinerary": [
                {"day": 1, "summary": "도심 산책", "activities": ["경복궁", "북촌"]}
              ],
              "recommendations": [
                {"candidateIndex": 0, "reason": "일정 동선과 가깝습니다."}
              ]
            }
            """
        );

        // when
        TravelPlanLlmResult result =
            claudeTravelPlanClient.generate(request());

        // then
        assertThat(result.itinerary()).hasSize(1);
        assertThat(result.itinerary().get(0).day()).isEqualTo(1);
        assertThat(result.itinerary().get(0).activities())
            .containsExactly("경복궁", "북촌");

        assertThat(result.selections()).hasSize(1);
        assertThat(result.selections().get(0).candidateIndex()).isZero();
        assertThat(result.selections().get(0).reason())
            .isEqualTo("일정 동선과 가깝습니다.");
    }

    @Test
    @DisplayName("응답의 토큰 사용량을 비용 감사용으로 추출한다")
    void extractTokenUsageForCostAudit() {
        // given
        givenClaudeResponse(
            "{\"itinerary\": [], \"recommendations\": []}"
        );

        // when
        TravelPlanLlmResult result =
            claudeTravelPlanClient.generate(request());

        // then
        assertThat(result.tokenUsage().inputTokens()).isEqualTo(1200);
        assertThat(result.tokenUsage().outputTokens()).isEqualTo(800);
    }

    @Test
    @DisplayName("코드 블록으로 감싸인 JSON 응답도 파싱한다")
    void parseJsonWrappedInCodeFence() {
        // given
        givenClaudeResponse(
            """
            ```json
            {"itinerary": [{"day": 1, "summary": "휴식", "activities": []}],
             "recommendations": []}
            ```
            """
        );

        // when
        TravelPlanLlmResult result =
            claudeTravelPlanClient.generate(request());

        // then
        assertThat(result.itinerary()).hasSize(1);
        assertThat(result.selections()).isEmpty();
    }

    @Test
    @DisplayName("후보 범위를 벗어난 candidateIndex는 추천에서 제외한다")
    void dropSelectionOutOfCandidateRange() {
        // given
        givenClaudeResponse(
            """
            {
              "itinerary": [{"day": 1, "summary": "관광", "activities": ["시장"]}],
              "recommendations": [
                {"candidateIndex": 0, "reason": "적합합니다."},
                {"candidateIndex": 5, "reason": "존재하지 않는 후보입니다."},
                {"candidateIndex": -1, "reason": "잘못된 인덱스입니다."}
              ]
            }
            """
        );

        // when
        TravelPlanLlmResult result =
            claudeTravelPlanClient.generate(request());

        // then
        assertThat(result.selections()).hasSize(1);
        assertThat(result.selections().get(0).candidateIndex()).isZero();
    }

    @Test
    @DisplayName("JSON이 아닌 응답은 응답 오류로 변환한다")
    void convertNonJsonResponseToInvalidResponseError() {
        // given
        givenClaudeResponse("죄송하지만 계획을 만들 수 없습니다.");

        // when & then
        assertThatThrownBy(
            () -> claudeTravelPlanClient.generate(request())
        )
            .isInstanceOf(BusinessException.class)
            .extracting(
                exception ->
                    ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.TRAVEL_PLAN_LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("itinerary가 없는 JSON 응답은 응답 오류로 변환한다")
    void convertMissingItineraryToInvalidResponseError() {
        // given
        givenClaudeResponse("{\"recommendations\": []}");

        // when & then
        assertThatThrownBy(
            () -> claudeTravelPlanClient.generate(request())
        )
            .isInstanceOf(BusinessException.class)
            .extracting(
                exception ->
                    ((BusinessException) exception).getErrorCode()
            )
            .isEqualTo(ErrorCode.TRAVEL_PLAN_LLM_INVALID_RESPONSE);
    }

    @Test
    @DisplayName("요청에는 설정된 모델과 후보 인덱스 안내가 포함된다")
    void includeModelAndCandidateIndexInRequest() {
        // given
        givenClaudeResponse(
            "{\"itinerary\": [], \"recommendations\": []}"
        );

        ArgumentCaptor<MessageCreateParams> captor =
            ArgumentCaptor.forClass(MessageCreateParams.class);

        // when
        claudeTravelPlanClient.generate(request());

        // then
        org.mockito.BDDMockito.then(messageService)
            .should()
            .create(captor.capture());

        MessageCreateParams params = captor.getValue();

        assertThat(params.model())
            .isEqualTo(Model.of("claude-opus-5"));
        assertThat(params.maxTokens()).isEqualTo(16000L);
        assertThat(captor.getValue().toString())
            .contains("candidateIndex 0");
    }

    private void givenClaudeResponse(
        String text
    ) {
        /*
         * Message 대역의 stubbing을 먼저 끝내야 한다.
         * given(...) 안에서 다시 given(...)을 호출하면
         * Mockito가 미완료 stubbing으로 판단한다.
         */
        Message message = message(text);

        given(anthropicClient.messages())
            .willReturn(messageService);

        given(messageService.create(any(MessageCreateParams.class)))
            .willReturn(message);
    }

    /**
     * 응답 본문 파싱과 토큰 사용량 추출만 검증하므로
     * content와 usage만 제공하는 Message 대역을 사용합니다.
     *
     * 본문이 비어 있는 경우에는 사용량 추출까지 도달하지 않으므로
     * usage 스텁은 lenient로 둡니다.
     */
    private Message message(
        String text
    ) {
        Message message = org.mockito.Mockito.mock(Message.class);

        given(message.content())
            .willReturn(
                List.of(
                    ContentBlock.ofText(
                        TextBlock.builder()
                            .text(text)
                            .citations(List.of())
                            .build()
                    )
                )
            );

        Usage usage = org.mockito.Mockito.mock(Usage.class);
        lenient().when(usage.inputTokens()).thenReturn(1200L);
        lenient().when(usage.outputTokens()).thenReturn(800L);
        lenient().when(message.usage()).thenReturn(usage);

        return message;
    }

    private TravelPlanLlmRequest request() {
        return new TravelPlanLlmRequest(
            37.5665,
            126.9780,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 3),
            2,
            List.of(
                new TravelPlanCandidate(
                    10L,
                    "룸픽 호텔",
                    "서울특별시",
                    1.2
                )
            )
        );
    }
}
