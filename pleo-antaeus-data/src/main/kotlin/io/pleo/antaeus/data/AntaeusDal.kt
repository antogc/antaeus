/*
    Implements the data access layer (DAL).
    The data access layer generates and executes requests to the database.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

//it should be provided by the configuration provider
private const val DB_PAGE_LIMIT = 50

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id)
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id)
    }

    fun fetchInvoicesByCustomerId(id: Int): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .select { InvoiceTable.customerId eq id }
                .orderBy(InvoiceTable.id to SortOrder.ASC)
                .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesByCustomerIdAndStatus(id: Int, status: InvoiceStatus): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .select { (InvoiceTable.customerId eq id) and (InvoiceTable.status eq status.toString()) }
                .orderBy(InvoiceTable.id to SortOrder.ASC)
                .map { it.toInvoice() }
        }
    }

    fun fetchInvoicesByStatus(status: String): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .select { (InvoiceTable.status eq status) }
                .orderBy(InvoiceTable.id to SortOrder.ASC)
                .map { it.toInvoice() }
        }
    }

    fun updateInvoice(invoice: Invoice, newStatus: InvoiceStatus): Int {
        return transaction(db) {
            InvoiceTable.update({ InvoiceTable.id eq invoice.id })
            { it[status] = newStatus.toString() }
        }
    }

    fun fetchCustomersPage(idMarker: Int): List<Customer> {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id greater idMarker }
                .orderBy(CustomerTable.id to SortOrder.ASC)
                .limit(DB_PAGE_LIMIT)
                .map { it.toCustomer() }
        }
    }
}