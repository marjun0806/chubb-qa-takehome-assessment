package com.example.demo.adapter.out.messaging;

import com.example.demo.domain.claim.ClaimStatus;
import com.example.demo.domain.claim.events.ClaimStatusChanged;
import com.example.demo.domain.claim.events.ClaimSubmitted;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaDomainEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private KafkaMessageSchemaValidator schemaValidator;

    private KafkaDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaDomainEventPublisher(kafkaTemplate, objectMapper, schemaValidator);
    }

    // TC-077: Event published to Kafka with correct topic and message key
    @Test
    void should_PublishEventToKafka_When_ClaimSubmittedEventProvided() {
        UUID claimId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        var event = new ClaimSubmitted(
                eventId,
                claimId,
                userId,
                LocalDate.now(),
                BigDecimal.TEN,
                Instant.now()
        );
        doNothing().when(schemaValidator).validate(anyString(), anyString());
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("claim-events"), eq(claimId.toString()), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        verify(schemaValidator).validate(payload, "ClaimSubmittedMessage");
        assertThat(payload).contains("\"eventType\":\"claim-submitted\"");
        assertThat(payload).contains("\"eventId\":\"" + eventId + "\"");
        assertThat(payload).contains("\"claimId\":\"" + claimId + "\"");
        assertThat(payload).contains("\"userId\":\"" + userId + "\"");
    }

    // TC-077b: ClaimStatusChanged published with correct key and envelope
    @Test
    void should_PublishEventToKafka_When_ClaimStatusChangedEventProvided() {
        UUID claimId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        var event = new ClaimStatusChanged(
                eventId,
                claimId,
                ClaimStatus.SUBMITTED,
                ClaimStatus.UNDER_REVIEW,
                adminId,
                Instant.now()
        );
        doNothing().when(schemaValidator).validate(anyString(), anyString());
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("claim-events"), eq(claimId.toString()), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        verify(schemaValidator).validate(payload, "ClaimStatusChangedMessage");
        assertThat(payload).contains("\"eventType\":\"claim-status-changed\"");
        assertThat(payload).contains("\"oldStatus\":\"SUBMITTED\"");
        assertThat(payload).contains("\"newStatus\":\"UNDER_REVIEW\"");
        assertThat(payload).contains("\"userId\":\"" + adminId + "\"");
    }

    // TC-078: Event payload contains expected fields when serialized
    @Test
    void should_SerializeEventFields_When_EventPublished() {
        UUID claimId = UUID.randomUUID();
        var event = new ClaimSubmitted(
                UUID.randomUUID().toString(),
                claimId,
                UUID.randomUUID(),
                LocalDate.now(),
                BigDecimal.TEN,
                Instant.now()
        );

        doNothing().when(schemaValidator).validate(anyString(), anyString());
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("claim-events"), eq(claimId.toString()), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        assertThat(payload).contains("\"eventId\"");
        assertThat(payload).contains("\"claimId\"");
        assertThat(payload).contains("\"payload\"");
        assertThat(payload).contains(claimId.toString());
    }

    // TC-079: Failed serialization throws IllegalStateException
    @Test
    void should_ThrowIllegalStateException_When_SerializationFails() throws JsonProcessingException {
        var event = new ClaimSubmitted(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                BigDecimal.TEN,
                Instant.now()
        );
        doThrow(new JsonProcessingException("test") {}).when(objectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize domain event");
    }

    // TC-NEW-001: Publisher calls validator with serialized envelope and schema name for ClaimSubmitted
    @Test
    void should_CallValidatorWithCorrectArgs_When_ClaimSubmittedEventPublished() {
        UUID claimId = UUID.randomUUID();
        var event = new ClaimSubmitted(
                UUID.randomUUID().toString(),
                claimId,
                UUID.randomUUID(),
                LocalDate.now(),
                BigDecimal.TEN,
                Instant.now()
        );

        doNothing().when(schemaValidator).validate(anyString(), eq("ClaimSubmittedMessage"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        ArgumentCaptor<String> validatedPayload = ArgumentCaptor.forClass(String.class);
        verify(schemaValidator).validate(validatedPayload.capture(), eq("ClaimSubmittedMessage"));
        verify(kafkaTemplate).send(eq("claim-events"), eq(claimId.toString()), eq(validatedPayload.getValue()));
    }

    // TC-NEW-002: Publisher propagates KafkaMessageValidationException when validator rejects missing required field
    @Test
    void should_PropagateValidationException_When_ValidatorRejectsMissingRequiredField() {
        UUID claimId = UUID.randomUUID();
        var event = new ClaimSubmitted(
                UUID.randomUUID().toString(),
                claimId,
                UUID.randomUUID(),
                LocalDate.now(),
                BigDecimal.TEN,
                Instant.now()
        );
        doThrow(new KafkaMessageValidationException("claimId: is missing but it is required"))
                .when(schemaValidator).validate(anyString(), eq("ClaimSubmittedMessage"));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(KafkaMessageValidationException.class)
                .hasMessageContaining("claimId");
    }

    // TC-NEW-003: Publisher propagates KafkaMessageValidationException when validator rejects invalid enum value
    @Test
    void should_PropagateValidationException_When_ValidatorRejectsInvalidClaimStatus() {
        UUID claimId = UUID.randomUUID();
        var event = new ClaimStatusChanged(
                UUID.randomUUID().toString(),
                claimId,
                ClaimStatus.SUBMITTED,
                ClaimStatus.UNDER_REVIEW,
                UUID.randomUUID(),
                Instant.now()
        );
        doThrow(new KafkaMessageValidationException("oldStatus: does not have a value in the enumeration"))
                .when(schemaValidator).validate(anyString(), eq("ClaimStatusChangedMessage"));

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(KafkaMessageValidationException.class)
                .hasMessageContaining("oldStatus");
    }
}