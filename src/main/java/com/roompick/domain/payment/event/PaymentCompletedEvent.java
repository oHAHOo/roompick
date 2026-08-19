package com.roompick.domain.payment.event;

import java.time.LocalDateTime;

public record PaymentCompletedEvent(
    Long paymentId,
    LocalDateTime completedAt
) {
}
