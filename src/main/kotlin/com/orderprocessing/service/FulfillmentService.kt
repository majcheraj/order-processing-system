package com.orderprocessing.service

import com.orderprocessing.entity.OrderStatus
import com.orderprocessing.exception.ResourceNotFoundException
import com.orderprocessing.repository.OrderRepository
import com.orderprocessing.cache.OrderStatusCache
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
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

    private val fulfillmentQueue = LinkedBlockingQueue<UUID>(100)
    private val running = AtomicBoolean(true)
    private val workers = mutableListOf<Thread>()
    private val workerCount = 3

    @PostConstruct
    fun startWorkers() {
        repeat(workerCount) { index ->
            val worker = Thread({
                println("[warehouse-worker-$index] Started, waiting for orders...")
                while (running.get()) {
                    val orderId = fulfillmentQueue.poll(1, TimeUnit.SECONDS)
                    if (orderId != null) {
                        processfulfillment(orderId, index)
                    }
                }
                println("[warehouse-worker-$index] Shutting down")
            }, "warehouse-worker-$index")
            worker.isDaemon = true
            workers.add(worker)
            worker.start()
        }
        println("[main] Started $workerCount warehouse workers")
    }

    @PreDestroy
    fun stopWorkers() {
        println("[main] Stopping warehouse workers...")
        running.set(false)
        workers.forEach { it.join(2000) }
        println("[main] All warehouse workers stopped")
    }

    fun submitForFulfillment(orderId: UUID) {
        val queued = fulfillmentQueue.offer(orderId, 5, TimeUnit.SECONDS)
        if (!queued) {
            println("[${Thread.currentThread().name}] WARNING: Fulfillment queue full, order $orderId dropped!")
        } else {
            println("[${Thread.currentThread().name}] Order $orderId submitted to fulfillment queue, " +
                    "queue size: ${fulfillmentQueue.size}")
        }
    }

    private fun processfulfillment(orderId: UUID, workerIndex: Int) {
        println("[warehouse-worker-$workerIndex] Processing fulfillment for order $orderId")
        try {
            updateStatus(orderId, OrderStatus.FULFILLMENT)
            Thread.sleep(1500) // simulate packing
            updateStatus(orderId, OrderStatus.SHIPPED)
            println("[warehouse-worker-$workerIndex] Order $orderId shipped!")
        } catch (e: Exception) {
            println("[warehouse-worker-$workerIndex] Error processing order $orderId: ${e.message}")
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
}