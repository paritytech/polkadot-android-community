package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_impl.domain.COINAGE_LOG_TAG

private val LOG_ENTRY_START = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""")

// Entry-aware rather than line-aware: a stack trace belongs to the entry above it and carries none of the tag
// itself, so filtering per line would drop exactly the failures the export exists to show.
internal fun Sequence<String>.filterCoinageLogEntries(maxLines: Int): List<String> {
    val lines = ArrayDeque<String>()
    var inCoinageEntry = false

    forEach { line ->
        if (LOG_ENTRY_START.containsMatchIn(line)) {
            inCoinageEntry = COINAGE_LOG_TAG in line
        }

        if (inCoinageEntry) {
            lines.addLast(line)
            if (lines.size > maxLines) lines.removeFirst()
        }
    }

    return lines
}
