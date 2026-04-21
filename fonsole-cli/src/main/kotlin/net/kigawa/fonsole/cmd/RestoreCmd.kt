package net.kigawa.fonsole.cmd

import net.kigawa.fonsole.editor.BackupEditor
import net.kigawa.fonsole.editor.ProjectEditor
import net.kigawa.fonsole.logger
import net.kigawa.fonsole.mongo.Client
import net.kigawa.kutil.domain.result.SuccessResult

class RestoreCmd : CmdBase() {
    private val logger = logger()
    override suspend fun execute() {
        Client.connect(config.connectionConfig) {
            val database = it.database
            logger.info("setup project...")
            val projectEditor = ProjectEditor(database, config.projectConfig)
            val backups = projectEditor.findBackups()
            if (backups !is SuccessResult) return@connect
            logger.info("find backup...")
            val backupEditor = BackupEditor(config.projectConfig, database)
            val backup = backupEditor.findBackup(config.restoreConfig.restoreDate, backups.result)
            if (backup !is SuccessResult) return@connect
            logger.info("restore backup... ${backup.result.id}")
            val targetDirectory = config.restoreConfig.targetDirectory
            if (targetDirectory != null) {
                logger.info("restoring specific directory: $targetDirectory")
            }
            backupEditor.downloadBackup(backup.result, targetDirectory)
        }
    }
}