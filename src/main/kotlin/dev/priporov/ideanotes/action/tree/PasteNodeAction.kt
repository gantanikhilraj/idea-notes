package dev.priporov.ideanotes.action.tree

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.priporov.ideanotes.dto.NodeCopyData
import dev.priporov.ideanotes.dto.NodeCreationInfo
import dev.priporov.ideanotes.dto.NodeCutData
import dev.priporov.ideanotes.tree.NoteTree
import dev.priporov.ideanotes.tree.common.NodeType
import dev.priporov.ideanotes.tree.node.FileTreeNode
import dev.priporov.ideanotes.tree.state.NodeInfo
import dev.priporov.ideanotes.util.IconUtils
import dev.priporov.ideanotes.util.WriteActionUtils
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.io.File

private val PASTE_ICON = IconUtils.toIcon("menu/paste.png")

class PasteNodeAction(
    private val tree: NoteTree,
    value: String? = null
) : AnAction(value, "", PASTE_ICON) {

    override fun actionPerformed(e: AnActionEvent) {
        val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard

        if (!isCopyOrCut(clipboard)) {
            if(isCopiedSystemFile(clipboard)){
                if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                    val files = clipboard.getData(DataFlavor.javaFileListFlavor)
                    if (null != files && files is ArrayList<*>) {
                        val copiedFiles = files as ArrayList<File?>
                        if(copiedFiles.isEmpty()){
                            return
                        }
                        val file= copiedFiles[0]!!
                        val type = NodeType.fromExtension(file.extension)
                        if(type == NodeType.UNKNOWN){
                            return
                        }
                        val nodeInfo = NodeInfo(file.name, file.extension, type = type)
                        createCopyNode(NodeCopyData(nodeInfo, file.readBytes()))
                    }
                }
            }
            return
        }

        val data = clipboard.getData(NodeCopyTransferable.dataFlavor)
        if (data == null) {
            return
        }
        if (data is NodeCopyData) {
            createCopyNode(data)
        } else if (data is NodeCutData) {
            createCutNode(data, tree.getSelectedNode())
        }
    }

    private fun createCopyNode(data: NodeCopyData) {
        val creationInfo = NodeCreationInfo(
            tree.getSelectedNode() ?: tree.root,
            data.nodeInfo.name,
            data.nodeInfo.extension,
            data.nodeInfo.type
        )
        val copiedNode = tree.insert(creationInfo)

        WriteActionUtils.runWriteAction {
            copiedNode.getFile()?.setBinaryContent(data.content)
        }
    }

    private fun createCutNode(data: NodeCutData, targetNode: FileTreeNode?) {
        val creationInfo = NodeCreationInfo(
            targetNode ?: tree.root,
            data.nodeInfo.name,
            data.nodeInfo.extension,
            data.nodeInfo.type
        )
        val copiedNode = tree.insert(creationInfo)
        WriteActionUtils.runWriteAction {
            copiedNode.getFile()?.setBinaryContent(data.content)
        }

        data.children?.forEach {
            createCutNode(it, copiedNode)
        }
    }

    private fun isCopyOrCut(clipboard: Clipboard):Boolean {
        val availableDataFlavors = clipboard.availableDataFlavors
        return availableDataFlavors.contains(NodeCutTransferable.dataFlavor) || availableDataFlavors.contains(NodeCopyTransferable.dataFlavor)
    }

    private fun isCopiedSystemFile(clipboard: Clipboard):Boolean {
        return clipboard.availableDataFlavors.contains(DataFlavor.javaFileListFlavor)
    }
}