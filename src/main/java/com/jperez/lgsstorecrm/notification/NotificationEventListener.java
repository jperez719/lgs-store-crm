package com.jperez.lgsstorecrm.notification;

import com.jperez.lgsstorecrm.common.config.RabbitMQConfig;
import com.jperez.lgsstorecrm.transaction.event.CreditTransactionAppliedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final RabbitTemplate rabbitTemplate;

    public NotificationEventListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreditTransactionApplied(CreditTransactionAppliedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", event.tenantId().toString());
        payload.put("customerId", event.customerId().toString());
        payload.put("employeeId", event.employeeId().toString());
        payload.put("transactionId", event.transactionId().toString());
        payload.put("type", event.type().toString());
        payload.put("amount", event.amount());
        payload.put("resultingBalance", event.resultingBalance());
        payload.put("occurredAt", event.occurredAt().toString());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_CREDIT_TRANSACTION_APPLIED,
                payload
        );

        log.info("Published CreditTransactionAppliedEvent to RabbitMQ for customer {}", event.customerId());
    }
}
