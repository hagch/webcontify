package io.webcontify.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebContifyBackendApplication

fun main(args: Array<String>) {
	runApplication<WebContifyBackendApplication>(*args)
}
