package com.orderprocessing.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.util.UUID

data class CreateProductRequest(
        @field:NotBlank(message = "Product name must not be blank")
        val name: String,

        @field:DecimalMin(value = "0.0", inclusive = true, message = "Price must not be negative")
        val price: BigDecimal,

        @field:Min(value = 0, message = "Stock quantity must not be negative")
        val stockQuantity: Int
)

data class ProductResponse(
        val id: UUID,
        val name: String,
        val price: BigDecimal,
        val stockQuantity: Int
)