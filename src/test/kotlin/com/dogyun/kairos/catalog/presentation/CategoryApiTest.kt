package com.dogyun.kairos.catalog.presentation

import com.dogyun.kairos.CommerceTestSupport
import tools.jackson.databind.JsonNode
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryApiTest : CommerceTestSupport() {

    private fun names(nodes: JsonNode): List<String> = (0 until nodes.size()).map { nodes[it].get("name").asString() }

    @Test
    fun 관리자가_만든_2단계_트리를_비로그인으로_조회한다() {
        val fashion = createCategory(null, "패션", 1)
        createCategory(null, "뷰티", 2)
        createCategory(fashion, "신발", 2)
        createCategory(fashion, "상의", 1)

        val data = json(get("/api/v1/categories").andExpect { status { isOk() } }).get("data")

        assertEquals(listOf("패션", "뷰티"), names(data))
        assertEquals(listOf("상의", "신발"), names(data[0].get("children")))
        assertEquals(0, data[1].get("children").size())
    }

    @Test
    fun 일반_회원의_카테고리_생성은_403() {
        post("/api/v1/admin/categories", newUserToken(), mapOf("name" to "패션", "displayOrder" to 1))
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun 삭제된_카테고리는_트리에_없다() {
        val root = createCategory(null, "패션", 1)
        val leaf = createCategory(root, "신발", 1)

        delete("/api/v1/admin/categories/$leaf", adminToken()).andExpect { status { isNoContent() } }

        val data = json(get("/api/v1/categories")).get("data")
        assertEquals(0, data[0].get("children").size())
    }

    @Test
    fun 자식이_있는_대분류_삭제는_409_CATEGORY_HAS_CHILDREN() {
        val root = createCategory(null, "패션", 1)
        createCategory(root, "신발", 1)

        val result = delete("/api/v1/admin/categories/$root", adminToken()).andExpect { status { isConflict() } }

        assertEquals("CATEGORY_HAS_CHILDREN", json(result).get("code").asString())
    }

    @Test
    fun 소분류_아래_생성은_400_이고_없는_부모는_404다() {
        val root = createCategory(null, "패션", 1)
        val leaf = createCategory(root, "신발", 1)

        post("/api/v1/admin/categories", adminToken(), mapOf("parentId" to leaf, "name" to "러닝화", "displayOrder" to 1))
            .andExpect { status { isBadRequest() } }
        post("/api/v1/admin/categories", adminToken(), mapOf("parentId" to 99999, "name" to "러닝화", "displayOrder" to 1))
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun 카테고리를_수정한다() {
        val id = createCategory(null, "패션", 1)

        put("/api/v1/admin/categories/$id", adminToken(), mapOf("name" to "의류", "displayOrder" to 3))
            .andExpect { status { isOk() } }

        val node = json(get("/api/v1/categories")).get("data")[0]
        assertEquals("의류", node.get("name").asString())
        assertEquals(3, node.get("displayOrder").asInt())
    }

    @Test
    fun leafCategory_헬퍼는_소분류_ID를_돌려준다() {
        val leaf = leafCategory()

        val children = json(get("/api/v1/categories")).get("data")[0].get("children")
        assertEquals(leaf, children[0].get("categoryId").asLong())
    }
}
