package net.kigawa.fonsole

import ch.qos.logback.classic.Logger
import kotlinx.coroutines.*
import net.kigawa.fonsole.config.EnvironmentConfig
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

object Main {
    val config by lazy { EnvironmentConfig() }
    private val logger = logger()

    init {
        val root = LoggerFactory.getLogger("root") as Logger
        root.level = config.logLevel
        val mongo = LoggerFactory.getLogger("org.mongodb") as Logger
        mongo.level = config.mongoLogLevel
        logger.info("log level: ${config.logLevel}")
        logger.info("mongo log level: ${config.mongoLogLevel}")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val argList = args.toMutableList()
        while (argList.isNotEmpty()) {
            val first = argList.removeFirst()
            Cmds.entries.forEach { cmd ->
                if (first == cmd.command) {
                    val handler = CoroutineExceptionHandler { _, exception ->
                        throw RuntimeException(exception)
                    }
                    val job = CoroutineScope(Dispatchers.Default).launch(handler) {
                        cmd.execute()
                    }
                    runBlocking {
                        job.join()
                    }
                    logger.info("job completed ${cmd.command}")
                    return
                }
            }
        }
        logger.error("subcommand not found")
        exitProcess(1)
    }
}