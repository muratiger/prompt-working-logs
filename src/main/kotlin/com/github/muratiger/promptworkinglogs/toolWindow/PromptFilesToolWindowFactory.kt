package com.github.muratiger.promptworkinglogs.toolwindow

import com.github.muratiger.promptworkinglogs.action.RunClaudeAction
import com.github.muratiger.promptworkinglogs.domain.FileOperationResult
import com.github.muratiger.promptworkinglogs.domain.FileOperations
import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.FileTransferable
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.PromptFilesController
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.VfsFileOperations
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions.DeleteSelectedAction
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions.MoveSelectedAction
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions.NewDirectoryAction
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions.NewFileAction
import com.github.muratiger.promptworkinglogs.toolwindow.filetree.actions.RenameSelectedAction
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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
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

        toolWindow.setTitleActions(emptyList())
        try {
            toolWindow.title = ""
        } catch (_: Exception) {
            // Older platforms may not allow clearing the title; safe to ignore.
        }
        try {
            toolWindow.stripeTitle = ""
        } catch (_: Exception) {
            // Older platforms may not allow clearing the stripe title; safe to ignore.
        }
    }

    companion object {
        const val TOOL_WINDOW_ID: String = "prompt-work-files"
    }
}

private object PromptFilesLayout {
    const val SPLIT_PROPORTION_TREE_FIRST = 0.3f
    const val SPLIT_PROPORTION_TREE_SECOND = 0.7f
    const val RUN_CLAUDE_SHORTCUT = "control alt W"
}

private enum class TreePosition { LEFT, RIGHT, BOTTOM }

private class PromptFilesPanel(
    override val project: Project,
    private val fileOps: FileOperations = VfsFileOperations(project),
) : JPanel(BorderLayout()), UiDataProvider, PromptFilesController {

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
    private val splitter = OnePixelSplitter(false, PromptFilesLayout.SPLIT_PROPORTION_TREE_FIRST)
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
        runClaudeAction?.registerCustomShortcutSet(
            CustomShortcutSet.fromString(PromptFilesLayout.RUN_CLAUDE_SHORTCUT),
            this
        )
        val newFileAction = NewFileAction(this, fileOps)
        val newDirectoryAction = NewDirectoryAction(this, fileOps)
        val renameAction = RenameSelectedAction(this, fileOps)
        val moveAction = MoveSelectedAction(this, fileOps)
        val deleteAction = DeleteSelectedAction(this, fileOps)
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
                if (events.any { isUnderWatchedRoot(it.path, watchedRoot) }) {
                    SwingUtilities.invokeLater { refreshTree() }
                }
            }

            private fun isUnderWatchedRoot(path: String, watchedRoot: String): Boolean {
                if (path == watchedRoot) return true
                return path.startsWith("$watchedRoot/") || path.startsWith("$watchedRoot\\")
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
        // Splitter#setFirstComponent / setSecondComponent internally do remove -> add,
        // so swapping the same component between first and second can unintentionally
        // remove one side and make it disappear. Reset both slots to null first, then
        // assign the new combination.
        splitter.firstComponent = null
        splitter.secondComponent = null
        contentWrapper.removeAll()

        if (!treeVisible) {
            // Tree hidden: place the editor directly in contentWrapper to use the full space.
            contentWrapper.add(editorContainer, BorderLayout.CENTER)
        } else {
            when (treePosition) {
                TreePosition.LEFT -> {
                    splitter.orientation = false
                    splitter.proportion = PromptFilesLayout.SPLIT_PROPORTION_TREE_FIRST
                    splitter.firstComponent = treeScrollPane
                    splitter.secondComponent = editorContainer
                }
                TreePosition.RIGHT -> {
                    splitter.orientation = false
                    splitter.proportion = PromptFilesLayout.SPLIT_PROPORTION_TREE_SECOND
                    splitter.firstComponent = editorContainer
                    splitter.secondComponent = treeScrollPane
                }
                TreePosition.BOTTOM -> {
                    splitter.orientation = true
                    splitter.proportion = PromptFilesLayout.SPLIT_PROPORTION_TREE_SECOND
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

    override fun refreshTree() {
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

    override fun selectedFile(): File? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.userObjectAsFileNode()?.file

    override fun rootFile(): File? =
        (rootNode.userObject as? FileNode)?.file

    override fun targetDirectoryForCreation(): File? {
        val selected = selectedFile()
        val root = rootFile() ?: return null
        val dir = when {
            selected == null -> root
            selected.isDirectory -> selected
            else -> selected.parentFile ?: root
        }
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    override fun refreshAndSelect(absolutePath: String) {
        val watchedRoot = watchedRootPath()
        if (watchedRoot != null) {
            LocalFileSystem.getInstance().refreshAndFindFileByPath(watchedRoot)?.refresh(false, true)
        }
        refreshTree()
        selectNodeByPath(rootNode, absolutePath)
    }

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

    /**
     * Move via drag and drop. Kept as an inner class because the source/target
     * resolution is tree-coupled by the tree / TransferHandler calling convention.
     * The actual VFS operations are delegated to [fileOps].
     */
    private inner class TreeFileTransferHandler : TransferHandler() {
        private val flavor = DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=java.io.File",
            "File"
        )

        override fun getSourceActions(c: JComponent): Int = MOVE

        override fun createTransferable(c: JComponent): Transferable? {
            val selected = selectedFile() ?: return null
            if (selected == rootFile()) return null
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

            return when (val result = fileOps.moveTo(source, destDir)) {
                is FileOperationResult.Success -> {
                    refreshAndSelect(result.resultPath)
                    true
                }
                is FileOperationResult.Failure -> {
                    Messages.showErrorDialog(project, result.message, "Move")
                    false
                }
            }
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
}
