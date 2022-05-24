package io.pleo.antaeus.core.services

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.*
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotUpdatedException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private const val AMOUNT = 1.0
private const val CUSTOMER_ID1 = 1
private const val CUSTOMER_ID2 = 2
private const val CUSTOMER_ID3 = 3
private const val CUSTOMER_ID4 = 4

internal class BillingServiceTest {

    private var paymentProvider = mockk<PaymentProvider>()
    private var customerService = mockk<CustomerService>()
    private var invoiceService = mockk<InvoiceService>()
    private var notificationService = mockk<NotificationService>(relaxed = true)
    private var customerPageFetcher = mockk<CustomersPageFetcher>()

    private lateinit var billingService: BillingService

    @BeforeEach
    fun setup() {
        billingService = BillingService(paymentProvider, customerService, invoiceService, notificationService)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will process payments when there is one customer page`() = runTest {
        expectOnePageOfCustomers()
        expectInvoicesForCustomers(CUSTOMER_ID1..CUSTOMER_ID2)
        expectInvoiceStatusUpdated()
        expectPaymentProviderChargesInvoices()
        val expectedHashNextCalls = 3 //fetcher needs an extra call to verify no more pages
        val expectedNextPageCalls = 2 //first one with a page, second one empty
        val expectedFetchInvoiceByCustomerCalls = 2 //2 customers
        val expectedProcessedInvoices = 4 //each customer with 2 invoices

        billingService.initBillingProcess()

        verify(exactly = 1) { customerService.getPageFetcher() }
        verify(exactly = expectedHashNextCalls) { customerPageFetcher.hasNext() }
        verify(exactly = expectedNextPageCalls) { customerPageFetcher.nextPage() }
        verify(exactly = expectedFetchInvoiceByCustomerCalls) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = expectedProcessedInvoices) { paymentProvider.charge(any()) }
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) }
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) }
        verify(exactly = expectedProcessedInvoices) { notificationService.notifyEvent(EventStatus.INVOICE_PROCESSED, any(), any()) }
        confirmVerified(customerService, invoiceService, customerService, customerPageFetcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will process payments when there are two customer pages`() = runTest {
        expectTwoPagesOfCustomers()
        expectInvoicesForCustomers(CUSTOMER_ID1..CUSTOMER_ID4)
        expectInvoiceStatusUpdated()
        expectPaymentProviderChargesInvoices()
        val expectedHashNextCalls = 4 //fetcher needs an extra call to verify no more pages
        val expectedNextPageCalls = 3 //2 pages with customers and one empty page
        val expectedFetchInvoiceByCustomerCalls = 4 //4 customers
        val expectedProcessedInvoices = 8 //each customer with 2 invoices

        billingService.initBillingProcess()

        verify(exactly = 1) { customerService.getPageFetcher() }
        verify(exactly = expectedHashNextCalls) { customerPageFetcher.hasNext() }
        verify(exactly = expectedNextPageCalls) { customerPageFetcher.nextPage() }
        verify(exactly = expectedFetchInvoiceByCustomerCalls) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = expectedProcessedInvoices) { paymentProvider.charge(any()) }
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) }
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) }
        confirmVerified(customerService, invoiceService, customerService, customerPageFetcher)
    }

   @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will retry payments when NetworkException is raised`() = runTest {
        expectOnePageOfCustomersAndInvoicesScenario()
        expectPaymentProviderThrowsExceptionTemporally()
        val expectedFetchInvoiceByCustomerCalls = 2 //2 customers
        val expectedInvoiceChargedCalls = 5 //2 customer with 2 invoices + 1 retry
        val expectedProcessedInvoices = 4 //2 customer with 2 invoices

        billingService.initBillingProcess()

        verify(exactly = expectedFetchInvoiceByCustomerCalls) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = expectedInvoiceChargedCalls) { paymentProvider.charge(any()) } //4 + 1 retry
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) }
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) }
        verify(exactly = expectedProcessedInvoices) { notificationService.notifyEvent(EventStatus.INVOICE_PROCESSED, any(), any()) }
        confirmVerified(invoiceService, paymentProvider)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will skip user customer when CustomerNotFoundException raised`() = runTest {
        expectOnePageOfCustomersAndInvoicesScenario()
        expectPaymentProviderThrowsCustomerNotFoundException()
        val expectedFetchInvoiceByCustomerCalls = 2 //2 customers
        val expectedInvoiceChargedCalls = 3 //2 customer but first one not found: 1 failed call for the first + 2 correct for the second
        val expectedProcessedInvoices = 2 //2 customer with 2 invoices, but first customer not found

        billingService.initBillingProcess()

        verify(exactly = expectedFetchInvoiceByCustomerCalls) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = expectedInvoiceChargedCalls) { paymentProvider.charge(any()) }
        verify(exactly = expectedProcessedInvoices + 1) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) }
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) }
        verify(exactly = expectedProcessedInvoices) { notificationService.notifyEvent(EventStatus.INVOICE_PROCESSED, any(), any()) }
        verify(exactly = 1) { notificationService.notifyEvent(EventStatus.CUSTOMER_NOT_FOUND, any(), any()) }
        confirmVerified(invoiceService, paymentProvider)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will skip user invoice when CurrencyMismatchException raised`() = runTest {
        expectOnePageOfCustomersAndInvoicesScenario()
        expectPaymentProviderThrowsCurrencyMismatchException()
        val expectedFetchInvoiceByCustomerCalls = 2 //2 customers
        val expectedInvoiceChargedCalls = 4 //2 customer 2 invoices each one
        val expectedProcessedInvoices = 3 //2 customer with 2 invoices, but first invoice was not updated

        billingService.initBillingProcess()

        verify(exactly = expectedFetchInvoiceByCustomerCalls) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = expectedInvoiceChargedCalls) { paymentProvider.charge(any()) }
        verify(exactly = expectedProcessedInvoices + 1) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) }
        verify(exactly = expectedProcessedInvoices) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) }
        verify(exactly = expectedProcessedInvoices) { notificationService.notifyEvent(EventStatus.INVOICE_PROCESSED, any(), any()) }
        verify(exactly = 1) { notificationService.notifyEvent(EventStatus.CURRENCY_MISMATCH, any(), any()) }
        confirmVerified(invoiceService, paymentProvider)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will send a notification when invoice not update before charged and leave invoice as in-progress`() = runTest {
        expectOnePageOfCustomersAndInvoicesScenario()
        expectPaymentProviderChargesInvoices()
        expectInvoiceStatusNotUpdated()
        val expectedFetchInvoiceByCustomerCalls = 2 //2 customers
        val expectedInvoiceChargedCalls = 4 //2 customer 2 invoices each one
        val expectedInvoiceUpdateCalls = 4 //2 customer 2 invoices each one
        val expectedProcessedInvoices = 3 //2 customer with 2 invoices

        billingService.initBillingProcess()

        verify(exactly = expectedFetchInvoiceByCustomerCalls) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = expectedInvoiceChargedCalls) { paymentProvider.charge(any()) }
        verify(exactly = expectedProcessedInvoices + 1) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) }
        verify(exactly = expectedInvoiceUpdateCalls) { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) }
        verify(exactly = expectedProcessedInvoices) { notificationService.notifyEvent(EventStatus.INVOICE_PROCESSED, any(), any()) }
        verify(exactly = 1) { notificationService.notifyEvent(EventStatus.INVOICE_NOT_UPDATED, any(), any()) }
        confirmVerified(invoiceService, paymentProvider)
    }

    @Test
    fun `will process an invoice manually`()  {
        val customer = Customer(CUSTOMER_ID1, Currency.EUR)
        val invoice = Invoice(1, CUSTOMER_ID1, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING)
        expectPaymentProviderChargesInvoices()
        expectInvoiceStatusUpdated()

        billingService.processSingleInvoice(customer, invoice).shouldBeTrue()
    }

    @Test
    fun `will process an invoice manually when customer has not funds then return false`()  {
        val customer = Customer(CUSTOMER_ID1, Currency.EUR)
        val invoice = Invoice(1, CUSTOMER_ID1, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING)
        every { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) } just Runs
        expectPaymentProviderReturnsNoFunds()

        billingService.processSingleInvoice(customer, invoice).shouldBeFalse()

        verify { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) }
        confirmVerified(invoiceService)
    }

    private fun expectOnePageOfCustomers() {
        every { customerPageFetcher.hasNext() } returns true andThen true andThen false
        val customerPage = listOf(Customer(CUSTOMER_ID1, Currency.EUR), Customer(CUSTOMER_ID2, Currency.EUR))
        every { customerPageFetcher.nextPage() } returns customerPage andThen listOf()
        every { customerService.getPageFetcher() } returns customerPageFetcher
    }

    private fun expectTwoPagesOfCustomers() {
        every { customerPageFetcher.hasNext() } returns true andThen true andThen true  andThen false
        val customerPage1 = listOf(Customer(CUSTOMER_ID1, Currency.EUR), Customer(CUSTOMER_ID2, Currency.EUR))
        val customerPage2 = listOf(Customer(CUSTOMER_ID3, Currency.EUR), Customer(CUSTOMER_ID4, Currency.EUR))
        every { customerPageFetcher.nextPage() } returns customerPage1 andThen customerPage2 andThen listOf()
        every { customerService.getPageFetcher() } returns customerPageFetcher
    }

    private fun expectInvoicesForCustomers(range: IntRange) {
        var invoiceId = 1
        range.forEach {
            every { invoiceService.fetchPendingInvoicesByCustomerId(it) } returns
                    listOf(
                        Invoice(invoiceId++, it, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING),
                        Invoice(invoiceId++, it, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING)
                    )
        }
    }

    private fun expectInvoiceStatusUpdated() {
        every { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.IN_PROGRESS) } just Runs
        every { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) } just Runs
    }

    private fun expectInvoiceStatusNotUpdated() {
        every { invoiceService.updateInvoiceStatus(any(), InvoiceStatus.PAID) } throws
                InvoiceNotUpdatedException(mockk() { every { id } returns  1 }) andThen Unit
    }

    private fun expectOnePageOfCustomersAndInvoicesScenario() {
        expectOnePageOfCustomers()
        expectInvoicesForCustomers(CUSTOMER_ID1..CUSTOMER_ID2)
        expectInvoiceStatusUpdated()
    }

    private fun expectPaymentProviderChargesInvoices() {
        every { paymentProvider.charge(any()) } returns true
    }

    private fun expectPaymentProviderReturnsNoFunds() {
        every { paymentProvider.charge(any()) } returns false
    }

    private fun expectPaymentProviderThrowsExceptionTemporally() {
        every { paymentProvider.charge(any()) } throws NetworkException() andThen true
    }

    private fun expectPaymentProviderThrowsCustomerNotFoundException() {
        every { paymentProvider.charge(any()) } throws CustomerNotFoundException(CUSTOMER_ID1) andThen true
    }

    private fun expectPaymentProviderThrowsCurrencyMismatchException() {
        every { paymentProvider.charge(any()) } throws CurrencyMismatchException(1, 1) andThen true
    }
}

