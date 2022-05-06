/*
 * This is a dummy implementation of a NotificationService.
 * The notification service will be in charge of processing system events.
 * The service will store registered events and will proceed depends on the case:
 *  - sending an alert
 *  - sending an email to the customer
 */
package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

enum class EventStatus {
    BILLING_PROCESS_STARTED,
    BILLING_PROCESS_FINISHED,
    BILLING_ALREADY_RUNNING,
    MANUAL_INVOICE_UPDATE,
    CURRENCY_MISMATCH,
    CUSTOMER_NOT_FOUND,
    INVOICE_CHARGED,
    INSUFFICIENT_FUNDS,
    INVOICE_NOT_UPDATED,
    INVOICE_UPDATED
}

class NotificationService {

    fun notifyEvent(status: EventStatus, customer: Customer? = null, invoice: Invoice? = null) {
        registerEvent(status, customer, invoice)
        when(status) {
            EventStatus.BILLING_ALREADY_RUNNING -> {
                sendAlert(EventStatus.BILLING_ALREADY_RUNNING)
                //update metrics
            }
            EventStatus.CURRENCY_MISMATCH -> {
                sendAlert(EventStatus.CURRENCY_MISMATCH, customer, invoice)
                //update metrics
            }
            EventStatus.CUSTOMER_NOT_FOUND -> {
                sendAlert(EventStatus.CUSTOMER_NOT_FOUND, customer, invoice)
                //update metrics
            }
            EventStatus.INVOICE_CHARGED -> {
                sendNotificationToCustomer(EventStatus.INVOICE_CHARGED, customer, invoice)
                //update metrics
            }
            EventStatus.INSUFFICIENT_FUNDS -> {
                sendNotificationToCustomer(EventStatus.INSUFFICIENT_FUNDS, customer, invoice)
                //update metrics
            }
            EventStatus.INVOICE_NOT_UPDATED -> {
                sendAlert(EventStatus.INVOICE_UPDATED, customer, invoice)
                //update metrics
            }
            else -> {}
        }
    }

    private fun registerEvent(status: EventStatus, customer: Customer?, invoice: Invoice?) {
        logger.info {
            "Registering event $status " +
                    (if (customer != null) "- customer ${customer.id}" else " ") +
                    (if (invoice != null) "- invoice ${invoice.id}" else "")
        }
        //code to send event to the log event system
    }

    private fun sendAlert(status: EventStatus, customer: Customer? = null, invoice: Invoice? = null) {
        logger.warn {
            "Sending alert for event: $status " +
                    (if (customer != null) "- customer ${customer.id} " else " ") +
                    (if (invoice != null) "- invoice ${invoice.id}" else "")
        }
        //code to email system administrators to fix the issue
    }

    private fun sendNotificationToCustomer(status: EventStatus, customer: Customer?, invoice: Invoice? = null) {
        logger.debug { "Sending notification for event: $status: customer ${customer?.id} " +
                if (invoice != null) "- invoice ${invoice.id}" else ""
        }
        //code to email a customer to notify an event
    }
}
