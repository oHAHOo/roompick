package com.roompick.domain.travelplan.client.anthropic;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roompick.domain.travelplan.client.TravelPlanLlmClient;
import com.roompick.domain.travelplan.client.anthropic.dto.ClaudeTravelPlanPayloadDto;
import com.roompick.domain.travelplan.model.TravelPlanCandidate;
import com.roompick.domain.travelplan.model.TravelPlanLlmRequest;
import com.roompick.domain.travelplan.model.TravelPlanLlmResult;
import com.roompick.global.common.BusinessException;
import com.roompick.global.common.ErrorCode;
import com.roompick.global.config.travelplan.TravelPlanLlmProperties;

/**
 * Anthropic Claude API로 여행 일정과 숙소 추천을 생성하는 Client입니다.
 *
 * Claude 전용 요청·응답 타입을 제공자 중립 모델로 변환하여
 * LLM SDK 타입이 Service 계층에 노출되지 않게 합니다.
 *
 * 숙소 추천은 전달한 후보 목록의 인덱스로만 받기 때문에
 * LLM이 등록되지 않은 숙소를 만들어내도 결과에 반영되지 않습니다.
 */
@Component
public class ClaudeTravelPlanClient implements TravelPlanLlmClient {

    private static final long MAX_TOKENS = 16000L;

    private static final String SYSTEM_PROMPT = """
        당신은 한국 여행 일정을 설계하는 RoomPick의 여행 플래너입니다.

        규칙:
        1. 반드시 JSON 객체 하나만 출력하고, 코드 블록이나 설명 문장을 덧붙이지 않습니다.
        2. JSON 구조는 다음과 같습니다.
           {
             "itinerary": [
               {"day": 1, "summary": "하루 요약", "activities": ["활동1", "활동2"]}
             ],
             "recommendations": [
               {"candidateIndex": 0, "reason": "이 일정에 적합한 이유"}
             ]
           }
        3. itinerary는 체크인 날짜부터 체크아웃 전날까지 숙박일 수만큼 생성합니다.
        4. recommendations에는 사용자 메시지에 제시된 숙소 후보의 candidateIndex만 사용합니다.
           후보 목록에 없는 숙소를 추천하거나 새로운 숙소를 만들어내면 안 됩니다.
        5. 후보 목록이 비어 있으면 recommendations는 빈 배열로 반환합니다.
        6. 추천 이유는 후보 목록에 주어진 정보와 생성한 일정만 근거로 작성합니다.
           가격, 편의시설, 평점처럼 제공되지 않은 정보를 추측해서 쓰지 않습니다.
        """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;
    private final TravelPlanLlmProperties properties;

    public ClaudeTravelPlanClient(
        AnthropicClient anthropicClient,
        ObjectMapper objectMapper,
        TravelPlanLlmProperties properties
    ) {
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 여행 조건과 숙소 후보를 Claude에 전달하고 일정·추천 결과로 변환합니다.
     */
    @Override
    public TravelPlanLlmResult generate(
        TravelPlanLlmRequest request
    ) {
        MessageCreateParams params =
            MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .addUserMessage(
                    buildUserMessage(request)
                )
                .thinking(
                    ThinkingConfigAdaptive.builder().build()
                )
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.HIGH)
                        .build()
                )
                .build();

        try {
            Message message =
                anthropicClient.messages().create(params);

            return toResult(
                extractText(message),
                request.candidates().size()
            );
        } catch (ClaudeTravelPlanException exception) {
            throw new BusinessException(
                ErrorCode.TRAVEL_PLAN_LLM_INVALID_RESPONSE,
                exception
            );
        } catch (RateLimitException exception) {
            throw new BusinessException(
                ErrorCode.TRAVEL_PLAN_LLM_RATE_LIMITED,
                exception
            );
        } catch (AnthropicServiceException exception) {
            throw new BusinessException(
                ErrorCode.TRAVEL_PLAN_LLM_UNAVAILABLE,
                exception
            );
        } catch (AnthropicException exception) {
            throw convertTransportException(exception);
        }
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    /**
     * 여행 조건과 후보 숙소 목록을 사용자 메시지로 구성합니다.
     *
     * 후보는 인덱스와 함께 전달해 LLM이 인덱스로만 숙소를 지목하게 합니다.
     */
    private String buildUserMessage(
        TravelPlanLlmRequest request
    ) {
        long nights = ChronoUnit.DAYS.between(
            request.checkInDate(),
            request.checkOutDate()
        );

        StringBuilder message = new StringBuilder()
            .append("여행 조건\n")
            .append("- 중심 좌표: 위도 ")
            .append(request.latitude())
            .append(", 경도 ")
            .append(request.longitude())
            .append('\n')
            .append("- 체크인: ")
            .append(request.checkInDate())
            .append('\n')
            .append("- 체크아웃: ")
            .append(request.checkOutDate())
            .append('\n')
            .append("- 숙박일 수: ")
            .append(nights)
            .append('\n')
            .append("- 인원: ")
            .append(request.guestCount())
            .append("명\n\n");

        List<TravelPlanCandidate> candidates =
            request.candidates();

        if (candidates.isEmpty()) {
            message.append(
                "숙소 후보가 없습니다. recommendations는 빈 배열로 반환하세요.\n"
            );

            return message.toString();
        }

        message.append("숙소 후보 목록\n");

        IntStream.range(0, candidates.size())
            .forEach(index -> {
                TravelPlanCandidate candidate =
                    candidates.get(index);

                message.append("- candidateIndex ")
                    .append(index)
                    .append(": ")
                    .append(candidate.name())
                    .append(" / 주소 ")
                    .append(candidate.address())
                    .append(" / 중심 좌표에서 약 ")
                    .append(
                        String.format(
                            "%.1f",
                            candidate.distanceKm()
                        )
                    )
                    .append("km\n");
            });

        return message.toString();
    }

