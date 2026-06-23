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
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class OrderService(
        private val orderRepository: OrderRepository,
        private val productRepository: ProductRepository,
        private val stockService: StockService,
        private val orderStatusCache: OrderStatusCache,
        private val paymentGatewayService: PaymentGatewayService,
        private val validationService: ValidationService,
        private val fulfillmentService: FulfillmentService
) {

    companion object {
        private val log = LoggerFactory.getLogger(OrderService::class.java)
    }

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
        log.info("[{}] Starting background processing for order {}", threadName, orderId)

        // --- Placeholder: VALIDATING step (real logic comes in 2.6) ---
        updateOrderStatusWithRetry(orderId, OrderStatus.VALIDATING)
        try {
            validationService.validateOrder(orderId)
        } catch (e: Exception) {
            updateOrderStatusWithRetry(orderId, OrderStatus.FAILED)
            log.error("[{}] Order {} validation failed: {}", threadName, orderId, e.message)
            return
        }

        try {
            stockService.reserveStock(orderId)
        } catch (e: InsufficientStockException) {
            updateOrderStatusWithRetry(orderId, OrderStatus.FAILED)
            log.error("[{}] Order {} insufficient stock: {}", threadName, orderId, e.message)
            return
        }
        updateOrderStatusWithRetry(orderId, OrderStatus.VALIDATED)
        log.info("[{}] Order {} validated, stock reserved", threadName, orderId)

        // --- Step: PAYMENT_PROCESSING — throttled via Semaphore ---
        updateOrderStatusWithRetry(orderId, OrderStatus.PAYMENT_PROCESSING)
        val paymentSuccess = paymentGatewayService.processPayment(orderId)
        if (!paymentSuccess) {
            updateOrderStatusWithRetry(orderId, OrderStatus.FAILED)
            log.error("[{}] Order {} payment failed", threadName, orderId)
            return
        }
        updateOrderStatusWithRetry(orderId, OrderStatus.PAID)
        log.info("[{}] Order {} paid", threadName, orderId)

        // --- Step: FULFILLMENT — submit to BlockingQueue pipeline ---
        fulfillmentService.submitForFulfillment(orderId)
        log.info("[{}] Order {} submitted to fulfillment pipeline", threadName, orderId)
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
                log.warn("[{}] Optimistic lock conflict on order {} status update to {}, attempt {} of {}",
                        Thread.currentThread().name, orderId, newStatus, attempt, maxRetries)
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