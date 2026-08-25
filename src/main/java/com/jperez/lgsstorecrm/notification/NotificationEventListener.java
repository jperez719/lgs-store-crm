package com.jperez.lgsstorecrm.notification;

import com.jperez.lgsstorecrm.transaction.event.CreditTransactionAppliedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreditTransactionApplied(CreditTransactionAppliedEvent event) {
        // Placeholder — stands in for a real notification service (email/SMS)
        log.info(
                "[NOTIFICATION] Tenant {} - Customer {}: {} of {} applied. New balance: {}. (transactionId={})",
                event.tenantId(),
                event.customerId(),
                event.type(),
                event.amount(),
                event.resultingBalance(),
                event.transactionId()
        );
    }
}
