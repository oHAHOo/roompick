package com.roompick.domain.payment.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.roompick.domain.member.entity.MemberRole;
import com.roompick.domain.payment.dto.response.PaymentPrepareResponseDto;
import com.roompick.domain.payment.entity.PaymentStatus;
import com.roompick.domain.payment.facade.PaymentFacade;
import com.roompick.global.security.AuthMember;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentPrepareControllerResponseTest {

    private static final Long RESERVATION_ID =
        1L;

    private static final Long MEMBER_ID =
        10L;

    private static final Long PAYMENT_ID =
        100L;

    private static final String PORTONE_PAYMENT_ID =
        "roompick-payment-test-001";

    private static final String STORE_ID =
        "store-test-001";

    private static final String CHANNEL_KEY =
        "channel-key-test-001";

    private static final long PAYMENT_AMOUNT =
        200_000L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentFacade paymentFacade;

    @Test
    @DisplayName(
        "결제 준비 응답에 PortOne storeId와 channelKey가 포함된다"
    )
    void preparePaymentResponseContainsPortOneConfiguration()
        throws Exception {

        // given
        PaymentPrepareResponseDto result =
            new PaymentPrepareResponseDto(
                PAYMENT_ID,
                PORTONE_PAYMENT_ID,
                RESERVATION_ID,
                PAYMENT_AMOUNT,
                PaymentStatus.READY,
                STORE_ID,
                CHANNEL_KEY
            );

        given(
            paymentFacade.preparePayment(
                RESERVATION_ID,
                MEMBER_ID
            )
        ).willReturn(result);

        // when & then
        mockMvc.perform(
                post(
                    "/api/v1/reservations/{reservationId}/payments",
                    RESERVATION_ID
                )
                    .with(
                        authentication(
                            userAuthentication(
                                MEMBER_ID
                            )
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            )
            .andExpect(
                jsonPath("$.success")
                    .value(true)
            )
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "결제가 준비되었습니다."
                    )
            )
            .andExpect(
                jsonPath("$.data.paymentId")
                    .value(PAYMENT_ID)
            )
            .andExpect(
                jsonPath(
                    "$.data.portOnePaymentId"
                )
                    .value(PORTONE_PAYMENT_ID)
            )
            .andExpect(
                jsonPath("$.data.reservationId")
                    .value(RESERVATION_ID)
            )
            .andExpect(
                jsonPath("$.data.amount")
                    .value(PAYMENT_AMOUNT)
            )
            .andExpect(
                jsonPath("$.data.status")
                    .value("READY")
            )
            .andExpect(
                jsonPath("$.data.storeId")
                    .value(STORE_ID)
            )
            .andExpect(
                jsonPath("$.data.channelKey")
                    .value(CHANNEL_KEY)
            );

        then(paymentFacade)
            .should()
            .preparePayment(
                RESERVATION_ID,
                MEMBER_ID
            );
    }

    private Authentication userAuthentication(
        Long memberId
    ) {
        AuthMember authMember =
            new AuthMember(
                memberId,
                MemberRole.USER
            );

        return new UsernamePasswordAuthenticationToken(
            authMember,
            null,
            List.of(
                new SimpleGrantedAuthority(
                    "ROLE_USER"
                )
            )
        );
    }
}
