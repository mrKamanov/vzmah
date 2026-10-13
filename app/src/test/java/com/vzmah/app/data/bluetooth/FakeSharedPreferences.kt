package com.vzmah.app.data.bluetooth

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {

    val data = mutableMapOf<String, String>()

    override fun getAll(): MutableMap<String, *> = data.toMutableMap()

    override fun getString(key: String, defValue: String?): String? = data[key] ?: defValue

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun contains(key: String): Boolean = key in data

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String, defValue: Int): Int = defValue

    override fun getLong(key: String, defValue: Long): Long = defValue

    override fun getFloat(key: String, defValue: Float): Float = defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }

        override fun remove(key: String) = apply { pending[key] = null }

        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) data.clear()
            pending.forEach { (key, value) -> if (value == null) data.remove(key) else data[key] = value }
            return true
        }

        override fun apply() {
            commit()
        }

        override fun putStringSet(key: String, values: MutableSet<String>?) =
            apply { pending[key] = values?.joinToString(",") }

        override fun putInt(key: String, value: Int) = apply { pending[key] = value.toString() }

        override fun putLong(key: String, value: Long) = apply { pending[key] = value.toString() }

        override fun putFloat(key: String, value: Float) = apply { pending[key] = value.toString() }

        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value.toString() }
    }
}
