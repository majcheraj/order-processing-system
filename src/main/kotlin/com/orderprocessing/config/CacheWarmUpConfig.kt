package com.orderprocessing.config

import com.orderprocessing.cache.OrderStatusCache
import com.orderprocessing.repository.OrderRepository
import com.orderprocessing.entity.OrderStatus
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CacheWarmUpConfig(
        private val orderRepository: OrderRepository,
        private val orderStatusCache: OrderStatusCache
) {

    @Bean
    fun warmUpCache(): ApplicationRunner {
        return ApplicationRunner {
            val excludedStatuses = listOf(
                    OrderStatus.DELIVERED,
                    OrderStatus.CANCELLED,
                    OrderStatus.FAILED
            )
            val orders = orderRepository.findAllByStatusNotIn(excludedStatuses)
                    .associate { it.id!! to it.status }
            orderStatusCache.warmUp(orders)
        }
    }
}