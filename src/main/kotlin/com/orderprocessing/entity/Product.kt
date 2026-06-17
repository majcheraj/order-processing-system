package com.orderprocessing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "products")
class Product(

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(nullable = false, updatable = false)
        var id: UUID? = null,

        @Column(nullable = false)
        var name: String = "",

        @Column(nullable = false, precision = 12, scale = 2)
        var price: BigDecimal = BigDecimal.ZERO,

        @Column(nullable = false)
        var stockQuantity: Int = 0
)