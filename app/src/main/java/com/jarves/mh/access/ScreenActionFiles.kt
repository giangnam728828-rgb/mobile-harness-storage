package com.jarves.mh.access

import com.jarves.mh.model.ScreenActionRequest
import com.jarves.mh.model.ScreenActionResult
import java.io.File

/**
 * The on-disk mailbox an agent uses to ask for screen actions.
 *
 * The guest runtime cannot call Android APIs, so it drops a JSON request into the project
 * workspace and waits for a JSON result. The app polls that directory, shows the request to
 * the user, and only then writes the result back. Because the mailbox lives inside the
 * project workspace, the agent can only reach projects the user already has open.
 */
object ScreenActionFiles {

    const val DIR_NAME = ".mh-screen"
    private const val REQUESTS = "requests"
    private const val RESULTS = "results"

    fun requestsDir(projectWorkspace: File): File =
        File(projectWorkspace, "$DIR_NAME/$REQUESTS").apply { mkdirs() }

    fun resultsDir(projectWorkspace: File): File =
        File(projectWorkspace, "$DIR_NAME/$RESULTS").apply { mkdirs() }

    /** Reads every pending request, oldest first, and removes malformed files. */
    fun readPending(projectWorkspace: File): List<Pair<File, ScreenActionRequest>> {
        val dir = requestsDir(projectWorkspace)
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?: return emptyList()
        val parsed = mutableListOf<Pair<File, ScreenActionRequest>>()
        for (file in files) {
            val request = runCatching {
                ScreenActionRequest.fromJson(file.readText())
            }.getOrNull()
            if (request == null) {
                // Keep the file so the user can inspect it, but stop retrying it forever.
                runCatching { file.renameTo(File(file.parentFile, "${file.name}.bad")) }
            } else {
                parsed.add(file to request)
            }
        }
        return parsed
    }

    /** Writes the result where the waiting agent will find it. */
    fun writeResult(projectWorkspace: File, result: ScreenActionResult) {
        val dir = resultsDir(projectWorkspace)
        val target = File(dir, "${result.id}.json")
        val temp = File(dir, "${result.id}.json.tmp")
        runCatching {
            temp.writeText(result.toJson().toString())
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    fun consume(requestFile: File) {
        runCatching { requestFile.delete() }
    }

    /** Drops result files older than an hour so the mailbox cannot grow without bound. */
    fun pruneResults(projectWorkspace: File) {
        val cutoff = System.currentTimeMillis() - 60L * 60 * 1000
        resultsDir(projectWorkspace).listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }
}
