package org.modelix.model.server.handlers

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import org.modelix.model.ModelFacade
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.TreePointer
import org.modelix.model.client.IModelClient
import org.modelix.model.lazy.CLVersion
import org.modelix.model.server.templates.PageWithMenuBar

class ContentExplorer(private val client: IModelClient, private val repoManager: RepositoriesManager) {

    private val rootNodes: List<PNodeAdapter>
        get() {
            val nodeList = mutableListOf<PNodeAdapter>()

            for (repoId in repoManager.getRepositories()) {
                val branchRef = repoId.getBranchReference()
                val version = ModelFacade.loadCurrentVersion(client, branchRef) ?: continue
                val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(version.getTree()))
                nodeList.add(rootNode)
            }
            return nodeList
        }

    fun init(application: Application) {
        application.routing {
            get("/content/") {
                call.respondRedirect("../repos/")
            }
            get("/content/{versionHash}/") {
                val versionHash = call.parameters["versionHash"]
                if (versionHash.isNullOrEmpty()) {
                    call.respondText("version not found", status = HttpStatusCode.InternalServerError)
                    return@get
                }
                val tree = CLVersion.loadFromHash(versionHash, client.storeCache).getTree()
                val rootNode = PNodeAdapter(ITree.ROOT_ID, TreePointer(tree))
                call.respondHtmlTemplate(PageWithMenuBar("repos/", "../..")) {
                    headContent {
                        title("Content Explorer")
                        link("../../public/content-explorer.css", rel = "stylesheet")
                        script("text/javascript", src = "../../public/content-explorer.js") {}
                    }
                    bodyContent {contentPageBody(rootNode, versionHash)}
                }
            }
            get("/content/{versionHash}/{nodeId}/") {
                val id = call.parameters["nodeId"]!!.toLong()
                var found: PNodeAdapter? = null
                for (node in rootNodes) {
                    val candidate = PNodeAdapter(id, node.branch).takeIf { it.isValid }
                    if (candidate != null) {
                        found = candidate
                        break
                    }
                }
                if (found == null) {
                    call.respondText("node id not found", status = HttpStatusCode.NotFound)
                } else {
                    call.respondHtml { body { nodeInspector(found) } }
                }
            }
        }
    }

    private fun FlowContent.contentPageBody(rootNode: PNodeAdapter, versionHash: String) {
        h1 {+"Model Server Content"}
        small {
            style = "color: #888; text-align: center; margin-bottom: 15px;"
            +versionHash
        }
        div {
            style = "display: flex;"
            button(classes="btn") {
                id = "expandAllBtn"
                +"Expand all"
            }
            button(classes="btn") {
                id = "collapseAllBtn"
                +"Collapse all"
            }
        }
        div {
            id = "treeWrapper"
            ul("treeRoot") {
                nodeItem(rootNode)
            }
        }
        div {
            id = "nodeInspector"
        }
    }

    private fun UL.nodeItem(node: PNodeAdapter) {
        li("nodeItem") {
            if (node.allChildren.toList().isNotEmpty()) {
                div("expander") { unsafe { +"&#x25B6;" } }
            }
            div("nameField") {
                attributes["data-nodeid"] = node.nodeId.toString()
                b {
                    if (node.getPropertyRoles().contains("name")) {
                        +"${node.getPropertyValue("name")}"
                    } else {
                        +"Unnamed Node"
                    }
                }
                small { +"(${node})" }
                br {  }
                val conceptRef = node.getConceptReference()
                small {
                    if (conceptRef != null) {
                        +conceptRef.getUID()
                    } else {
                        +"No concept reference"
                    }
                }

            }
            div("nested") {
                ul("nodeTree") {
                    for (child in node.allChildren) {
                        nodeItem(child as PNodeAdapter)
                    }
                }
            }

        }
    }

    private fun BODY.nodeInspector(node: PNodeAdapter) {
        div {
            h3 { +"Node Details" }
        }
        val nodeEmpty = node.getReferenceRoles().isEmpty() && node.getPropertyRoles().isEmpty()
        if (nodeEmpty) {
            div { +"No roles." }
            return
        }
        if (node.getPropertyRoles().isEmpty()) {
            div { +"No properties." }
        } else {
            table {
                thead {
                    tr {
                        th { +"PropertyRole" }
                        th { +"Value" }
                    }
                }
                for (propertyRole in node.getPropertyRoles()) {
                    tr {
                        td { +propertyRole }
                        td { +(node.getPropertyValue(propertyRole) ?: "null") }
                    }
                }
            }
        }
        if (node.getReferenceRoles().isEmpty()) {
            div { +"No references." }
        } else {
            table {
                thead {
                    tr {
                        th { +"ReferenceRole" }
                        th { +"Value" }
                    }
                }
                for (referenceRole in node.getReferenceRoles()) {
                    tr {
                        td { +referenceRole }
                        td {
                            // TODO MODELIX-387
                            // +"${node.getReferenceTarget(referenceRole)}"
                        }
                    }
                }
            }
        }
    }
}
