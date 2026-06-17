package com.orderprocessing.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "order_items")
class OrderItem(

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(nullable = false, updatable = false)
        var id: UUID? = null,

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "order_id", nullable = false)
        var order: Order? = null,

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "product_id", nullable = false)
        var product: Product? = null,

        @Column(nullable = false)
        var quantity: Int = 1,

        @Column(nullable = false, precision = 12, scale = 2)
        var priceAtPurchase: BigDecimal = BigDecimal.ZERO
)