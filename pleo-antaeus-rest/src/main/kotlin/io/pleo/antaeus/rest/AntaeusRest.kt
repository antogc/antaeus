/*
    Configures the rest app along with basic exception handling and URL endpoints.
 */

package io.pleo.antaeus.rest

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.pleo.antaeus.core.exceptions.*
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.InvoiceStatus
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@OptIn(DelicateCoroutinesApi::class)
class AntaeusRest(
    private val invoiceService: InvoiceService,
    private val customerService: CustomerService,
    private val billingService: BillingService
) : Runnable {

    override fun run() {
        app.start(7000)
    }

    private fun getStatus(status: String): InvoiceStatus? {
        return try {
            InvoiceStatus.valueOf(status)
        } catch (e: Exception) {
            null
        }
    }

    // Set up Javalin rest app
    private val app = Javalin
        .create()
        .apply {
            // InvoiceNotFoundException: return 404 HTTP status code
            exception(EntityNotFoundException::class.java) { _, ctx ->
                ctx.status(404)
            }
            // Unexpected exception: return HTTP 500
            exception(Exception::class.java) { e, _ ->
                logger.error(e) { "Internal server error" }
            }
            // On 404: return message
            error(404) { ctx -> ctx.json("not found") }
        }

    init {
        // Set up URL endpoints for the rest app
        app.routes {
            get("/") {
                it.result("Welcome to Antaeus! see AntaeusRest class for routes")
            }
            path("rest") {
                // Route to check whether the app is running
                // URL: /rest/health
                get("health") {
                    it.json("ok")
                }

                // V1
                path("v1") {
                    path("invoices") {
                        // URL: /rest/v1/invoices
                        get {
                            it.json(invoiceService.fetchAll())
                            it.status(200)
                        }

                        // URL: /rest/v1/invoices/{:id}
                        get(":id") {
                            it.json(invoiceService.fetch(it.pathParam("id").toInt()))
                            it.status(200)
                        }

                        // URL: /rest/v1/invoices/status/:status}
                        get("/status/:status") {
                            val status = getStatus(it.pathParam("status").uppercase())
                            if (status != null) {
                                it.json(invoiceService.fetchByStatus(status))
                                it.status(200)
                            } else {
                                it.json("Wrong status")
                                it.status(400)
                            }
                        }

                        // URL: /rest/v1/invoices/status/:status}
                        get("/customer/:id") {
                            try {
                                val customer = customerService.fetch(it.pathParam("id").toInt())
                                it.json(invoiceService.fetchByCustomerId(customer.id))
                                it.status(200)
                            } catch (e: CustomerNotFoundException) {
                                it.json("Customer not found")
                                it.status(404)
                            }
                        }
                    }

                    path("customers") {
                        // URL: /rest/v1/customers
                        get {
                            it.json(customerService.fetchAll())
                            it.status(200)
                        }

                        // URL: /rest/v1/customers/{:id}
                        get(":id") {
                            try {
                                val customer = customerService.fetch(it.pathParam("id").toInt())
                                it.json(customer)
                                it.status(200)
                            } catch (e: CustomerNotFoundException) {
                                it.json("Customer not found")
                                it.status(404)
                            }
                        }
                    }

                    path("payments") {
                        //URL: /rest/v1/payments/executeBillingProcess
                        post("/executeBillingProcess") {
                            if (billingService.isRunning) {
                                it.status(405)
                                it.json("The billing process is currently been executed")
                            } else {
                                GlobalScope.launch { billingService.initBillingProcess() }
                                it.status(200)
                                it.json("Process launched")
                            }
                        }

                        post("/invoices/:id") {
                            // URL: /rest/v1/payments/invoices/{:id}
                            if (billingService.isRunning) {
                                it.status(405)
                                it.json("The billing process is currently been executed")
                            } else {
                                val invoice = invoiceService.fetch(it.pathParam("id").toInt())
                                val customer = customerService.fetch(invoice.customerId)
                                try {
                                    if (billingService.processSingleInvoice(customer, invoice)) {
                                        it.status(200)
                                        it.json("Invoice correctly processed")
                                    } else {
                                        it.status(200)
                                        it.json("The invoice cannot be paid. Insufficient funds")
                                    }
                                } catch (e : CustomerNotFoundException) {
                                    it.status(404)
                                    it.json("The customer owning the invoice was not found. Please contact the administrators")
                                } catch (e : CurrencyMismatchException) {
                                    it.status(422)
                                    it.json("There was an error processing the invoice. The invoice's currency does not match customer one. Please contact the administrators")
                                } catch (e : NetworkException) {
                                    it.status(502)
                                    it.json("There was an temporal error processing the invoice. Please try again in a moment")
                                } catch (e : InvoiceNotUpdatedException) {
                                    it.status(500)
                                    it.json("There was an error updating the invoice. The payment was processed, but the invoice could not be updated in the db. PLease contact administrators.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
