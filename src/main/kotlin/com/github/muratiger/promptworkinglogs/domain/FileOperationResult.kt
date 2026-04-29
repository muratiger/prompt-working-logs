package com.github.muratiger.promptworkinglogs.domain

/**
 * Return type for [FileOperations]. On success, holds the absolute path of the
 * affected target (used by the UI for the immediately following selection).
 * On failure, holds the message to display to the user.
 */
sealed class FileOperationResult {
    data class Success(val resultPath: String) : FileOperationResult()
    data class Failure(val message: String) : FileOperationResult()
}