    /**
     * 응답 본문에서 text 블록만 이어 붙입니다.
     *
     * thinking 블록은 결과 파싱에 사용하지 않습니다.
     */
    private String extractText(
        Message message
    ) {
        String text = message.content()
            .stream()
            .map(ContentBlock::text)
            .flatMap(java.util.Optional::stream)
            .map(textBlock -> textBlock.text())
            .reduce(
                "",
                String::concat
            );

        if (text.isBlank()) {
            throw new ClaudeTravelPlanException(
                "Claude 여행 계획 응답에 텍스트 본문이 없습니다."
            );
        }

        return text;
    }

    /**
     * Claude가 반환한 JSON 본문을 제공자 중립 결과로 변환합니다.
     *
     * 후보 범위를 벗어난 candidateIndex는 신뢰할 수 없으므로 제외합니다.
     */
    private TravelPlanLlmResult toResult(
        String text,
        int candidateCount
    ) {
        ClaudeTravelPlanPayloadDto payload =
            parsePayload(text);

        if (payload.itinerary() == null) {
            throw new ClaudeTravelPlanException(
                "Claude 여행 계획 응답에 itinerary가 없습니다."
            );
        }

        List<TravelPlanLlmResult.ItineraryDay> itinerary =
            payload.itinerary()
                .stream()
                .filter(day -> day != null && day.day() != null)
                .map(day -> new TravelPlanLlmResult.ItineraryDay(
                    day.day(),
                    day.summary(),
                    day.activities() == null
                        ? List.of()
                        : List.copyOf(day.activities())
                ))
                .toList();

        List<TravelPlanLlmResult.CandidateSelection> selections =
            payload.recommendations() == null
                ? List.of()
                : payload.recommendations()
                    .stream()
                    .filter(
                        recommendation ->
                            isSelectableIndex(
                                recommendation,
                                candidateCount
                            )
                    )
                    .map(
                        recommendation ->
                            new TravelPlanLlmResult.CandidateSelection(
                                recommendation.candidateIndex(),
                                recommendation.reason()
                            )
                    )
                    .toList();

        return new TravelPlanLlmResult(
            itinerary,
            selections
        );
    }

    /**
     * 추천 항목의 인덱스가 실제 후보 범위 안에 있는지 확인합니다.
     */
    private boolean isSelectableIndex(
        ClaudeTravelPlanPayloadDto.ClaudeRecommendationDto recommendation,
        int candidateCount
    ) {
        if (
            recommendation == null
                || recommendation.candidateIndex() == null
        ) {
            return false;
        }

        int index = recommendation.candidateIndex();

        return index >= 0
            && index < candidateCount;
    }

    /**
     * 응답 텍스트를 JSON으로 파싱합니다.
     *
     * 지시에도 불구하고 코드 블록으로 감싸 오는 경우가 있어
     * 첫 '{'부터 마지막 '}'까지만 잘라내어 파싱합니다.
     */
    private ClaudeTravelPlanPayloadDto parsePayload(
        String text
    ) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start < 0 || end <= start) {
            throw new ClaudeTravelPlanException(
                "Claude 여행 계획 응답이 JSON 형식이 아닙니다."
            );
        }

        String json = text.substring(
            start,
            end + 1
        );

        try {
            return objectMapper.readValue(
                json,
                ClaudeTravelPlanPayloadDto.class
            );
        } catch (JsonProcessingException exception) {
            throw new ClaudeTravelPlanException(
                "Claude 여행 계획 응답 JSON을 해석할 수 없습니다.",
                exception
            );
        }
    }

    /**
     * 연결 계층 오류의 원인 체인을 확인하여 timeout과 일반 장애를 구분합니다.
     */
    private BusinessException convertTransportException(
        AnthropicException exception
    ) {
        ErrorCode errorCode =
            hasTimeoutCause(exception)
                ? ErrorCode.TRAVEL_PLAN_LLM_TIMEOUT
                : ErrorCode.TRAVEL_PLAN_LLM_UNAVAILABLE;

        return new BusinessException(
            errorCode,
            exception
        );
    }

    /**
     * 예외 원인 체인에 소켓 timeout이 포함되어 있는지 확인합니다.
     */
    private boolean hasTimeoutCause(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (
                current instanceof SocketTimeoutException
                    || current instanceof InterruptedIOException
            ) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
