package net.kigawa.fonsole.cmd

interface Cmd {
    suspend fun execute()
}
