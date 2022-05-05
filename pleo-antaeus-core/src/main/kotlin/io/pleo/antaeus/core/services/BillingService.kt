package io.pleo.antaeus.core.services

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.StopRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
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
private const val RETRY_BASE = 10L
private const val RETRY_MAX = 10L
private const val CHANNEL_MAX_LIMIT = 50

class BillingService(
    private val paymentProvider: PaymentProvider,
    private val customerService: CustomerService,
    private val invoiceService: InvoiceService,
    private val notificationService: NotificationService
) {

    private var isRunningLock: AtomicBoolean = AtomicBoolean(false)
    val isRunning: Boolean
        get() = isRunningLock.get()

    suspend fun processCustomerInvoice(customer: Customer, invoice: Invoice) {
        //TODO event?
        if (isRunningLock.get()) {
            throw BillingProcessAlreadyRunning()
        }
        processInvoice(customer, invoice)
    }

    /**
     * Starts the billing process
     */
    suspend fun initBillingProcess()  {
        if (isRunningLock.get()) {
            throw BillingProcessAlreadyRunning()
        }
        isRunningLock.set(true)
        notificationService.notifyEvent(EventStatus.BILLING_PROCESS_STARTED)
        launchProcess()
        isRunningLock.set(false)
        notificationService.notifyEvent(EventStatus.BILLING_PROCESS_FINISHED)
    }

    private suspend fun launchProcess() = coroutineScope {
        val jobs = mutableListOf<Job>()
        createCustomersChannel().consumeEach { customer ->
            jobs.add(
                launch(Dispatchers.IO) {
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
            )
        }
        jobs.joinAll()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun CoroutineScope.createCustomersChannel(): ReceiveChannel<Customer> = produce(capacity = CHANNEL_MAX_LIMIT) {
        val pageFetcher = customerService.getPageFetcher()
        while (pageFetcher.hasNext()) {
            val customers = pageFetcher.nextPage()
            customers.forEach {
                send(it)
            }
        }
    }

    private suspend fun processInvoice(customer: Customer, invoice: Invoice) {
        if (processInvoiceWithRetry(invoice)) {
            notifyEvent(EventStatus.INVOICE_CHARGED, customer, invoice)
            updateInvoice(customer, invoice)
        } else {
            notifyEvent(EventStatus.INSUFFICIENT_FUNDS, customer, invoice)
        }
    }

    private suspend fun processInvoiceWithRetry(invoice: Invoice): Boolean {
        var processed: Boolean
        retry(retryPolicy  + binaryExponentialBackoff(base = RETRY_BASE, max = RETRY_MAX)) {
            processed = paymentProvider.charge(invoice)
        }
        return processed
    }

    private fun updateInvoice(customer: Customer, invoice: Invoice) {
        try {
            invoiceService.updateInvoiceStatus(invoice, InvoiceStatus.PAID)
            notificationService.notifyEvent(EventStatus.INVOICE_UPDATED, customer, invoice)
        } catch (e: InvoiceNotUpdatedException) {
            notifyError(e, customer, invoice)
        }
    }

    private fun notifyEvent(status: EventStatus, customer: Customer, invoice: Invoice) {
        when (status) {
            EventStatus.INVOICE_CHARGED -> {
                notificationService.notifyEvent(EventStatus.INVOICE_CHARGED, customer, invoice)
                logger.debug { "Invoice charged for customer ${customer.id} and id ${invoice.id}" }
            }
            EventStatus.INVOICE_UPDATED -> {
                notificationService.notifyEvent(EventStatus.INVOICE_UPDATED, customer, invoice)
                logger.debug { "Invoice updated customer ${customer.id} and id ${invoice.id} " }
            }
            EventStatus.INSUFFICIENT_FUNDS -> {
                notificationService.notifyEvent(EventStatus.INSUFFICIENT_FUNDS, customer, invoice)
                logger.debug { "Insufficient funds for customer ${customer.id} and invoice ${invoice.id}" }
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

