/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License. 
 */

package org.modelix.model.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import org.junit.Test
import org.modelix.authorization.installAuthentication
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.operations.OTBranch
import org.modelix.model.server.handlers.ModelReplicationServer
import org.modelix.model.server.store.InMemoryStoreClient
import kotlin.test.assertEquals

class ModelClientV2_Test {

    private fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            installAuthentication(unitTestMode = true)
            install(ContentNegotiation) {
                json()
            }
            install(WebSockets)
            ModelReplicationServer(InMemoryStoreClient()).init(this)
        }
        block()
    }

    @Test
    fun test_t1() = runTest {
        val url = "http://localhost/v2"
        val client = ModelClientV2.builder().url(url).client(client).build().also { it.init() }

        val repositoryId = RepositoryId("repo1")
        val initialVersion = client.initRepository(repositoryId)
        assertEquals(0, initialVersion.getTree().getAllChildren(ITree.ROOT_ID).count())

        val branch = OTBranch(PBranch(initialVersion.getTree(), client.getIdGenerator()), client.getIdGenerator(), client.store)
        branch.runWriteT { t ->
            t.addNewChild(ITree.ROOT_ID, "role", -1, null as IConceptReference?)
        }
        val (ops, newTree) = branch.operationsAndTree
        val newVersion = CLVersion.createRegularVersion(
            client.getIdGenerator().generate(),
            null,
            null,
            newTree as CLTree,
            initialVersion as CLVersion,
            ops.map { it.getOriginalOp() }.toTypedArray()
        )

        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference())
        )

        val branchId = repositoryId.getBranchReference("my-branch")
        val mergedVersion = client.push(branchId, newVersion)
        assertEquals(1, mergedVersion.getTree().getAllChildren(ITree.ROOT_ID).count())

        assertEquals(
            client.listBranches(repositoryId).toSet(),
            setOf(repositoryId.getBranchReference(), branchId)
        )
    }
}
