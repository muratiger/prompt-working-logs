package com.github.muratiger.promptworkinglogs.runner

import com.github.muratiger.promptworkinglogs.domain.OutputEventFormatter
import com.github.muratiger.promptworkinglogs.runner.formatter.JsonOutputFormatter
import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.github.muratiger.promptworkinglogs.toolwindow.ClaudeConsoleService
import com.github.muratiger.promptworkinglogs.util.ProjectPaths
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import java.io.File

object ClaudeCliRunner {

    private const val TOOL_WINDOW_ID = "prompt-work"
    private const val NOTIFICATION_GROUP_ID = "prompt-work"

    fun run(
        project: Project,
        file: VirtualFile,
        formatter: OutputEventFormatter = JsonOutputFormatter(),
    ) {
        FileDocumentManager.getInstance().saveAllDocuments()

        val settings = SimpleSettings.getInstance()
        val basePath = project.basePath ?: return
        val relativePath = ProjectPaths.relativeFilePath(basePath, file) ?: return

        // Directory path with the same name as the file (extension stripped, trailing '/').
        val dirPath = relativePath.substringBeforeLast(".") + "/"

        val command = settings.state.cliCommand
            .replace("\${filePath}", relativePath)
            .replace("\${dirPath}", dirPath)
            .replace("\${language}", settings.state.outputLanguage)

        ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.show()

        val service = ClaudeConsoleService.getInstance(project)
        // Bring the log view back to the front even if the previous run switched to the Result MD view.
        service.requestShowConsole()
        service.console?.clear()

        val commandLine = PtyCommandLine(listOf("bash", "-lc", command))
            .withWorkDirectory(basePath)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        try {
            val handler = KillableColoredProcessHandler(commandLine)
            service.processHandler = handler
            handler.addProcessListener(
                StreamingJsonListener(project, file, basePath, dirPath, relativePath, formatter)
            )
            handler.startNotify()
        } catch (e: Exception) {
            thisLogger().warn("Failed to start Claude CLI process", e)
            notify(project, "Claude CLI error", e.message ?: "Unknown error", NotificationType.ERROR)
        }
    }

    private class StreamingJsonListener(
        private val project: Project,
        private val file: VirtualFile,
        private val basePath: String,
        private val dirPath: String,
        private val relativePath: String,
        private val formatter: OutputEventFormatter,
    ) : ProcessAdapter() {
        private val buffer = StringBuilder()

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            val text = event.text
            buffer.append(text)
            val pending = buffer.toString()
            buffer.clear()

            val lines = pending.split("\n")
            val console = ClaudeConsoleService.getInstance(project).console
            val contentType = if (outputType === ProcessOutputTypes.STDERR) {
                ConsoleViewContentType.ERROR_OUTPUT
            } else {
                ConsoleViewContentType.NORMAL_OUTPUT
            }

            for (i in lines.indices) {
                val line = lines[i]
                if (i == lines.lastIndex && !text.endsWith("\n")) {
                    buffer.append(line)
                    continue
                }
                val formatted = formatter.format(line) ?: continue
                console?.print(formatted + "\n", contentType)
            }
        }

        override fun processTerminated(event: ProcessEvent) {
            val service = ClaudeConsoleService.getInstance(project)
            service.processHandler = null

            // Recursively refresh the parent directory so the Project view picks up changes.
            file.parent?.refresh(true, true)

            detectLatestResultMd(project, basePath, dirPath)

            if (event.exitCode != 0) {
                notify(
                    project,
                    "Claude CLI failed",
                    "Exit code: ${event.exitCode}. See the 'Claude Runner' tool window for details.",
                    NotificationType.ERROR
                )
            } else {
                notify(
                    project,
                    "Claude CLI finished",
                    "File updated: $relativePath",
                    NotificationType.INFORMATION
                )
            }
        }
    }

    private fun detectLatestResultMd(project: Project, basePath: String, dirPath: String) {
        val dirFile = File(basePath, dirPath.removeSuffix("/"))
        if (!dirFile.exists() || !dirFile.isDirectory) return
        val latestMd = dirFile.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".md") }
            ?.maxByOrNull { it.lastModified() }
            ?: return
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(latestMd) ?: return
        ApplicationManager.getApplication().invokeLater {
            ClaudeConsoleService.getInstance(project).setLatestResultFile(vf)
        }
    }

    private fun notify(project: Project, title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, message, type)
            .notify(project)
    }
}
