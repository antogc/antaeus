package io.pleo.antaeus.core.services

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.StopRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import io.pleo.antaeus.core.config.CUSTOMERS_CHANNEL_MAX_LIMIT
import io.pleo.antaeus.core.config.NETWORK_RETRY_BASE
import io.pleo.antaeus.core.config.NETWORK_RETRY_MAX
import io.pleo.antaeus.core.exceptions.*
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce
import mu.KotlinLogging
import java.util.concurrent.atomic.AtomicBoolean

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
    private val invoiceService: InvoiceService,
    private val notificationService: NotificationService
){

    private var isRunningLock: AtomicBoolean = AtomicBoolean(false)
    val isRunning: Boolean
        get() = isRunningLock.get()

    /**
     * Process the payment of a specific invoice without error checking
     */
    fun processSingleInvoice(customer: Customer, invoice: Invoice): Boolean {
        checkProcessIsNotRunning()
        notifyEvent(EventStatus.MANUAL_INVOICE_UPDATE, customer, invoice)
        return if (paymentProvider.charge(invoice)) {
            updateInvoiceStatus(customer, invoice)
        } else {
            notifyEvent(EventStatus.INSUFFICIENT_FUNDS, customer, invoice)
            false
        }
    }

    /**
     * Initiates the billing process that is going to process all pending invoices
     */
    suspend fun initBillingProcess()  {
        checkProcessIsNotRunning()
        isRunningLock.set(true)
        launchProcess()
        isRunningLock.set(false)
    }

    private fun checkProcessIsNotRunning() {
        if (isRunningLock.get()) {
            throw BillingProcessAlreadyRunning()
        }
    }

    private suspend fun launchProcess() = coroutineScope {
        notificationService.notifyEvent(EventStatus.BILLING_PROCESS_STARTED)
        val jobs = mutableListOf<Job>()
        createCustomersChannel().consumeEach { customer ->
            jobs.add(
                launch(Dispatchers.IO) {
                    processCustomerInvoices(customer)
                }
            )
        }
        jobs.joinAll()
        notificationService.notifyEvent(EventStatus.BILLING_PROCESS_FINISHED)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.createCustomersChannel(): ReceiveChannel<Customer> = produce(capacity = CUSTOMERS_CHANNEL_MAX_LIMIT) {
        val pageFetcher = customerService.getPageFetcher()
        while (pageFetcher.hasNext()) {
            val customers = pageFetcher.nextPage()
            customers.forEach {
                send(it)
            }
        }
    }

    private suspend fun processCustomerInvoices(customer: Customer){
        for (invoice in invoiceService.fetchPendingInvoicesByCustomerId(customer.id)) {
            try {
                processInvoice(customer, invoice)
            } catch (e: CurrencyMismatchException) {
                notifyError(e, customer, invoice)
                continue
            } catch (e: CustomerNotFoundException) {
                notifyError(e, customer, invoice)
                break
            }
        }
    }

    private suspend fun processInvoice(customer: Customer, invoice: Invoice) {
        if (processInvoiceWithRetry(invoice)) {
            updateInvoiceStatus(customer, invoice)
        } else {
            notifyEvent(EventStatus.INSUFFICIENT_FUNDS, customer, invoice)
        }
    }

    private suspend fun processInvoiceWithRetry(invoice: Invoice): Boolean {
        var processed: Boolean
        retry(retryPolicy  + binaryExponentialBackoff(base = NETWORK_RETRY_BASE, max = NETWORK_RETRY_MAX)) {
            processed = paymentProvider.charge(invoice)
        }
        return processed
    }

    private fun updateInvoiceStatus(customer: Customer, invoice: Invoice): Boolean {
        return try {
            invoiceService.updateInvoiceStatus(invoice, InvoiceStatus.PAID)
            notifyEvent(EventStatus.INVOICE_PROCESSED, customer, invoice)
            true
        } catch (e: InvoiceNotUpdatedException) {
            notifyError(e, customer, invoice)
            false
        }
    }

    private fun notifyEvent(status: EventStatus, customer: Customer, invoice: Invoice) {
        when (status) {
            EventStatus.INVOICE_PROCESSED -> {
                notificationService.notifyEvent(EventStatus.INVOICE_PROCESSED, customer, invoice)
                logger.debug { "Invoice processed for customer ${customer.id} and id ${invoice.id}" }
            }
            EventStatus.INSUFFICIENT_FUNDS -> {
                notificationService.notifyEvent(EventStatus.INSUFFICIENT_FUNDS, customer, invoice)
                logger.debug { "Insufficient funds for customer ${customer.id} and invoice ${invoice.id}" }
            }
            EventStatus.MANUAL_INVOICE_UPDATE -> {
                notificationService.notifyEvent(EventStatus.MANUAL_INVOICE_UPDATE, customer, invoice)
                logger.debug { "The invoice with id ${invoice.id} is going to be updated manually" }
            }
            else -> {}
        }
    }

    private fun notifyError(e: Throwable, customer: Customer, invoice: Invoice?) {
        when (e) {
            is CustomerNotFoundException -> {
                notificationService.notifyEvent(EventStatus.CUSTOMER_NOT_FOUND, customer)
                logger.error { "Customer not found ${customer.id}: $e" }
            }
            is CurrencyMismatchException -> {
                logger.error { "Error processing invoice ${invoice?.id}: $e" }
                notificationService.notifyEvent(EventStatus.CURRENCY_MISMATCH, customer, invoice)
            }
            is InvoiceNotUpdatedException -> {
                notificationService.notifyEvent(EventStatus.INVOICE_NOT_UPDATED, customer, invoice)
                logger.error { "Invoice with id ${invoice?.id} was not updated!" }
            }
        }
    }

}

