package com.orderprocessing.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.Semaphore
import kotlin.random.Random

@Service
class PaymentGatewayService {

    // Max 5 concurrent payment calls allowed
    private val semaphore = Semaphore(5, true)

    companion object {
        private val log = LoggerFactory.getLogger(PaymentGatewayService::class.java)
    }

    fun processPayment(orderId: UUID): Boolean {
        val threadName = Thread.currentThread().name
        log.info("[{}] Waiting for payment gateway permit for order {}, available permits: {}",
                threadName, orderId, semaphore.availablePermits())

        semaphore.acquire()
        try {
            log.info("[{}] Payment gateway permit acquired for order {}, available permits: {}",
                    threadName, orderId, semaphore.availablePermits())

            // Simulate payment gateway call (1-3 seconds)
            val delay = Random.nextLong(1000, 3000)
            Thread.sleep(delay)

            // Simulate 90% success rate
            val success = Random.nextDouble() < 0.9
            log.info("[{}] Payment {} for order {} after {}ms",
                    threadName, if (success) "succeeded" else "failed", orderId, delay)
            return success

        } finally {
            semaphore.release()
            log.info("[{}] Payment gateway permit released for order {}, available permits: {}",
                    threadName, orderId, semaphore.availablePermits())
        }
    }

    fun availablePermits(): Int = semaphore.availablePermits()
}