package net.kigawa.fonsole.cmd

import net.kigawa.fonsole.Main

abstract class CmdBase : Cmd {
    val config
        get() = Main.config
}