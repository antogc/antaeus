package io.pleo.antaeus.core.scheduler

import dev.inmo.krontab.builder.buildSchedule
import dev.inmo.krontab.doInfinityTz
import dev.inmo.krontab.doOnce
import io.pleo.antaeus.core.exceptions.BillingProcessAlreadyRunning
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.EventStatus
import io.pleo.antaeus.core.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging

private const val scheduleExpression =  "0 0 0 1 * 0o"
private const val initialExecutionDelay =  20
private const val executeOnBoot =  true
private val logger = KotlinLogging.logger {}

class BillingProcessScheduler(
    private val billingService: BillingService,
    private val notificationService: NotificationService
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    fun scheduleProcess() = scope.launch {
        if (executeOnBoot) {
            buildSchedule { seconds { initialExecutionDelay } }.doOnce {
                billingService.initBillingProcess()
            }
        }

        doInfinityTz(scheduleExpression)  {
            try {
                billingService.initBillingProcess()
            } catch (e: BillingProcessAlreadyRunning) {
                logger.warn { "Billing process already running" }
                notificationService.notifyEvent(EventStatus.BILLING_ALREADY_RUNNING)
            }
        }
    }
}