package com.github.muratiger.promptworkinglogs.toolwindow.filetree

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

/**
 * Transferable wrapper used when the prompt-files tree initiates a drag-and-drop move.
 */
internal class FileTransferable(
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
