/*
    Implements endpoints related to customers.
 */

package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Customer
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class CustomerService(private val dal: AntaeusDal) {
    fun fetchAll(): List<Customer> {
        return dal.fetchCustomers()
    }

    fun fetch(id: Int): Customer {
        return dal.fetchCustomer(id) ?: throw CustomerNotFoundException(id)
    }

    suspend fun initCustomerPagesChannel(channel: Channel<Customer>)  {
        logger.debug {"Starting process to fetch customers by page" }
        var currentId = 0 //assuming initial id 0
        while (currentId >= 0) {
            val customers = dal.fetchCustomersPage(currentId)
            logger.info {"Fetched page $currentId items ${customers.size}" }
            customers.forEach {
                channel.send(it)
            }
            currentId = if (customers.isNotEmpty()) customers.last().id else -1
        }
        channel.close()
        logger.debug {"Channel closed" }
    }
}
