package io.pleo.antaeus.core.scheduler

import dev.inmo.krontab.builder.buildSchedule
import dev.inmo.krontab.doInfinityTz
import dev.inmo.krontab.doOnce
import io.pleo.antaeus.core.config.EXEC_PROCESS_ON_BOOT
import io.pleo.antaeus.core.config.INITIAL_EXECUTION_DELAY
import io.pleo.antaeus.core.exceptions.BillingProcessAlreadyRunning
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.services.EventStatus
import io.pleo.antaeus.core.services.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import io.pleo.antaeus.core.config.MONTHLY_SCHEDULE_EXPRESSION

private val logger = KotlinLogging.logger {}

class BillingProcessScheduler(
    private val billingService: BillingService,
    private val notificationService: NotificationService
) {

    private val scope = CoroutineScope(Dispatchers.Default)

    fun scheduleProcess() = scope.launch {
        if (EXEC_PROCESS_ON_BOOT) {
            buildSchedule { seconds { INITIAL_EXECUTION_DELAY } }.doOnce {
                billingService.initBillingProcess()
            }
        }

        doInfinityTz(MONTHLY_SCHEDULE_EXPRESSION)  {
            try {
                billingService.initBillingProcess()
            } catch (e: BillingProcessAlreadyRunning) {
                logger.warn { "Billing process already running" }
                notificationService.notifyEvent(EventStatus.BILLING_ALREADY_RUNNING)
            }
        }
    }
}