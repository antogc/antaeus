package io.pleo.antaeus.core.services

import io.mockk.*
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.models.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

private const val AMOUNT = 1.0
private const val CUSTOMER_ID1 = 1
private const val CUSTOMER_ID2 = 2

internal class BillingServiceTest {

    private var paymentProvider = mockk<PaymentProvider>()
    private var customerService = mockk<CustomerService>()
    private var invoiceService = mockk<InvoiceService>()

    private lateinit var billingService: BillingService

    @BeforeEach
    fun setup() {
        billingService = BillingService(paymentProvider, customerService, invoiceService)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will process payments`() = runTest {
        expectCustomers()
        expectInvoicesForCustomers()
        expectInvoiceStatusUpdated()
        expectPaymentProviderChargesInvoices()

        billingService.init()

        coVerify(exactly = 1) { customerService.initCustomerPagesChannel(any()) }
        verify(exactly = 2) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = 4) { paymentProvider.charge(any()) }
        verify(exactly = 4) { invoiceService.updatePaidInvoice(any()) }
        confirmVerified(customerService, invoiceService, customerService)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will retry payments when NetworkException is raised`() = runTest {
        expectCustomers()
        expectInvoicesForCustomers()
        expectInvoiceStatusUpdated()
        expectPaymentProviderReturnsExceptionTemporally()

        billingService.init()

        coVerify(exactly = 1) { customerService.initCustomerPagesChannel(any()) }
        verify(exactly = 2) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = 5) { paymentProvider.charge(any()) } //4 + 1 retry
        verify(exactly = 4) { invoiceService.updatePaidInvoice(any()) }
        confirmVerified(customerService, invoiceService, customerService)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will skip user customer when CustomerNotFoundException raised`() = runTest {
        expectCustomers()
        expectInvoicesForCustomers()
        expectInvoiceStatusUpdated()
        expectPaymentProviderReturnsCustomerNotFoundException()

        billingService.init()

        coVerify(exactly = 1) { customerService.initCustomerPagesChannel(any()) }
        verify(exactly = 2) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = 3) { paymentProvider.charge(any()) }
        verify(exactly = 2) { invoiceService.updatePaidInvoice(any()) }
        confirmVerified(customerService, invoiceService, customerService)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will skip user invoice when CurrencyMismatchException raised`() = runTest {
        expectCustomers()
        expectInvoicesForCustomers()
        expectInvoiceStatusUpdated()
        expectPaymentProviderReturnsCurrencyMismatchException()

        billingService.init()

        coVerify(exactly = 1) { customerService.initCustomerPagesChannel(any()) }
        verify(exactly = 2) { invoiceService.fetchPendingInvoicesByCustomerId(any()) }
        verify(exactly = 4) { paymentProvider.charge(any()) }
        verify(exactly = 3) { invoiceService.updatePaidInvoice(any()) }
        confirmVerified(customerService, invoiceService, customerService)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.expectCustomers() {
        coEvery { customerService.initCustomerPagesChannel(any()) } answers {
            val channel = firstArg<Channel<Customer>>()
            launch {
                channel.send(Customer(CUSTOMER_ID1, Currency.EUR))
                channel.send(Customer(CUSTOMER_ID2, Currency.EUR))
                channel.close()
            }
        }
    }

    private fun expectInvoicesForCustomers() {
        every { invoiceService.fetchPendingInvoicesByCustomerId(CUSTOMER_ID1) } returns
                listOf(
                    Invoice(1, CUSTOMER_ID1, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING),
                    Invoice(2, CUSTOMER_ID1, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING)
                )

        every { invoiceService.fetchPendingInvoicesByCustomerId(CUSTOMER_ID2) } returns
                listOf(
                    Invoice(3, CUSTOMER_ID2, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING),
                    Invoice(4, CUSTOMER_ID2, Money(BigDecimal.valueOf(AMOUNT), Currency.EUR), InvoiceStatus.PENDING)
                )
    }

    private fun expectInvoiceStatusUpdated() {
        every { invoiceService.updatePaidInvoice(any()) } just Runs
    }

    private fun expectPaymentProviderChargesInvoices() {
        every { paymentProvider.charge(any()) } returns true
    }

    private fun expectPaymentProviderReturnsExceptionTemporally() {
        every { paymentProvider.charge(any()) } throws NetworkException() andThen true
    }

    private fun expectPaymentProviderReturnsCustomerNotFoundException() {
        every { paymentProvider.charge(any()) } throws CustomerNotFoundException(CUSTOMER_ID1) andThen true
    }

    private fun expectPaymentProviderReturnsCurrencyMismatchException() {
        every { paymentProvider.charge(any()) } throws CurrencyMismatchException(1, 1) andThen true
    }
}

