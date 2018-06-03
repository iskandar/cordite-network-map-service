package io.cordite.networkmap.utils


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
    val propertyWidth = (options.map { it.width() }.max() ?: 0)
    val envWidth = propertyWidth + 8
    val defaultWidth = (options.map { it.default.length }.max() ?: 0) + 4
    val descriptionWidth = (options.map { it.description.length}.max() ?: 0) + 4

    println("\njava properties (pass with -D<propertyname>=<property-value>) and env variables\n")
    println("| Property".padEnd(propertyWidth + 2) + " | Env Variable".padEnd(envWidth + 3) + " | Default".padEnd(defaultWidth + 3) + " | Description".padEnd(descriptionWidth + 3) + " |")
    println("| --------".padEnd(propertyWidth + 2, '-') + " | ------------".padEnd(envWidth + 3, '-') + " | -------".padEnd(defaultWidth + 3, '-') + " | -----------".padEnd(descriptionWidth + 3, '-') + " |")
    options.forEach {
      println("| ${it.name.padEnd(propertyWidth)} | ${it.environmentVariable.padEnd(envWidth)} | ${it.default.padEnd(defaultWidth)} | ${it.description.padEnd(descriptionWidth)} |")
    }
    println()
  }
}