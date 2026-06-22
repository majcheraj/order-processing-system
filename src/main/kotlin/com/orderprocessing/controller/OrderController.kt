package com.orderprocessing.controller

import com.orderprocessing.dto.CreateOrderRequest
import com.orderprocessing.dto.OrderResponse
import com.orderprocessing.service.OrderService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/orders")
class OrderController(
        private val orderService: OrderService
) {

    @PostMapping
    fun createOrder(@Valid @RequestBody request: CreateOrderRequest): ResponseEntity<OrderResponse> {
        val response = orderService.createOrder(request)
        orderService.processOrderAsync(response.id)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val response = orderService.getOrder(id)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun listOrders(
            @RequestParam(defaultValue = "0") page: Int,
            @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Page<OrderResponse>> {
        val pageable = PageRequest.of(page, size)
        val response = orderService.listOrders(pageable)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/cancel")
    fun cancelOrder(@PathVariable id: UUID): ResponseEntity<OrderResponse> {
        val response = orderService.cancelOrder(id)
        return ResponseEntity.ok(response)
    }
}