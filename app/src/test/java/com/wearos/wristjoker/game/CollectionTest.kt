package com.wearos.wristjoker.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图鉴的断言。
 *
 * 图鉴本身不参与计分，但它最容易出"数量对不上、id 撞车、点开是空描述"
 * 这类静默错误 —— 玩家看到的是一格问号，不会报错也不会闪退。所以这里
 * 把三件事钉死：**数量**、**id 唯一**、**每条都有可展示的文字**。
 */
class CollectionTest {

    @Test
    fun `图鉴五类数量与原作一致`() {
        assertEquals("小丑", 150, Collection.total(CollectionTab.JOKER))
        assertEquals("消耗品（22 塔罗 + 12 行星 + 18 幻灵）", 52, Collection.total(CollectionTab.CONSUMABLE))
        assertEquals("代金券", 32, Collection.total(CollectionTab.VOUCHER))
        assertEquals("标签", 24, Collection.total(CollectionTab.TAG))
        assertEquals("牌组", 15, Collection.total(CollectionTab.DECK))
        assertEquals(273, Collection.all().size)
    }

    @Test
    fun `图鉴 id 全局唯一`() {
        val ids = Collection.all().map { it.id }
        assertEquals("id 有重复：${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size, ids.toSet().size)
        assertEquals(ids.size, Collection.allIds.size)
    }

    @Test
    fun `图鉴 id 与商店 key 同构`() {
        // 商店的 ShopItem.key 用同一套前缀，图鉴才能直接吃 ui.shop 里的 key。
        // 这条一旦崩，说明有人改了其中一边的前缀，已解锁状态会全线错位。
        Jokers.ALL.forEach { assertTrue("小丑 ${it.id}", "j:${it.id}" in Collection.allIds) }
        Consumables.ALL.forEach { assertTrue("消耗品 ${it.key}", "c:${it.key}" in Collection.allIds) }
        Voucher.entries.forEach { assertTrue("券 ${it.key}", "v:${it.key}" in Collection.allIds) }
        Tag.entries.forEach { assertTrue("标签 ${it.key}", "t:${it.key}" in Collection.allIds) }
        DeckType.entries.forEach { assertTrue("牌组 ${it.name}", "d:${it.name}" in Collection.allIds) }
    }

    @Test
    fun `每条图鉴都有名字和描述`() {
        Collection.all().forEach { e ->
            assertTrue("空名字：${e.id}", e.name.isNotBlank())
            assertTrue("空描述：${e.id}", e.desc.isNotBlank())
            assertTrue("空副标题：${e.id}", e.sub.isNotBlank())
            // 5~6 个汉字，圆屏一行放得下；超过 12 个字会在格子里被截成一半
            assertTrue("名字过长：${e.id} ${e.name}", e.name.length <= 8)
        }
    }

    @Test
    fun `已解锁计数只认合法 id`() {
        val seen = setOf("j:joker", "c:t_magician", "v:overstock", "t:charm", "d:RED", "j:不存在的牌")
        assertEquals(1, Collection.seenCount(CollectionTab.JOKER, seen))
        assertEquals(1, Collection.seenCount(CollectionTab.CONSUMABLE, seen))
        assertEquals(1, Collection.seenCount(CollectionTab.VOUCHER, seen))
        assertEquals(1, Collection.seenCount(CollectionTab.TAG, seen))
        assertEquals(1, Collection.seenCount(CollectionTab.DECK, seen))
        // 脏 id 不抛异常、也不计入
        assertEquals(0, Collection.seenCount(CollectionTab.JOKER, setOf("j:nope")))
    }

    @Test
    fun `按 id 反查得到同一条`() {
        Collection.all().take(20).forEach { e ->
            val back = Collection.find(e.id)
            assertNotNull("反查失败：${e.id}", back)
            assertEquals(e.name, back!!.name)
        }
        assertEquals(null, Collection.find("j:没有这张"))
    }
}
