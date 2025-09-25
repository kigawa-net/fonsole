package net.kigawa.fonsole.editor

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Sorts
import com.mongodb.client.model.Updates
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import net.kigawa.fonsole.config.ProjectConfig
import net.kigawa.fonsole.document.BackupDocument
import net.kigawa.fonsole.logger
import net.kigawa.fonsole.model.DirectoryModel
import net.kigawa.fonsole.model.FileModel
import net.kigawa.fonsole.mongo.Database
import net.kigawa.kutil.domain.result.ErrorResult
import net.kigawa.kutil.domain.result.Result
import net.kigawa.kutil.domain.result.SuccessResult
import org.bson.types.ObjectId
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import kotlin.io.path.*

class BackupEditor(
    private val projectConfig: ProjectConfig,
    database: Database,
) {
    private val collection = database.getCollection(BackupDocument::class)
    private val bucket = database.createBucket(BackupDocument::class.simpleName!!)
    private val logger = logger()

    private suspend fun uploadDirectory(currentDirectory: Path): Deferred<DirectoryModel> {
        val files = currentDirectory.listDirectoryEntries().filter { !it.isDirectory() }.map { uploadFile(it) }
        val dirs = currentDirectory.listDirectoryEntries().filter { it.isDirectory() }.map { uploadDirectory(it) }
        return CoroutineScope(currentCoroutineContext()).async {
            DirectoryModel(
                name = currentDirectory.name,
                files = files.map { it.await() },
                subDirectories = dirs.map { it.await() }
            )
        }
    }

    private suspend fun uploadFile(file: Path): Deferred<FileModel> {
        return CoroutineScope(currentCoroutineContext()).async {
            logger.debug("open file {}", file)
            Files.newByteChannel(file).use {
                bucket.request {
                    val uploadSubscriber = ChannelSubscriber<ObjectId>(capacity = 1)
                    logger.info("upload file ${file.name}")
                    val fileReadPublisher = FileReadPublisher(it)
                    uploadFromPublisher(file.name, fileReadPublisher).subscribe(uploadSubscriber)
                    withContext(Dispatchers.IO) {
                        fileReadPublisher.write()
                    }
                    FileModel(uploadSubscriber.receive(), file.name).also {
                        uploadSubscriber.join()
                        logger.info("uploaded file {}", it)
                    }
                }
            }
        }
    }

    suspend fun uploadBackup(backupDocumentId: ObjectId): Result<Unit, Unit> {
        val dir = projectConfig.directory
        if (Files.isDirectory(dir).not()) {
            logger.error("$dir is not directory")
            return ErrorResult(Unit)
        }
        val directory = uploadDirectory(dir).await()
        logger.debug("upload directory {} {}", backupDocumentId, directory)
        collection.request {
            findOneAndUpdate(
                Filters.eq("_id", backupDocumentId),
                Updates.combine(
                    Updates.set(BackupDocument::rootDirectory.name, directory),
                    Updates.set(BackupDocument::endDate.name, LocalDateTime.now()),
                )
            ).also { logger.debug("update root directory {}", it) }
        }
        return SuccessResult(Unit)
    }

    suspend fun insertBackupDocument(backupDocumentId: ObjectId) {
        collection.request {
            insertOne(
                BackupDocument(
                    id = backupDocumentId,
                    startDate = LocalDateTime.now()
                )
            )
        }
    }

    suspend fun findBackup(
        date: LocalDateTime, backups: List<ObjectId>,
    ): Result<BackupDocument, Unit> {
        val document = collection.request {
            find(
                Filters.and(
                    Filters.lt<LocalDateTime>(BackupDocument::endDate.name, date),
                    Filters.`in`<ObjectId>("_id", backups),
                    Filters.not(Filters.eq(BackupDocument::removeRequest.name, true))
                ),
            ).sort(Sorts.descending(BackupDocument::endDate.name))
                .limit(1)
                .singleOrNull()
        }
        if (document == null) {
            logger.error("backup is not exist")
            return ErrorResult(Unit)
        }
        return SuccessResult(document)
    }

    suspend fun downloadBackup(backupDocument: BackupDocument, targetDirectory: String? = null): Result<Unit, Unit> {
        val root = backupDocument.rootDirectory
        if (root == null) return ErrorResult(Unit)

        if (targetDirectory != null) {
            val targetDir = findTargetDirectory(root, targetDirectory)
            if (targetDir == null) {
                logger.error("Target directory '$targetDirectory' not found in backup")
                return ErrorResult(Unit)
            }
            val targetPath = projectConfig.directory.resolve(targetDirectory)
            @OptIn(ExperimentalPathApi::class)
            if (targetPath.exists())
                targetPath.deleteRecursively()
            val result = downloadDirectory(targetDir, targetPath)
            if (result.await() is ErrorResult) result.cancelAndJoin()
            return result.await()
        } else {
            @OptIn(ExperimentalPathApi::class)
            if (projectConfig.directory.exists())
                projectConfig.directory.listDirectoryEntries().forEach { it.deleteRecursively() }
            val result = downloadDirectory(root, projectConfig.directory)
            if (result.await() is ErrorResult) result.cancelAndJoin()
            return result.await()
        }
    }

    private fun findTargetDirectory(root: DirectoryModel, targetPath: String): DirectoryModel? {
        val pathParts = targetPath.split("/").filter { it.isNotEmpty() }
        return findDirectoryRecursive(root, pathParts)
    }

    private fun findDirectoryRecursive(current: DirectoryModel, remainingPath: List<String>): DirectoryModel? {
        if (remainingPath.isEmpty()) return current

        val nextDirName = remainingPath.first()
        val nextDir = current.subDirectories.find { it.name == nextDirName }
            ?: return null

        return findDirectoryRecursive(nextDir, remainingPath.drop(1))
    }

    private suspend fun downloadDirectory(
        directoryModel: DirectoryModel, directory: Path,
    ): Deferred<Result<Unit, Unit>> {
        return CoroutineScope(currentCoroutineContext()).async {
            directory.createDirectories()
            val dirs = directoryModel.subDirectories.map { downloadDirectory(it, directory.resolve(it.name)) }
            val files = directoryModel.files.map { downloadFile(it, directory.resolve(it.name)) }
            dirs.forEach { if (it.await() is ErrorResult) return@async ErrorResult(Unit) }
            files.forEach { if (it.await() is ErrorResult) return@async ErrorResult(Unit) }
            SuccessResult(Unit)
        }
    }

    private suspend fun downloadFile(fileModel: FileModel, file: Path): Deferred<Result<Unit, Unit>> {
        return CoroutineScope(currentCoroutineContext()).async {
            bucket.request {
                val subscriber = ChannelSubscriber<ByteBuffer>()
                val publisher = downloadToPublisher(fileModel.id)
                publisher.subscribe(subscriber)
                file.createFile()
                Files.newByteChannel(file.toAbsolutePath(), StandardOpenOption.WRITE).use {
                    withContext(Dispatchers.IO) {
                        for (byteBuffer in subscriber) {
                            it.write(byteBuffer)
                        }
                    }
                }
                logger.info("created file ${file.name}")
                SuccessResult(Unit)
            }
        }
    }

    suspend fun markToRemove(
        backups: List<ObjectId>,
    ) {
        collection.request {
            val now = LocalDateTime.now()
            var nextLatestDate: LocalDateTime = now.minusDays(2)
            find(
                Filters.and(
                    Filters.or(
                        Filters.lt<LocalDateTime>(BackupDocument::endDate.name, nextLatestDate),
                        Filters.eq<LocalDateTime>(BackupDocument::endDate.name, null),
                    ),
                    Filters.lt<LocalDateTime>(BackupDocument::startDate.name, nextLatestDate),
                    Filters.`in`<ObjectId>("_id", backups),
                    Filters.not(Filters.eq(BackupDocument::removeRequest.name, true))
                ),
            ).sort(Sorts.descending(BackupDocument::endDate.name))
                .filter {
                    if (it.endDate == null) return@filter true
                    if (it.endDate > nextLatestDate) return@filter true
                    if (it.endDate > now.minusMonths(1)) nextLatestDate = it.endDate.minusDays(1)
                    else if (it.endDate > now.minusMonths(3)) nextLatestDate = it.endDate.minusDays(7)
                    else nextLatestDate = it.endDate.minusMonths(1)
                    return@filter false
                }
        }.let {
            collection.request {
                updateMany(
                    Filters.`in`<ObjectId>("_id", it.map { it.id }.toList()),
                    Updates.set(BackupDocument::removeRequest.name, true)
                )
            }
        }
    }

    suspend fun removeBackup() {
        collection.request {
            find(Filters.eq(BackupDocument::removeRequest.name, true))
        }.collect {
            if (it.rootDirectory != null) removeDir(it.rootDirectory).join()
            collection.request {
                deleteOne(Filters.eq("_id", it.id))
            }
        }
    }

    private suspend fun removeDir(directoryModel: DirectoryModel): Job {
        return CoroutineScope(currentCoroutineContext()).launch {
            val dirs = directoryModel.subDirectories.map { removeDir(it) }
            val files = directoryModel.files.map { removeFile(it) }
            dirs.forEach { it.join() }
            files.forEach { it.join() }
        }
    }

    private suspend fun removeFile(fileModel: FileModel): Job {
        return CoroutineScope(currentCoroutineContext()).launch {
            bucket.request {
                val subscriber = ChannelSubscriber<Any>()
                delete(fileModel.id).subscribe(subscriber)
                subscriber.join()
            }
        }
    }
}