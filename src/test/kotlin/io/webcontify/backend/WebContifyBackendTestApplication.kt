package io.webcontify.backend

import org.springframework.boot.SpringApplication

class WebContifyBackendTestApplication

fun main(args: Array<String>) {
  SpringApplication.from(WebContifyBackendApplication.Companion::getMain)
      .with(PostgresApplicationConfiguration::class.java)
      .run(*args)
}
