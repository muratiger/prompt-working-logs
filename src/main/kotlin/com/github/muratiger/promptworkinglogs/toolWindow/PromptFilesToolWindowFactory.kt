package com.github.muratiger.promptworkinglogs.toolwindow

import com.github.muratiger.promptworkinglogs.settings.SimpleSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.messages.MessageBusConnection
import java.awt.BorderLayout
import java.awt.Component
import java.io.File
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingUtilities
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
    }
    private val treeScrollPane = JBScrollPane(tree)
    private val editorContainer = JPanel(BorderLayout())
    private val placeholder = JBLabel("ファイルを選択してください", JBLabel.CENTER)
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

        val refreshAction = object : AnAction("更新", "ファイルツリーを再読み込み", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) = refreshTree()
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        }
        val leftAction = SetPositionAction(
            TreePosition.LEFT, "ツリーを左に配置", AllIcons.General.ArrowLeft
        )
        val rightAction = SetPositionAction(
            TreePosition.RIGHT, "ツリーを右に配置", AllIcons.General.ArrowRight
        )
        val bottomAction = SetPositionAction(
            TreePosition.BOTTOM, "ツリーを下に配置", AllIcons.General.ArrowDown
        )
        val anchorLeftAction = SetToolWindowAnchorAction(
            ToolWindowAnchor.LEFT, "ToolWindow を左側に表示", AllIcons.Actions.MoveToLeftTop
        )
        val anchorRightAction = SetToolWindowAnchorAction(
            ToolWindowAnchor.RIGHT, "ToolWindow を右側に表示", AllIcons.Actions.MoveToRightTop
        )
        val toggleTreeAction = ToggleTreeVisibilityAction()
        val group = DefaultActionGroup(
            refreshAction,
            Separator.getInstance(),
            toggleTreeAction,
            leftAction,
            rightAction,
            bottomAction,
            Separator.getInstance(),
            anchorLeftAction,
            anchorRightAction
        )
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("PromptFilesToolbar", group, false)
        toolbar.targetComponent = this

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
            rootNode.userObject = "(watched directory が存在しません: ${rootFile?.path ?: "?"})"
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
            editorContainer.add(JBLabel("ドキュメントを読み込めません: ${file.name}", JBLabel.CENTER), BorderLayout.CENTER)
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
        "ツリー表示切替",
        "ファイルツリーの表示/非表示を切り替え（非表示時はエディタを全幅で表示）",
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
}
