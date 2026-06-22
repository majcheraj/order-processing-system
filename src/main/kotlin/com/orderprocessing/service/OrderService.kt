package com.orderprocessing.service

import com.orderprocessing.dto.CreateOrderRequest
import com.orderprocessing.dto.OrderItemResponse
import com.orderprocessing.dto.OrderResponse
import com.orderprocessing.entity.Order
import com.orderprocessing.entity.OrderItem
import com.orderprocessing.entity.OrderStatus
import com.orderprocessing.exception.ResourceNotFoundException
import com.orderprocessing.repository.OrderRepository
import com.orderprocessing.repository.ProductRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.scheduling.annotation.Async
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class OrderService(
        private val orderRepository: OrderRepository,
        private val productRepository: ProductRepository
) {

    @Transactional
    fun createOrder(request: CreateOrderRequest): OrderResponse {
        val order = Order(
                customerId = request.customerId,
                status = OrderStatus.CREATED
        )

        var total = BigDecimal.ZERO

        for (itemRequest in request.items) {
            val product = productRepository.findById(itemRequest.productId)
                    .orElseThrow {
                        ResourceNotFoundException("Product with id ${itemRequest.productId} not found")
                    }

            val orderItem = OrderItem(
                    order = order,
                    product = product,
                    quantity = itemRequest.quantity,
                    priceAtPurchase = product.price
            )
            order.items.add(orderItem)

            total = total.add(product.price.multiply(BigDecimal(itemRequest.quantity)))
        }

        order.totalAmount = total

        val saved = orderRepository.save(order)
        return saved.toResponse()
    }

    @Async("orderProcessingExecutor")
    fun processOrderAsync(orderId: UUID) {
        val threadName = Thread.currentThread().name
        println("[$threadName] Starting background processing for order $orderId")

        // --- Placeholder: VALIDATING step (real logic comes in 2.6) ---
        updateOrderStatus(orderId, OrderStatus.VALIDATING)
        Thread.sleep(1000)
        updateOrderStatus(orderId, OrderStatus.VALIDATED)
        println("[$threadName] Order $orderId validated")

        // --- Placeholder: PAYMENT_PROCESSING step (real logic comes in 2.5) ---
        updateOrderStatus(orderId, OrderStatus.PAYMENT_PROCESSING)
        Thread.sleep(1000)
        updateOrderStatus(orderId, OrderStatus.PAID)
        println("[$threadName] Order $orderId paid")

        // --- Placeholder: FULFILLMENT step (real logic comes in 2.7) ---
        updateOrderStatus(orderId, OrderStatus.FULFILLMENT)
        Thread.sleep(1000)
        updateOrderStatus(orderId, OrderStatus.SHIPPED)
        println("[$threadName] Order $orderId shipped")
    }

    @Transactional
    fun updateOrderStatus(orderId: UUID, newStatus: OrderStatus) {
        val order = orderRepository.findById(orderId)
                .orElseThrow { ResourceNotFoundException("Order with id $orderId not found") }
        order.status = newStatus
        order.updatedAt = Instant.now()
        orderRepository.save(order)
    }

    @Transactional(readOnly = true)
    fun getOrder(id: UUID): OrderResponse {
        val order = orderRepository.findById(id)
                .orElseThrow { ResourceNotFoundException("Order with id $id not found") }
        return order.toResponse()
    }

    @Transactional(readOnly = true)
    fun listOrders(pageable: Pageable): Page<OrderResponse> {
        return orderRepository.findAll(pageable)
                .map { it.toResponse() }
    }

    @Transactional
    fun cancelOrder(id: UUID): OrderResponse {
        val order = orderRepository.findById(id)
                .orElseThrow { ResourceNotFoundException("Order with id $id not found") }

        if (order.status == OrderStatus.SHIPPED ||
                order.status == OrderStatus.DELIVERED
        ) {
            throw IllegalStateException("Order $id has already shipped and cannot be cancelled")
        }

        order.status = OrderStatus.CANCELLED
        order.updatedAt = Instant.now()

        val saved = orderRepository.save(order)
        return saved.toResponse()
    }

    private fun Order.toResponse(): OrderResponse {
        return OrderResponse(
                id = this.id ?: throw IllegalStateException("Saved order must have an id"),
                customerId = this.customerId,
                status = this.status,
                createdAt = this.createdAt,
                updatedAt = this.updatedAt,
                totalAmount = this.totalAmount,
                items = this.items.map { it.toItemResponse() }
        )
    }

    private fun OrderItem.toItemResponse(): OrderItemResponse {
        return OrderItemResponse(
                id = this.id ?: throw IllegalStateException("Saved order item must have an id"),
                productId = this.product?.id ?: throw IllegalStateException("Order item must have a product"),
                productName = this.product?.name ?: "",
                quantity = this.quantity,
                priceAtPurchase = this.priceAtPurchase
        )
    }
}