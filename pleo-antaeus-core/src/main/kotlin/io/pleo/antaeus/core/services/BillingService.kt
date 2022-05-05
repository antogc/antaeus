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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
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
private const val RETRY_DELAY = 1000L
private const val CHANNEL_MAX_LIMIT = 50

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val customerService: CustomerService,
    private val invoiceService: InvoiceService
) {

    /**
     * Stats the billing process
     */
    suspend fun initBillingProcess() = coroutineScope {
        val customersChannel = createCustomersChannel()
        for (customer in customersChannel) {
            launch(Dispatchers.IO) {
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

    private fun CoroutineScope.createCustomersChannel(): ReceiveChannel<Customer> = produce(capacity = CHANNEL_MAX_LIMIT) {
        val pageFetcher = customerService.getPageFetcher()
        while (pageFetcher.hasNext()) {
            val customers = pageFetcher.nextPage()
            customers.forEach {
                send(it)
            }
        }
        //close()
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
            //TODO retry
            invoiceService.updatePaidInvoice(invoice)
        } catch (e: InvoiceNotUpdatedException) {
            //TODO notificationService.notify(invoice.id, ERROR.PAID_)
            logger.error { "Invoice with id ${invoice.id} was not updated!" }
        }
    }

    private suspend fun processInvoiceWithRetry(invoice: Invoice): Boolean {
        var processed: Boolean
        retry(retryPolicy  + constantDelay(RETRY_DELAY)) {
            processed = paymentProvider.charge(invoice)
        }
        return processed
    }
}

