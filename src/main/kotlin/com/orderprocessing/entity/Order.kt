package com.orderprocessing.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "orders")
class Order(

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        @Column(nullable = false, updatable = false)
        var id: UUID? = null,

        @Column(nullable = false)
        var customerId: UUID = UUID.randomUUID(),

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        var status: OrderStatus = OrderStatus.CREATED,

        @Column(nullable = false)
        var createdAt: Instant = Instant.now(),

        @Column(nullable = false)
        var updatedAt: Instant = Instant.now(),

        @Column(nullable = false, precision = 12, scale = 2)
        var totalAmount: BigDecimal = BigDecimal.ZERO,

        @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
        var items: MutableList<OrderItem> = mutableListOf()

) {
    @Version
    var version: Long = 0
}