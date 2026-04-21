package net.kigawa.fonsole

import net.kigawa.fonsole.cmd.BackupCmd
import net.kigawa.fonsole.cmd.Cmd
import net.kigawa.fonsole.cmd.RestoreCmd

enum class Cmds(
    val command: String,
    val newTask: () -> Cmd,
) {
    BACKUP("backup", ::BackupCmd),
    RESTORE("restore", ::RestoreCmd),
    ;

    suspend fun execute() = newTask().execute()
}