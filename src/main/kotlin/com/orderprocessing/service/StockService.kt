package com.orderprocessing.service

import com.orderprocessing.entity.OrderStatus
import com.orderprocessing.exception.InsufficientStockException
import com.orderprocessing.exception.ResourceNotFoundException
import com.orderprocessing.repository.OrderRepository
import com.orderprocessing.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StockService(
        private val orderRepository: OrderRepository,
        private val productRepository: ProductRepository
) {

    @Transactional
    fun reserveStock(orderId: UUID) {
        val order = orderRepository.findByIdWithItems(orderId)
                ?: throw ResourceNotFoundException("Order with id $orderId not found")

        val sortedItems = order.items.sortedBy { it.product?.id.toString() }

        for (item in sortedItems) {
            val product = productRepository.findByIdWithLock(
                    item.product?.id ?: throw IllegalStateException("Order item missing product")
            ) ?: throw ResourceNotFoundException("Product not found")

            if (product.stockQuantity < item.quantity) {
                throw InsufficientStockException(
                        "Insufficient stock for product '${product.name}': " +
                                "requested ${item.quantity}, available ${product.stockQuantity}"
                )
            }

            product.stockQuantity -= item.quantity
            productRepository.save(product)
            println("[${Thread.currentThread().name}] Reserved ${item.quantity} units of '${product.name}', " +
                    "remaining stock: ${product.stockQuantity}")
        }
    }
}