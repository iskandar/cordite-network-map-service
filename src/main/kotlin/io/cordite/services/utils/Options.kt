package io.cordite.services.utils


class Options {
  data class Option(val name: String, val default: String, val description: String = "") {
    val environmentVariable: String = "NMS_" + name.toUpperCase().replace('.', '_')
    fun width() = name.length

    val value by lazy {
      (System.getenv(environmentVariable) ?: System.getProperty(name) ?: default)
    }
  }

  private val options = mutableListOf<Option>()

  fun addOption(name: String, default: String, description: String = ""): Option {
    val option = Option(name, default, description)
    options.add(option)
    return option
  }

  fun printOptions() {
    val propertyWidth = (options.map { it.width() }.max() ?: 0) + 2
    val envWidth = propertyWidth + 4
    val defaultWidth = (options.map { it.default.length }.max() ?: 0) + 2

    println("\njava properties (pass with -D<propertyname>=<property-value>) and env variables\n")
    println("Property".padEnd(propertyWidth) + "Env Variable".padEnd(envWidth) + "Default".padEnd(defaultWidth) + "Description")
    println("========".padEnd(propertyWidth) + "============".padEnd(envWidth) + "=======".padEnd(defaultWidth) + "===========")
    options.forEach {
      println("${it.name.padEnd(propertyWidth)}${it.environmentVariable.padEnd(envWidth)}${it.default.padEnd(defaultWidth)}${it.description}")
    }
    println()
  }
}