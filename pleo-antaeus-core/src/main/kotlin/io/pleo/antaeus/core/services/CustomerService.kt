/*
    Implements endpoints related to customers.
*/

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Customer

class CustomerService(private val dal: AntaeusDal) {

    fun fetchAll(): List<Customer> {
        return dal.fetchCustomers()
    }

    fun fetch(id: Int): Customer {
        return dal.fetchCustomer(id) ?: throw CustomerNotFoundException(id)
    }

    fun getPageFetcher(): CustomersPageFetcher {
        return CustomersPageFetcher(dal)
    }
}

/**
 * Simple class that can retrieve all the customer pages
 */
class CustomersPageFetcher (private val dal: AntaeusDal) {
    private var marker = 0

    fun resetMarker() {
        this.marker = 0
    }

    fun hasNext(): Boolean {
        return marker >= 0
    }

    /**
     * Gets next customers page
     */
    fun nextPage(): List<Customer> {
        if (marker == -1) return emptyList()
        val customers = dal.fetchCustomersPage(marker)
        marker = if (customers.isNotEmpty()) customers.last().id else -1
        return customers
    }
}
