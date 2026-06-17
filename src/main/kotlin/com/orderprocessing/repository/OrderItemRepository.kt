package com.orderprocessing.repository

import com.orderprocessing.entity.OrderItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderItemRepository : JpaRepository<OrderItem, UUID>