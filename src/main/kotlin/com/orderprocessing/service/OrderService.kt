package com.orderprocessing.service

import com.orderprocessing.dto.CreateOrderRequest
import com.orderprocessing.dto.OrderItemResponse
import com.orderprocessing.dto.OrderResponse
import com.orderprocessing.entity.Order
import com.orderprocessing.entity.OrderItem
import com.orderprocessing.entity.OrderStatus
import com.orderprocessing.exception.ResourceNotFoundException
import com.orderprocessing.exception.InsufficientStockException
import com.orderprocessing.repository.OrderRepository
import com.orderprocessing.repository.ProductRepository
import com.orderprocessing.cache.OrderStatusCache
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.scheduling.annotation.Async
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class OrderService(
        private val orderRepository: OrderRepository,
        private val productRepository: ProductRepository,
        private val stockService: StockService,
        private val orderStatusCache: OrderStatusCache,
        private val paymentGatewayService: PaymentGatewayService
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
        updateOrderStatusWithRetry(orderId, OrderStatus.VALIDATING)
        try {
            stockService.reserveStock(orderId)
        } catch (e: InsufficientStockException) {
            updateOrderStatus(orderId, OrderStatus.FAILED)
            println("[$threadName] Order $orderId failed: ${e.message}")
            return
        }
        updateOrderStatusWithRetry(orderId, OrderStatus.VALIDATED)
        println("[$threadName] Order $orderId validated, stock reserved")

        // --- Step: PAYMENT_PROCESSING — throttled via Semaphore ---
        updateOrderStatusWithRetry(orderId, OrderStatus.PAYMENT_PROCESSING)
        val paymentSuccess = paymentGatewayService.processPayment(orderId)
        if (!paymentSuccess) {
            updateOrderStatusWithRetry(orderId, OrderStatus.FAILED)
            println("[$threadName] Order $orderId payment failed")
            return
        }
        updateOrderStatusWithRetry(orderId, OrderStatus.PAID)
        println("[$threadName] Order $orderId paid")

        // --- Placeholder: FULFILLMENT step (real logic comes in 2.7) ---
        updateOrderStatusWithRetry(orderId, OrderStatus.FULFILLMENT)
        Thread.sleep(1000)
        updateOrderStatusWithRetry(orderId, OrderStatus.SHIPPED)
        println("[$threadName] Order $orderId shipped")
    }

    @Transactional
    fun updateOrderStatus(orderId: UUID, newStatus: OrderStatus) {
        val order = orderRepository.findById(orderId)
                .orElseThrow { ResourceNotFoundException("Order with id $orderId not found") }
        order.status = newStatus
        order.updatedAt = Instant.now()
        orderRepository.save(order)
        orderStatusCache.update(orderId, newStatus)
    }

    fun updateOrderStatusWithRetry(orderId: UUID, newStatus: OrderStatus, maxRetries: Int = 3) {
        var attempt = 0
        while (attempt < maxRetries) {
            try {
                updateOrderStatus(orderId, newStatus)
                return
            } catch (e: ObjectOptimisticLockingFailureException) {
                attempt++
                println("[${Thread.currentThread().name}] Optimistic lock conflict on order $orderId " +
                        "status update to $newStatus, attempt $attempt of $maxRetries")
                if (attempt >= maxRetries) {
                    throw e
                }
                Thread.sleep(50)
            }
        }
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