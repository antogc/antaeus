package io.pleo.antaeus.core.services

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val CUSTOMER_ID_404 = 404
private const val CUSTOMER_ID_1 = 1
private const val CUSTOMER_ID_2 = 2
private const val CUSTOMER_ID_3 = 3
private const val CUSTOMER_ID_4 = 4
private const val CUSTOMERS_PER_PAGE = 2
private const val FIRST_ID = 0
private const val FIRST_PAGE_LAST_ID = CUSTOMER_ID_2
private const val SECOND_PAGE_LAST_ID = CUSTOMER_ID_4

class CustomerServiceTest {
    private var dal = mockk<AntaeusDal> ()
    private val customerService = CustomerService(dal = dal)

    @Test
    fun `will throw if customer is not found`() {
        expectFetchCustomer404()

        assertThrows<CustomerNotFoundException> {
            customerService.fetch(CUSTOMER_ID_404)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `will send first page to the input channel`()  = runTest {
        expectDalReturnsOnlyOnePage()
        val channel = Channel<Customer>()

        launch { customerService.initCustomerPagesChannel(channel) }
        val customers = consumeChannel(channel)

        customers shouldHaveSize CUSTOMERS_PER_PAGE
        customers.map { it.id } shouldContainAll listOf(CUSTOMER_ID_1, CUSTOMER_ID_2)
        verify { dal.fetchCustomersPage(FIRST_ID) }
        verify { dal.fetchCustomersPage(FIRST_PAGE_LAST_ID) }
        confirmVerified(dal)
    }

    @Test
    fun `will send teo pages to the input channel`()  = runTest {
        expectDalReturnsTwoPages()
        val channel = Channel<Customer>()

        launch { customerService.initCustomerPagesChannel(channel) }
        val customers = consumeChannel(channel)

        customers shouldHaveSize CUSTOMERS_PER_PAGE*2
        customers.map { it.id } shouldContainAll listOf(CUSTOMER_ID_1, CUSTOMER_ID_2, CUSTOMER_ID_3, CUSTOMER_ID_4)
        verify { dal.fetchCustomersPage(FIRST_ID) }
        verify { dal.fetchCustomersPage(FIRST_PAGE_LAST_ID) }
        verify { dal.fetchCustomersPage(SECOND_PAGE_LAST_ID) }
        confirmVerified(dal)
    }

    private suspend fun consumeChannel(channel: Channel<Customer>): MutableList<Customer> {
        val customers = mutableListOf<Customer>()
        for (customer in channel) {
            customers.add(customer)
        }
        return customers
    }

    private fun expectFetchCustomer404() {
        every { dal.fetchCustomer(CUSTOMER_ID_404) } returns null
    }

    private fun expectDalReturnsOnlyOnePage() {
        every { dal.fetchCustomersPage(FIRST_ID) } returns listOf(
            Customer(CUSTOMER_ID_1, Currency.EUR),
            Customer(CUSTOMER_ID_2, Currency.EUR)
        )
        every { dal.fetchCustomersPage(FIRST_PAGE_LAST_ID) } returns listOf()
    }

    private fun expectDalReturnsTwoPages() {
        every { dal.fetchCustomersPage(FIRST_ID) } returns listOf(
            Customer(CUSTOMER_ID_1, Currency.EUR),
            Customer(CUSTOMER_ID_2, Currency.EUR)
        )
        every { dal.fetchCustomersPage(FIRST_PAGE_LAST_ID) } returns listOf(
            Customer(CUSTOMER_ID_3, Currency.EUR),
            Customer(CUSTOMER_ID_4, Currency.EUR)
        )
        every { dal.fetchCustomersPage(SECOND_PAGE_LAST_ID) } returns listOf()
    }
}
