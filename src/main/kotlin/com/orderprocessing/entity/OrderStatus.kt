package com.orderprocessing.entity

enum class OrderStatus {
    CREATED,
    VALIDATING,
    VALIDATED,
    PAYMENT_PROCESSING,
    PAID,
    FULFILLMENT,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    FAILED
}