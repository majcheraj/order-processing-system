package com.orderprocessing.service

import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.Semaphore
import kotlin.random.Random

@Service
class PaymentGatewayService {

    // Max 5 concurrent payment calls allowed
    private val semaphore = Semaphore(5, true)

    fun processPayment(orderId: UUID): Boolean {
        val threadName = Thread.currentThread().name
        println("[$threadName] Waiting for payment gateway permit for order $orderId, " +
                "available permits: ${semaphore.availablePermits()}")

        semaphore.acquire()
        try {
            println("[$threadName] Payment gateway permit acquired for order $orderId, " +
                    "available permits: ${semaphore.availablePermits()}")

            // Simulate payment gateway call (1-3 seconds)
            val delay = Random.nextLong(1000, 3000)
            Thread.sleep(delay)

            // Simulate 90% success rate
            val success = Random.nextDouble() < 0.9
            println("[$threadName] Payment ${if (success) "succeeded" else "failed"} " +
                    "for order $orderId after ${delay}ms")
            return success

        } finally {
            semaphore.release()
            println("[$threadName] Payment gateway permit released for order $orderId, " +
                    "available permits: ${semaphore.availablePermits()}")
        }
    }
}