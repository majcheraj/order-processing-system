package com.orderprocessing.controller

import com.orderprocessing.dto.CreateProductRequest
import com.orderprocessing.dto.ProductResponse
import com.orderprocessing.service.ProductService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@RestController
@RequestMapping("/products")
class ProductController(
        private val productService: ProductService
) {

    @PostMapping
    fun createProduct(@Valid @RequestBody request: CreateProductRequest): ResponseEntity<ProductResponse> {
        val response = productService.createProduct(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{id}")
    fun getProduct(@PathVariable id: UUID): ResponseEntity<ProductResponse> {
        val response = productService.getProduct(id)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun listProducts(
            @RequestParam(defaultValue = "0") page: Int,
            @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Page<ProductResponse>> {
        val pageable = PageRequest.of(page, size)
        val response = productService.listProducts(pageable)
        return ResponseEntity.ok(response)
    }
}