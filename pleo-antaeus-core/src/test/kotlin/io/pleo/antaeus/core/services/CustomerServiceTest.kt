package io.pleo.antaeus.core.services

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val CUSTOMER_ID_404 = 404
private const val BASE_ID = 0
private const val CUSTOMER_ID_1 = 1
private const val CUSTOMER_ID_2 = 2

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

    private fun expectFetchCustomer404() {
        every { dal.fetchCustomer(CUSTOMER_ID_404) } returns null
    }
}

class CustomersPageFetcherTest {

    private var dal = mockk<AntaeusDal> ()

    @Test
    fun `fetcher will return empty page`() {
        expectEmptyPage()

        val fetcher = CustomersPageFetcher(dal)

        fetcher.hasNext() shouldBe true
        fetcher.nextPage() shouldHaveSize 0
        fetcher.hasNext() shouldBe false

        verify { dal.fetchCustomersPage(BASE_ID) }
        confirmVerified(dal)
    }

    @Test
    fun `fetcher will return empty page and reset the marker`() {
        expectEmptyPage()

        val fetcher = CustomersPageFetcher(dal)

        fetcher.hasNext() shouldBe true
        fetcher.nextPage() shouldHaveSize 0
        fetcher.hasNext() shouldBe false

        fetcher.resetMarker()

        fetcher.hasNext() shouldBe true
        fetcher.nextPage() shouldHaveSize 0
        fetcher.hasNext() shouldBe false

        verify (exactly = 2) { dal.fetchCustomersPage(BASE_ID) }
        confirmVerified(dal)
    }

    @Test
    fun `fetcher will return only one page`() {
        expectOnePage()

        val fetcher = CustomersPageFetcher(dal)

        fetcher.hasNext() shouldBe true
        fetcher.nextPage() shouldHaveSize 2
        fetcher.hasNext() shouldBe true
        fetcher.nextPage() shouldHaveSize 0
        fetcher.hasNext() shouldBe false

        verify (exactly = 2) { dal.fetchCustomersPage(any()) }
        confirmVerified(dal)
    }

    private fun expectEmptyPage() {
        every { dal.fetchCustomersPage(BASE_ID) } returns emptyList()
    }

    private fun expectOnePage() {
        val customersPage = listOf(Customer(CUSTOMER_ID_1, Currency.EUR), Customer(CUSTOMER_ID_2, Currency.EUR))
        every { dal.fetchCustomersPage(BASE_ID) } returns customersPage
        every { dal.fetchCustomersPage(CUSTOMER_ID_2) } returns emptyList() //marker -> first page last Id = customer2
    }
}

