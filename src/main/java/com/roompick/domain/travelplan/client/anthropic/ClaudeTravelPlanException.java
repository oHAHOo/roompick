package com.roompick.domain.travelplan.client.anthropic;

/**
 * Claude 응답 구조가 기대한 JSON 형식과 다를 때
 * Client 내부에서 사용하는 예외입니다.
 *
 * ClaudeTravelPlanClient에서 최종적으로
 * TRAVEL_PLAN_LLM_INVALID_RESPONSE BusinessException으로 변환합니다.
 */
public class ClaudeTravelPlanException extends RuntimeException {

    public ClaudeTravelPlanException(
        String message
    ) {
        super(message);
    }

    public ClaudeTravelPlanException(
        String message,
        Throwable cause
    ) {
        super(
            message,
            cause
        );
    }
}
