package com.classplatform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ClassPlatformApplication

fun main(args: Array<String>) {
	runApplication<ClassPlatformApplication>(*args)
}
