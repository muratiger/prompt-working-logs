package com.github.muratiger.promptworkinglogs.domain

/**
 * [FileOperations] の戻り値。成功時は対象になった絶対パスを保持する（UI 側で
 * 直後の選択処理に使う）。失敗時はユーザーに表示するメッセージを保持する。
 */
sealed class FileOperationResult {
    data class Success(val resultPath: String) : FileOperationResult()
    data class Failure(val message: String) : FileOperationResult()
}
