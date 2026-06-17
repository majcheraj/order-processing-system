package com.orderprocessing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OrderProcessingSystemApplication

fun main(args: Array<String>) {
	runApplication<OrderProcessingSystemApplication>(*args)
}
