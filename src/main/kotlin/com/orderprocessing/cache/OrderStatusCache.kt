package com.orderprocessing.cache

import com.orderprocessing.entity.OrderStatus
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class OrderStatusCache {

    private val cache = ConcurrentHashMap<UUID, OrderStatus>()

    fun update(orderId: UUID, status: OrderStatus) {
        cache.compute(orderId) { _, _ -> status }
        println("[${Thread.currentThread().name}] Cache updated: order $orderId -> $status")
    }

    fun get(orderId: UUID): OrderStatus? {
        return cache[orderId]
    }

    fun getAll(): Map<UUID, OrderStatus> {
        return cache.toMap()
    }

    fun warmUp(orders: Map<UUID, OrderStatus>) {
        cache.putAll(orders)
        println("[${Thread.currentThread().name}] Cache warmed up with ${orders.size} orders")
    }
}