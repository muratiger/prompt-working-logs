package com.github.muratiger.promptworkinglogs.toolwindow.filetree

import com.intellij.openapi.project.Project
import java.io.File

/**
 * prompt-files ツリーパネルの API 抽象。アクションクラスを inner class から
 * 切り出すために必要な panel 状態（選択中ファイル、ルート、再描画）を最小限に
 * 公開する。実装は [com.github.muratiger.promptworkinglogs.toolwindow.PromptFilesToolWindowFactory]
 * 内部のパネルが担う。
 */
interface PromptFilesController {
    val project: Project

    /** 現在ツリーで選択されているファイル/ディレクトリ。未選択時は null。 */
    fun selectedFile(): File?

    /** ツリーのルートディレクトリ（監視対象ディレクトリ）。未存在時は null。 */
    fun rootFile(): File?

    /** 新規作成の対象となるディレクトリ。選択がディレクトリならそれ、ファイルなら親、未選択ならルート。 */
    fun targetDirectoryForCreation(): File?

    /** ツリー全体を再構築し、指定パスを選択状態にする。 */
    fun refreshAndSelect(absolutePath: String)

    /** ツリー全体を再構築する。 */
    fun refreshTree()
}
