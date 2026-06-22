package com.orderprocessing.repository

import com.orderprocessing.entity.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    @Query("SELECT o FROM Order o JOIN FETCH o.items i JOIN FETCH i.product WHERE o.id = :id")
    fun findByIdWithItems(id: UUID): Order?
}