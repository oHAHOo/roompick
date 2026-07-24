# 결제 정책

- 문서 버전: `v0.1`
- 최종 수정일: 2026-07-24
- 담당자: minjae123123
- 근거 코드: `com.roompick.domain.payment` (현재 `package-info.java`만 존재, Service 미구현)
- 근거 문서: `docs/TABLE_SPEC.md` 7절 `PAYMENTS`, `docs/MVP_CONTEXT.md` 5절·8절·9절

이 문서가 다루는 결제 도메인은 아직 Service 계층이 구현되지 않았다. 아래 규칙은 DB 설계와
기획 문서에 있는 "구현해야 할 정책"이며, 실제 코드에 반영되면 이 문서도 함께 갱신한다.

---

## 1. 접근 권한 (예정)

| 동작 | 필요 권한 |
| --- | --- |
| 결제 준비·성공·실패 처리 | 인증된 회원, 본인이 생성한 예약에 대한 결제만 |

리소스 소유자 검증 방식은 [`RESERVATION_POLICY.md`](RESERVATION_POLICY.md) 5절의 예약 소유자
검증과 동일한 패턴을 따를 예정이다.

## 2. 결제 상태

| 상태 | 설명 |
| --- | --- |
| `READY` | 결제 요청 전 준비 상태 |
| `PAID` | 결제 승인 완료 상태 |
| `FAILED` | 결제 실패 상태 |
| `REFUNDED` | 전액 환불 상태(`docs/MVP_CONTEXT.md` 9절) |

## 3. 정합성 규칙 (`docs/TABLE_SPEC.md` 기준)

- 결제 금액(`payments.amount`)은 대상 예약의 `reservations.total_amount`와 일치해야 한다.
- 하나의 예약에는 최종적으로 하나의 `PAID` 결제만 허용한다.
- 결제 실패 기록과 환불 이력은 삭제하지 않는다(예약·결제 이력 보호를 위해 `ON DELETE CASCADE`를
  사용하지 않는다).
- 결제 금액은 음수일 수 없다(`chk_payments_amount` DB 제약).

## 4. 예약과의 상태 전이

```text
PENDING_PAYMENT
├─ 결제 성공 → CONFIRMED
└─ 결제 실패 → CANCELED + 점유한 객실 복구

CONFIRMED
└─ 예약 취소 → CANCELED + 결제 취소 + 객실 복구
```

- 결제 성공 시 예약 상태를 `CONFIRMED`로 변경하는 처리와 결제 실패 시 예약을 취소하고 점유한
  객실을 복구하는 처리는 결제 도메인과 예약 도메인이 함께 조율해야 하는 흐름이다
  (`Controller → Facade → Service` 계층 중 Facade에서 조율, `docs/MVP_CONTEXT.md` 10절 참고).

## 5. MVP 범위와 미확정 항목

- MVP에서는 실제 PG 연동 대신 테스트 가능한 간단한 결제 구현(`provider = FAKE`)을 사용한다.
  실제 PG 도입 여부와 PG사는 미확정이다.
- 실제 PG로 교체할 가능성을 고려해 결제 연동부는 인터페이스로 분리하는 방식을 권장한다.
- 부분 취소·부분 환불, 복잡한 취소 수수료 정책은 MVP에서 제외한다.
- 결제 대기 만료 시각(`expires_at`)을 MVP부터 실제로 사용할지, 예약·결제의 1:N 관계를 어떻게
  유지할지는 아직 팀 회의가 필요하다(`docs/TABLE_SPEC.md` 확인 필요 항목 참고).
