package io.paritytech.polkadotapp.common.utils.logging

import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.io.File

/**
 * A Timber.DebugTree that saves every log to a file using the provided coroutineScope.
 *
 * Untagged calls inherit DebugTree's class-name tag, which costs a stack trace per call. Trees that only
 * want tagged lines extend Timber.Tree and hold a [LogFileAppender] instead.
 *
 * @param coroutineScope should be managed by the consumer
 */
abstract class CoroutineFileDebugTree(
    coroutineScope: CoroutineScope,
    fallbackLogTag: String,
    logFile: File
) : Timber.DebugTree() {
    private val appender = LogFileAppender(coroutineScope, fallbackLogTag, logFile)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        appender.append(priority, tag, message, t)
    }
}
