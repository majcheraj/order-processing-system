package com.orderprocessing.controller

import com.orderprocessing.cache.OrderStatusCache
import com.orderprocessing.service.FulfillmentService
import com.orderprocessing.service.PaymentGatewayService
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.factory.annotation.Qualifier

@RestController
@RequestMapping("/internal")
class ConcurrencyStatusController(
        @Qualifier("orderProcessingExecutor")
        private val orderProcessingExecutor: ThreadPoolTaskExecutor,
        private val paymentGatewayService: PaymentGatewayService,
        private val fulfillmentService: FulfillmentService,
        private val orderStatusCache: OrderStatusCache
) {

    @GetMapping("/concurrency-status")
    fun getConcurrencyStatus(): Map<String, Any> {
        return mapOf(
                "threadPool" to mapOf(
                        "activeCount" to orderProcessingExecutor.activeCount,
                        "poolSize" to orderProcessingExecutor.poolSize,
                        "corePoolSize" to orderProcessingExecutor.corePoolSize,
                        "maxPoolSize" to orderProcessingExecutor.maxPoolSize,
                        "queueSize" to orderProcessingExecutor.threadPoolExecutor.queue.size,
                        "queueRemainingCapacity" to orderProcessingExecutor.threadPoolExecutor.queue.remainingCapacity(),
                        "completedTaskCount" to orderProcessingExecutor.threadPoolExecutor.completedTaskCount
                ),
                "paymentGateway" to mapOf(
                        "availablePermits" to paymentGatewayService.availablePermits(),
                        "maxPermits" to 5
                ),
                "fulfillmentQueue" to mapOf(
                        "currentSize" to fulfillmentService.queueSize(),
                        "maxCapacity" to 100,
                        "remainingCapacity" to fulfillmentService.queueRemainingCapacity()
                ),
                "orderStatusCache" to mapOf(
                        "cachedOrderCount" to orderStatusCache.size()
                )
        )
    }
}