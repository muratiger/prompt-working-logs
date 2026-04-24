package com.github.muratiger.promptworkinglogs.toolwindow

import com.github.muratiger.promptworkinglogs.action.RunClaudeAction
import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import java.awt.Component
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.DropMode
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class PromptFilesToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = PromptFilesPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        Disposer.register(content) { panel.dispose() }
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val TOOL_WINDOW_ID: String = "prompt-work-files"
    }
}

private enum class TreePosition { LEFT, RIGHT, BOTTOM }

private class PromptFilesPanel(private val project: Project) : JPanel(BorderLayout()), UiDataProvider {

    private data class FileNode(val file: File)

    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel).apply {
        isRootVisible = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = FileTreeCellRenderer()
        dragEnabled = true
        dropMode = DropMode.ON
    }
    private val treeScrollPane = JBScrollPane(tree)
    private val editorContainer = JPanel(BorderLayout())
    private val placeholder = JBLabel("Select a file", JBLabel.CENTER)
    private val splitter = OnePixelSplitter(false, 0.3f)
    private val contentWrapper = JPanel(BorderLayout())

    private var currentEditor: Editor? = null
    private var currentFile: VirtualFile? = null
    private var treePosition: TreePosition = TreePosition.LEFT
    private var treeVisible: Boolean = true

    private val vfsConnection: MessageBusConnection = project.messageBus.connect()

    init {
        tree.addTreeSelectionListener { handleSelection() }

        editorContainer.add(placeholder, BorderLayout.CENTER)

        applyLayout()

        val refreshAction = object : AnAction("Refresh", "Reload the file tree", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = refreshTree()
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val leftAction = SetPositionAction(
            TreePosition.LEFT, "Place tree on the left", AllIcons.General.ArrowLeft
        )
        val rightAction = SetPositionAction(
            TreePosition.RIGHT, "Place tree on the right", AllIcons.General.ArrowRight
        )
        val bottomAction = SetPositionAction(
            TreePosition.BOTTOM, "Place tree on the bottom", AllIcons.General.ArrowDown
        )
        val anchorLeftAction = SetToolWindowAnchorAction(
            ToolWindowAnchor.LEFT, "Anchor tool window to the left", AllIcons.Actions.MoveToLeftTop
        )
        val anchorRightAction = SetToolWindowAnchorAction(
            ToolWindowAnchor.RIGHT, "Anchor tool window to the right", AllIcons.Actions.MoveToRightTop
        )
        val toggleTreeAction = ToggleTreeVisibilityAction()
        val runClaudeAction = ActionManager.getInstance().getAction(RunClaudeAction.ID)
        if (runClaudeAction != null) {
            runClaudeAction.registerCustomShortcutSet(
                CustomShortcutSet.fromString("control alt W"),
                this
            )
        }
        val newFileAction = NewFileAction()
        val newDirectoryAction = NewDirectoryAction()
        val renameAction = RenameSelectedAction()
        val moveAction = MoveSelectedAction()
        val deleteAction = DeleteSelectedAction()
        renameAction.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)),
            tree
        )
        moveAction.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0)),
            tree
        )
        deleteAction.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)),
            tree
        )

        val group = DefaultActionGroup().apply {
            if (runClaudeAction != null) {
                add(runClaudeAction)
                addSeparator()
            }
            add(refreshAction)
            addSeparator()
            add(toggleTreeAction)
            add(leftAction)
            add(rightAction)
            add(bottomAction)
            addSeparator()
            add(anchorLeftAction)
            add(anchorRightAction)
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("PromptFilesToolbar", group, false)
        toolbar.targetComponent = this

        val contextMenuGroup = DefaultActionGroup().apply {
            add(newFileAction)
            add(newDirectoryAction)
            addSeparator()
            add(renameAction)
            add(moveAction)
            add(deleteAction)
            addSeparator()
            add(refreshAction)
        }
        PopupHandler.installPopupMenu(tree, contextMenuGroup, "PromptFilesTreePopup")

        tree.transferHandler = TreeFileTransferHandler()

        add(toolbar.component, BorderLayout.WEST)
        add(contentWrapper, BorderLayout.CENTER)

        vfsConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val watchedRoot = watchedRootPath() ?: return
                if (events.any { it.path.startsWith(watchedRoot) }) {
                    SwingUtilities.invokeLater { refreshTree() }
                }
            }
        })

        refreshTree()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[CommonDataKeys.PROJECT] = project
        val file = currentFile ?: return
        if (!file.isValid) return
        sink[CommonDataKeys.VIRTUAL_FILE] = file
        sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = arrayOf(file)
        sink.lazy(CommonDataKeys.PSI_FILE) {
            if (file.isValid) PsiManager.getInstance(project).findFile(file) else null
        }
    }

    private fun applyLayout() {
        // Splitter は setFirstComponent / setSecondComponent で内部的に remove → add を行うため、
        // 同じコンポーネントを first ⇄ second で入れ替えると片方が意図せず remove されて表示が消える。
        // 一旦両スロットを null にしてから新しい組み合わせを設定する。
        splitter.firstComponent = null
        splitter.secondComponent = null
        contentWrapper.removeAll()

        if (!treeVisible) {
            // ツリー非表示: エディタを contentWrapper に直接配置し、スペース全体を使う
            contentWrapper.add(editorContainer, BorderLayout.CENTER)
        } else {
            when (treePosition) {
                TreePosition.LEFT -> {
                    splitter.orientation = false
                    splitter.proportion = 0.3f
                    splitter.firstComponent = treeScrollPane
                    splitter.secondComponent = editorContainer
                }
                TreePosition.RIGHT -> {
                    splitter.orientation = false
                    splitter.proportion = 0.7f
                    splitter.firstComponent = editorContainer
                    splitter.secondComponent = treeScrollPane
                }
                TreePosition.BOTTOM -> {
                    splitter.orientation = true
                    splitter.proportion = 0.7f
                    splitter.firstComponent = editorContainer
                    splitter.secondComponent = treeScrollPane
                }
            }
            contentWrapper.add(splitter, BorderLayout.CENTER)
        }
        contentWrapper.revalidate()
        contentWrapper.repaint()
    }

    private fun watchedRootPath(): String? {
        val basePath = project.basePath ?: return null
        val watchedDir = SimpleSettings.getInstance().state.watchedDirectory
        return File(basePath, watchedDir).absolutePath
    }

    fun refreshTree() {
        val previouslySelected = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.userObjectAsFileNode()?.file?.absolutePath

        val basePath = project.basePath
        val watchedDir = SimpleSettings.getInstance().state.watchedDirectory
        val rootFile = if (basePath != null) File(basePath, watchedDir) else null

        rootNode.removeAllChildren()
        if (rootFile != null && rootFile.exists()) {
            rootNode.userObject = FileNode(rootFile)
            populateNode(rootNode, rootFile)
        } else {
            rootNode.userObject = "(watched directory does not exist: ${rootFile?.path ?: "?"})"
        }
        treeModel.reload()

        if (previouslySelected != null) {
            selectNodeByPath(rootNode, previouslySelected)
        } else {
            tree.expandPath(TreePath(rootNode.path))
        }
    }

    private fun populateNode(parent: DefaultMutableTreeNode, dir: File) {
        val children = dir.listFiles() ?: return
        children
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .forEach { child ->
                val node = DefaultMutableTreeNode(FileNode(child))
                parent.add(node)
                if (child.isDirectory) {
                    populateNode(node, child)
                }
            }
    }

    private fun selectNodeByPath(node: DefaultMutableTreeNode, absolutePath: String): Boolean {
        val file = node.userObjectAsFileNode()?.file
        if (file?.absolutePath == absolutePath) {
            val path = TreePath(node.path)
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            if (selectNodeByPath(child, absolutePath)) return true
        }
        return false
    }

    private fun handleSelection() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val file = node.userObjectAsFileNode()?.file ?: return
        if (!file.isFile) return

        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
        if (virtualFile == currentFile) return
        openInEditor(virtualFile)
    }

    private fun openInEditor(file: VirtualFile) {
        releaseCurrentEditor()

        val document = FileDocumentManager.getInstance().getDocument(file)
        if (document == null) {
            editorContainer.removeAll()
            editorContainer.add(JBLabel("Could not load document: ${file.name}", JBLabel.CENTER), BorderLayout.CENTER)
            editorContainer.revalidate()
            editorContainer.repaint()
            return
        }

        val editor = EditorFactory.getInstance().createEditor(document, project, file, false)
        currentEditor = editor
        currentFile = file

        editorContainer.removeAll()
        editorContainer.add(editor.component, BorderLayout.CENTER)
        editorContainer.revalidate()
        editorContainer.repaint()
    }

    private fun releaseCurrentEditor() {
        currentEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
        currentEditor = null
        currentFile = null
    }

    fun dispose() {
        vfsConnection.disconnect()
        releaseCurrentEditor()
    }

    private fun DefaultMutableTreeNode.userObjectAsFileNode(): FileNode? =
        userObject as? FileNode

    private inner class FileTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode
            val fileNode = node?.userObject as? FileNode
            if (fileNode != null) {
                text = fileNode.file.name
                icon = if (fileNode.file.isDirectory) AllIcons.Nodes.Folder else AllIcons.FileTypes.Any_type
            }
            return component
        }
    }

    private inner class SetPositionAction(
        private val position: TreePosition,
        text: String,
        icon: Icon
    ) : ToggleAction(text, text, icon) {
        override fun isSelected(e: AnActionEvent): Boolean = treePosition == position

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (state && treePosition != position) {
                treePosition = position
                applyLayout()
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class ToggleTreeVisibilityAction : ToggleAction(
        "Toggle tree visibility",
        "Show/hide the file tree (editor uses full width when hidden)",
        AllIcons.Actions.PreviewDetails
    ) {
        override fun isSelected(e: AnActionEvent): Boolean = treeVisible

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (treeVisible != state) {
                treeVisible = state
                applyLayout()
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class SetToolWindowAnchorAction(
        private val anchor: ToolWindowAnchor,
        text: String,
        icon: Icon
    ) : ToggleAction(text, text, icon) {
        override fun isSelected(e: AnActionEvent): Boolean {
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(PromptFilesToolWindowFactory.TOOL_WINDOW_ID) ?: return false
            return toolWindow.anchor == anchor
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            if (!state) return
            val toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(PromptFilesToolWindowFactory.TOOL_WINDOW_ID) ?: return
            if (toolWindow.anchor != anchor) {
                toolWindow.setAnchor(anchor, null)
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private fun selectedFile(): File? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.userObjectAsFileNode()?.file

    private fun targetDirectoryForCreation(): File? {
        val selected = selectedFile()
        val root = (rootNode.userObject as? FileNode)?.file ?: return null
        val dir = when {
            selected == null -> root
            selected.isDirectory -> selected
            else -> selected.parentFile ?: root
        }
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    private fun refreshAndSelect(absolutePath: String) {
        val watchedRoot = watchedRootPath()
        if (watchedRoot != null) {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(watchedRoot)?.refresh(false, true)
        }
        refreshTree()
        selectNodeByPath(rootNode, absolutePath)
    }

    private inner class NewFileAction : AnAction(
        "New File",
        "Create a new file in the selected directory (or root when nothing is selected)",
        AllIcons.FileTypes.Text
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = targetDirectoryForCreation() != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            val dir = targetDirectoryForCreation() ?: return
            val name = Messages.showInputDialog(
                project,
                "Enter file name",
                "New File",
                AllIcons.FileTypes.Text,
                "",
                FileNameInputValidator(dir)
            )?.trim().orEmpty()
            if (name.isEmpty()) return

            val parentVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir) ?: return
            val created = try {
                WriteCommandAction.runWriteCommandAction<VirtualFile>(project) {
                    parentVf.createChildData(this, name)
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to create file: ${ex.message}", "New File")
                return
            }
            refreshAndSelect(created.path)
        }
    }

    private inner class NewDirectoryAction : AnAction(
        "New Directory",
        "Create a new directory in the selected directory (or root when nothing is selected)",
        AllIcons.Nodes.Folder
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = targetDirectoryForCreation() != null
        }

        override fun actionPerformed(e: AnActionEvent) {
            val dir = targetDirectoryForCreation() ?: return
            val name = Messages.showInputDialog(
                project,
                "Enter directory name",
                "New Directory",
                AllIcons.Nodes.Folder,
                "",
                FileNameInputValidator(dir)
            )?.trim().orEmpty()
            if (name.isEmpty()) return

            val parentVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir) ?: return
            val created = try {
                WriteCommandAction.runWriteCommandAction<VirtualFile>(project) {
                    VfsUtil.createDirectoryIfMissing(parentVf, name)
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to create directory: ${ex.message}", "New Directory")
                return
            }
            if (created == null) {
                Messages.showErrorDialog(project, "Failed to create directory", "New Directory")
                return
            }
            refreshAndSelect(created.path)
        }
    }

    private inner class RenameSelectedAction : AnAction(
        "Rename",
        "Rename the selected file or directory",
        AllIcons.Actions.Edit
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val selected = selectedFile()
            val root = (rootNode.userObject as? FileNode)?.file
            e.presentation.isEnabled = selected != null && selected != root
        }

        override fun actionPerformed(e: AnActionEvent) {
            val selected = selectedFile() ?: return
            val root = (rootNode.userObject as? FileNode)?.file
            if (selected == root) return
            val parent = selected.parentFile ?: return
            val newName = Messages.showInputDialog(
                project,
                "Enter new name",
                "Rename",
                AllIcons.Actions.Edit,
                selected.name,
                FileNameInputValidator(parent, selected)
            )?.trim().orEmpty()
            if (newName.isEmpty() || newName == selected.name) return

            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(selected) ?: return
            val newPath = File(parent, newName).absolutePath
            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    vf.rename(this, newName)
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to rename: ${ex.message}", "Rename")
                return
            }
            refreshAndSelect(newPath)
        }
    }

    private inner class MoveSelectedAction : AnAction(
        "Move",
        "Move the selected file or directory to another directory",
        AllIcons.Actions.Forward
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val selected = selectedFile()
            val root = (rootNode.userObject as? FileNode)?.file
            e.presentation.isEnabled = selected != null && selected != root
        }

        @Suppress("DEPRECATION")
        override fun actionPerformed(e: AnActionEvent) {
            val selected = selectedFile() ?: return
            val root = (rootNode.userObject as? FileNode)?.file ?: return
            if (selected == root) return
            val currentParent = selected.parentFile ?: return

            val candidates = collectMoveTargetDirectories(root, selected)
                .filter { it != currentParent }
            if (candidates.isEmpty()) {
                Messages.showInfoMessage(
                    project,
                    "No other destination directory available",
                    "Move"
                )
                return
            }

            val rootPath = root.toPath()
            val labels = candidates.map { dir ->
                val rel = rootPath.relativize(dir.toPath()).toString()
                if (rel.isEmpty()) "/ (${root.name})" else rel
            }.toTypedArray()

            val idx = Messages.showChooseDialog(
                project,
                "Select destination directory",
                "Move: ${selected.name}",
                AllIcons.Actions.Forward,
                labels,
                labels.first()
            )
            if (idx < 0) return
            val targetDir = candidates[idx]
            val newFile = File(targetDir, selected.name)
            if (newFile.exists()) {
                val kind = if (selected.isDirectory) "directory" else "file"
                Messages.showErrorDialog(
                    project,
                    "A $kind with the same name already exists at destination: ${newFile.path}",
                    "Move"
                )
                return
            }

            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(selected) ?: return
            val targetVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir) ?: return

            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    vf.move(this, targetVf)
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to move: ${ex.message}", "Move")
                return
            }
            refreshAndSelect(newFile.absolutePath)
        }

        private fun collectMoveTargetDirectories(root: File, exclude: File): List<File> {
            val result = mutableListOf<File>()
            fun walk(dir: File) {
                result.add(dir)
                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory && child != exclude) {
                        walk(child)
                    }
                }
            }
            if (root.isDirectory) walk(root)
            return result
        }
    }

    private inner class TreeFileTransferHandler : TransferHandler() {
        private val flavor = DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=java.io.File",
            "File"
        )

        override fun getSourceActions(c: JComponent): Int = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            val selected = selectedFile() ?: return null
            val root = (rootNode.userObject as? FileNode)?.file
            if (selected == root) return null
            return FileTransferable(selected, flavor)
        }

        override fun canImport(support: TransferSupport): Boolean {
            if (!support.isDrop) return false
            if (!support.isDataFlavorSupported(flavor)) return false
            val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
            val path = dropLocation.path ?: return false
            val targetNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return false
            val targetFile = targetNode.userObjectAsFileNode()?.file ?: return false
            val source = sourceFromSupport(support) ?: return false

            val destDir = if (targetFile.isDirectory) targetFile else targetFile.parentFile ?: return false
            if (destDir == source) return false
            if (isAncestor(source, destDir)) return false
            if (destDir == source.parentFile) return false
            return true
        }

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            val dropLocation = support.dropLocation as? JTree.DropLocation ?: return false
            val path = dropLocation.path ?: return false
            val targetNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return false
            val targetFile = targetNode.userObjectAsFileNode()?.file ?: return false
            val source = sourceFromSupport(support) ?: return false

            val destDir = if (targetFile.isDirectory) targetFile else targetFile.parentFile ?: return false
            val destFile = File(destDir, source.name)
            if (destFile.exists()) {
                val kind = if (source.isDirectory) "directory" else "file"
                Messages.showErrorDialog(
                    project,
                    "A $kind with the same name already exists at destination: ${destFile.path}",
                    "Move"
                )
                return false
            }

            val sourceVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(source) ?: return false
            val destVf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(destDir) ?: return false

            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    sourceVf.move(this, destVf)
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to move: ${ex.message}", "Move")
                return false
            }
            refreshAndSelect(destFile.absolutePath)
            return true
        }

        private fun sourceFromSupport(support: TransferSupport): File? {
            return try {
                support.transferable.getTransferData(flavor) as? File
            } catch (_: Exception) {
                null
            }
        }

        private fun isAncestor(ancestor: File, descendant: File): Boolean {
            var current: File? = descendant
            while (current != null) {
                if (current == ancestor) return true
                current = current.parentFile
            }
            return false
        }
    }

    private class FileTransferable(
        private val file: File,
        private val flavor: DataFlavor
    ) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(flavor)
        override fun isDataFlavorSupported(f: DataFlavor): Boolean = f == flavor
        override fun getTransferData(f: DataFlavor): Any {
            if (f != flavor) throw UnsupportedFlavorException(f)
            return file
        }
    }

    private inner class DeleteSelectedAction : AnAction(
        "Delete",
        "Delete the selected file or directory",
        AllIcons.Actions.GC
    ) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val selected = selectedFile()
            val root = (rootNode.userObject as? FileNode)?.file
            e.presentation.isEnabled = selected != null && selected != root
        }

        override fun actionPerformed(e: AnActionEvent) {
            val selected = selectedFile() ?: return
            val root = (rootNode.userObject as? FileNode)?.file
            if (selected == root) return

            val kind = if (selected.isDirectory) "Directory" else "File"
            val result = Messages.showYesNoDialog(
                project,
                "Delete ${selected.name}?" + if (selected.isDirectory) "\n(Files within will also be deleted)" else "",
                "Delete $kind",
                Messages.getWarningIcon()
            )
            if (result != Messages.YES) return

            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(selected) ?: return
            val parentPath = selected.parentFile?.absolutePath
            try {
                WriteCommandAction.runWriteCommandAction(project) {
                    vf.delete(this)
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to delete: ${ex.message}", "Delete")
                return
            }
            if (parentPath != null) {
                refreshAndSelect(parentPath)
            } else {
                refreshTree()
            }
        }
    }

    private class FileNameInputValidator(
        private val parentDir: File,
        private val ignoreExisting: File? = null
    ) : InputValidator {
        override fun checkInput(inputString: String?): Boolean {
            val name = inputString?.trim().orEmpty()
            if (name.isEmpty()) return false
            if (name.contains('/') || name.contains('\\')) return false
            if (name == "." || name == "..") return false
            val candidate = File(parentDir, name)
            if (candidate.exists() && candidate != ignoreExisting) return false
            return true
        }

        override fun canClose(inputString: String?): Boolean = checkInput(inputString)
    }
}
