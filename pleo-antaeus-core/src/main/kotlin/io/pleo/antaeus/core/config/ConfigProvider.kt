package io.pleo.antaeus.core.config

//scheduler
const val MONTHLY_SCHEDULE_EXPRESSION =  "0 0 0 1 * 0o"
const val EXEC_PROCESS_ON_BOOT =  true
const val INITIAL_EXECUTION_DELAY =  20

//billing service
const val NETWORK_RETRY_BACKOFF_BASE = 10L
const val NETWORK_RETRY_BACKOFF_MAX = 10000L
const val CUSTOMERS_CHANNEL_MAX_LIMIT = 50
