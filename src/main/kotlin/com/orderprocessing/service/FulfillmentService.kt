package com.orderprocessing.service

import com.orderprocessing.entity.OrderStatus
import com.orderprocessing.exception.ResourceNotFoundException
import com.orderprocessing.repository.OrderRepository
import com.orderprocessing.cache.OrderStatusCache
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service
class FulfillmentService(
        private val orderRepository: OrderRepository,
        private val orderStatusCache: OrderStatusCache
) {

    companion object {
        private val log = LoggerFactory.getLogger(FulfillmentService::class.java)
    }

    private val fulfillmentQueue = LinkedBlockingQueue<UUID>(100)
    private val running = AtomicBoolean(true)
    private val workers = mutableListOf<Thread>()
    private val workerCount = 3

    @PostConstruct
    fun startWorkers() {
        repeat(workerCount) { index ->
            val worker = Thread({
                log.info("[warehouse-worker-{}] Started, waiting for orders...", index)
                while (running.get()) {
                    val orderId = fulfillmentQueue.poll(1, TimeUnit.SECONDS)
                    if (orderId != null) {
                        processfulfillment(orderId, index)
                    }
                }
                log.info("[warehouse-worker-{}] Shutting down", index)
            }, "warehouse-worker-$index")
            worker.isDaemon = true
            workers.add(worker)
            worker.start()
        }
        log.info("[main] Started {} warehouse workers", workerCount)
    }

    @PreDestroy
    fun stopWorkers() {
        log.info("[main] Stopping warehouse workers...")
        running.set(false)
        workers.forEach { it.join(2000) }
        log.info("[main] All warehouse workers stopped")
    }

    fun submitForFulfillment(orderId: UUID) {
        val queued = fulfillmentQueue.offer(orderId, 5, TimeUnit.SECONDS)
        if (!queued) {
            log.warn("[{}] Fulfillment queue full, order {} dropped!",
                    Thread.currentThread().name, orderId)
        } else {
            log.info("[{}] Order {} submitted to fulfillment queue, queue size: {}",
                    Thread.currentThread().name, orderId, fulfillmentQueue.size)
        }
    }

    private fun processfulfillment(orderId: UUID, workerIndex: Int) {
        log.info("[warehouse-worker-{}] Processing fulfillment for order {}", workerIndex, orderId)
        try {
            updateStatus(orderId, OrderStatus.FULFILLMENT)
            Thread.sleep(1500)
            updateStatus(orderId, OrderStatus.SHIPPED)
            log.info("[warehouse-worker-{}] Order {} shipped!", workerIndex, orderId)
        } catch (e: Exception) {
            log.error("[warehouse-worker-{}] Error processing order {}: {}",
                    workerIndex, orderId, e.message)
        }
    }

    private fun updateStatus(orderId: UUID, status: OrderStatus) {
        val order = orderRepository.findById(orderId)
                .orElseThrow { ResourceNotFoundException("Order $orderId not found") }
        order.status = status
        order.updatedAt = Instant.now()
        orderRepository.save(order)
        orderStatusCache.update(orderId, status)
    }

    fun queueSize(): Int = fulfillmentQueue.size
    fun queueRemainingCapacity(): Int = fulfillmentQueue.remainingCapacity()
}