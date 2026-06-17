package com.orderprocessing.service

import com.orderprocessing.dto.CreateProductRequest
import com.orderprocessing.dto.ProductResponse
import com.orderprocessing.entity.Product
import com.orderprocessing.exception.ResourceNotFoundException
import com.orderprocessing.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

@Service
class ProductService(
        private val productRepository: ProductRepository
) {

    @Transactional
    fun createProduct(request: CreateProductRequest): ProductResponse {
        val product = Product(
                name = request.name,
                price = request.price,
                stockQuantity = request.stockQuantity
        )
        val saved = productRepository.save(product)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun getProduct(id: UUID): ProductResponse {
        val product = productRepository.findById(id)
                .orElseThrow { ResourceNotFoundException("Product with id $id not found") }
        return product.toResponse()
    }

    @Transactional(readOnly = true)
    fun listProducts(pageable: Pageable): Page<ProductResponse> {
        return productRepository.findAll(pageable)
                .map { it.toResponse() }
    }

    private fun Product.toResponse(): ProductResponse {
        return ProductResponse(
                id = this.id ?: throw IllegalStateException("Saved product must have an id"),
                name = this.name,
                price = this.price,
                stockQuantity = this.stockQuantity
        )
    }
}