package com.orderprocessing.dto

import com.orderprocessing.entity.OrderStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateOrderItemRequest(
        @field:NotNull(message = "Product id is required")
        val productId: UUID,

        @field:Min(value = 1, message = "Quantity must be at least 1")
        val quantity: Int
)

data class CreateOrderRequest(
        @field:NotNull(message = "Customer id is required")
        val customerId: UUID,

        @field:NotEmpty(message = "Order must contain at least one item")
        @field:Valid
        val items: List<CreateOrderItemRequest>
)

data class OrderItemResponse(
        val id: UUID,
        val productId: UUID,
        val productName: String,
        val quantity: Int,
        val priceAtPurchase: BigDecimal
)

data class OrderResponse(
        val id: UUID,
        val customerId: UUID,
        val status: OrderStatus,
        val createdAt: Instant,
        val updatedAt: Instant,
        val totalAmount: BigDecimal,
        val items: List<OrderItemResponse>
)