package net.kigawa.fonsole.config

import java.time.LocalDateTime

data class RestoreConfig(
    val restoreDate: LocalDateTime,
    val targetDirectory: String? = null,
)
