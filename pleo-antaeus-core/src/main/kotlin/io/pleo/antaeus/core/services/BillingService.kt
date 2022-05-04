package io.pleo.antaeus.core.services

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.StopRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.constantDelay
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotUpdatedException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}
val retryPolicy: RetryPolicy<Throwable> = {
    if (reason is NetworkException) {
        logger.error { "Network exception, retrying" }
        ContinueRetrying
    } else {
        StopRetrying
    }
}

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val customerService: CustomerService,
    private val invoiceService: InvoiceService
) {

    /**
     * Stats the billing process
     */
    suspend fun init() = coroutineScope {
        val customersChannel = Channel<Customer>()
        launch { customerService.initCustomerPagesChannel(customersChannel) }
        for (customer in customersChannel) {
            launch {
                for (invoice in invoiceService.fetchPendingInvoicesByCustomerId(customer.id)) {
                    try {
                        processInvoice(customer, invoice)
                    } catch (e: CurrencyMismatchException) {
                        logger.error { "Error processing invoice ${invoice.id}: $e" }
                        continue
                    } catch (e: CustomerNotFoundException) {
                        logger.error { "Error processing invoice ${invoice.id}: $e" }
                        break
                    }
                }
            }
        }
    }

    private suspend fun processInvoice(customer: Customer, invoice: Invoice) {
        val processed = processInvoiceWithRetry(invoice)
        if (processed) {
            updateInvoice(invoice)
            logger.info { "Customer ${customer.id}, invoice ${invoice.id} processed" }
        } else {
            logger.info { "Customer ${customer.id}, invoice ${invoice.id} NOT charged!" }
        }
    }

    private fun updateInvoice(invoice: Invoice) {
        try {
            invoiceService.updatePaidInvoice(invoice)
        } catch (e: InvoiceNotUpdatedException) {
            logger.error { "Invoice with id ${invoice.id} was not updated!" }
        }
    }

    private suspend fun processInvoiceWithRetry(invoice: Invoice): Boolean {
        var processed: Boolean
        retry(retryPolicy  + constantDelay(10)) {
            processed = paymentProvider.charge(invoice)
        }
        return processed
    }
}

