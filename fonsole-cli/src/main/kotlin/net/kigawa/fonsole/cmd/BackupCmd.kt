package net.kigawa.fonsole.cmd

import net.kigawa.fonsole.editor.BackupEditor
import net.kigawa.fonsole.editor.ProjectEditor
import net.kigawa.fonsole.logger
import net.kigawa.fonsole.mongo.Client
import net.kigawa.kutil.domain.result.SuccessResult
import org.bson.types.ObjectId

class BackupCmd : CmdBase() {
    private val logger = logger()
    override suspend fun execute() {
        Client.connect(config.connectionConfig) {
            val database = it.database
            logger.info("setup project...")
            val projectEditor = ProjectEditor(database, config.projectConfig)
            projectEditor.setupProject()
            logger.info("create backup info...")
            val backupEditor = BackupEditor(config.projectConfig, database)
            val backupDocumentId = ObjectId()
            projectEditor.addBackupId(backupDocumentId)
            backupEditor.insertBackupDocument(backupDocumentId)
            logger.info("upload files...")
            backupEditor.uploadBackup(backupDocumentId)
            logger.info("delete backup...")
            val backups = projectEditor.findBackups()
            if (backups !is SuccessResult) return@connect
            backupEditor.markToRemove(backups.result)
            backupEditor.removeBackup()
            logger.info("backup finished!")
        }
    }
}