package com.vzmah.app.data.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostMemoryStoreTest {

    private val prefs = FakeSharedPreferences()
    private val store = HostMemoryStore(prefs)

    @Test
    fun `empty store has nothing remembered`() {
        assertTrue(store.all().isEmpty())
        assertNull(store.lastUsed())
    }

    @Test
    fun `remembered host survives a new store over the same preferences`() {
        store.remember("AA:BB:CC:DD:EE:FF", "Рабочий ПК", 1_000L)

        val reopened = HostMemoryStore(prefs)
        assertEquals(1, reopened.all().size)
        assertTrue(reopened.isRemembered("AA:BB:CC:DD:EE:FF"))
        assertEquals("Рабочий ПК", reopened.lastUsed()?.name)
    }

    @Test
    fun `reusing the same host updates time instead of duplicating`() {
        store.remember("AA:BB:CC:DD:EE:FF", "ПК", 1_000L)
        store.remember("AA:BB:CC:DD:EE:FF", "ПК переименован", 5_000L)

        assertEquals(1, store.all().size)
        assertEquals("ПК переименован", store.all().single().name)
        assertEquals(5_000L, store.lastUsed()?.lastUsedAtMs)
    }

    @Test
    fun `last used is the most recent one`() {
        store.remember("A", "Дом", 1_000L)
        store.remember("B", "Офис", 2_000L)
        store.remember("C", "Аудитория", 3_000L)

        assertEquals("Аудитория", store.lastUsed()?.name)
        assertEquals(listOf("C", "B", "A"), store.all().map { it.address })
    }

    @Test
    fun `forget removes a host that is no longer paired`() {
        store.remember("A", "Дом", 1_000L)
        store.remember("B", "Офис", 2_000L)

        store.forget("B")

        assertFalse(store.isRemembered("B"))
        assertEquals(listOf("A"), store.all().map { it.address })
    }

    @Test
    fun `forget of an unknown host changes nothing`() {
        store.remember("A", "Дом", 1_000L)
        val before = store.all()

        store.forget("НЕТ-ТАКОГО")

        assertEquals(before, store.all())
    }

    @Test
    fun `blank address is not remembered`() {
        store.remember("", "Без адреса", 1_000L)

        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `host name containing separators survives a round trip`() {

        val tricky = "Дом \u001F «Аудитория 1» \u001E PC \\ в конце"
        store.remember("A", tricky, 1_000L)

        val restored = HostMemoryStore(prefs).all().single()
        assertEquals("A", restored.address)
        assertEquals(tricky, restored.name)
        assertEquals(1_000L, restored.lastUsedAtMs)
    }

    @Test
    fun `corrupted payload does not crash the store`() {
        prefs.data["remembered_hosts"] = "мусор"
        assertTrue(store.all().isEmpty())

        prefs.data["remembered_hosts"] = "A\u001FДом\u001Fне-число"
        assertTrue(store.all().isEmpty())

        prefs.data["remembered_hosts"] = "\u001F\u001F"
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `store keeps only the most recent hosts`() {
        repeat(30) { index -> store.remember("ADDR$index", "PC$index", index.toLong()) }

        val all = store.all()
        assertTrue("Ожидалось ограничение списка, получено ${all.size}", all.size <= 12)
        assertEquals("ADDR29", all.first().address)
    }
}
