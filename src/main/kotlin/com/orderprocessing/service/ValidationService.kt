package com.orderprocessing.service

import com.orderprocessing.entity.Order
import com.orderprocessing.exception.InsufficientStockException
import com.orderprocessing.exception.ResourceNotFoundException
import com.orderprocessing.repository.OrderRepository
import com.orderprocessing.repository.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service
class ValidationService(
        private val orderRepository: OrderRepository,
        private val productRepository: ProductRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(ValidationService::class.java)
    }

    private val validationExecutor = ThreadPoolExecutor(
            3,                          // corePoolSize
            24,                         // maxPoolSize
            60L,                        // keepAliveTime
            TimeUnit.SECONDS,           // keepAliveTime unit
            SynchronousQueue()          // queue bez kapaciteta — odmah pravi novi thread
    )

    fun validateOrder(orderId: UUID) {
        val order = orderRepository.findByIdWithItems(orderId)
                ?: throw ResourceNotFoundException("Order $orderId not found")

        val latch = CountDownLatch(3)
        val failed = AtomicBoolean(false)
        val failureReason = AtomicReference<String>("")
        val threadName = Thread.currentThread().name

        // Check 1: Stock availability
        validationExecutor.submit {
            try {
                log.info("[{} → validation-thread] Checking stock for order {}", threadName, orderId)
                checkStockAvailability(order)
                log.info("[{} → validation-thread] Stock check passed for order {}", threadName, orderId)
            } catch (e: Exception) {
                failed.set(true)
                failureReason.set("Stock check failed: ${e.message}")
            } finally {
                latch.countDown()
            }
        }

        // Check 2: Customer eligibility
        validationExecutor.submit {
            try {
                log.info("[{} → validation-thread] Checking customer eligibility for order {}", threadName, orderId)
                checkCustomerEligibility(order)
                log.info("[{} → validation-thread] Customer check passed for order {}", threadName, orderId)
            } catch (e: Exception) {
                failed.set(true)
                failureReason.set("Customer check failed: ${e.message}")
            } finally {
                latch.countDown()
            }
        }

        // Check 3: Price consistency
        validationExecutor.submit {
            try {
                log.info("[{} → validation-thread] Checking price consistency for order {}", threadName, orderId)
                checkPriceConsistency(order)
                log.info("[{} → validation-thread] Price check passed for order {}", threadName, orderId)
            } catch (e: Exception) {
                failed.set(true)
                failureReason.set("Price check failed: ${e.message}")
            } finally {
                latch.countDown()
            }
        }

        // Wait for all 3 checks to complete
        log.info("[{}] Waiting for all validation checks to complete...", threadName)
        latch.await()
        log.info("[{}] All validation checks completed for order {}", threadName, orderId)

        if (failed.get()) {
            throw IllegalStateException(failureReason.get())
        }
    }

    private fun checkStockAvailability(order: Order) {
        Thread.sleep(200) // simulate check time
        for (item in order.items) {
            val product = item.product
                    ?: throw IllegalStateException("Order item missing product")
            if (product.stockQuantity < item.quantity) {
                throw InsufficientStockException(
                        "Insufficient stock for '${product.name}': " +
                                "requested ${item.quantity}, available ${product.stockQuantity}"
                )
            }
        }
    }

    private fun checkCustomerEligibility(order: Order) {
        Thread.sleep(300) // simulate external customer service call
        // Stub: all customers are eligible in this demo
        if (order.customerId.toString() == "00000000-0000-0000-0000-000000000000") {
            throw IllegalStateException("Customer ${order.customerId} is blacklisted")
        }
    }

    private fun checkPriceConsistency(order: Order) {
        Thread.sleep(150) // simulate check time
        for (item in order.items) {
            val product = item.product
                    ?: throw IllegalStateException("Order item missing product")
            if (item.priceAtPurchase != product.price) {
                throw IllegalStateException(
                        "Price mismatch for '${product.name}': " +
                                "paid ${item.priceAtPurchase}, current ${product.price}"
                )
            }
        }
    }
}