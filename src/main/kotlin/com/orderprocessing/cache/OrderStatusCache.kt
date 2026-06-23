package com.orderprocessing.cache

import com.orderprocessing.entity.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class OrderStatusCache {

    companion object {
        private val log = LoggerFactory.getLogger(OrderStatusCache::class.java)
    }

    private val cache = ConcurrentHashMap<UUID, OrderStatus>()

    fun update(orderId: UUID, status: OrderStatus) {
        cache.compute(orderId) { _, _ -> status }
        log.info("[{}] Cache updated: order {} -> {}", Thread.currentThread().name, orderId, status)
    }

    fun get(orderId: UUID): OrderStatus? {
        return cache[orderId]
    }

    fun getAll(): Map<UUID, OrderStatus> {
        return cache.toMap()
    }

    fun warmUp(orders: Map<UUID, OrderStatus>) {
        cache.putAll(orders)
        log.info("[{}] Cache warmed up with {} orders", Thread.currentThread().name, orders.size)
    }

    fun size(): Int = cache.size
}