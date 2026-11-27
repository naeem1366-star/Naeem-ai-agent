package com.naeem.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File

data class SavedImage(val file: File, val prompt: String, val createdAt: Long)

class AssistantDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("naeem_ai_assistant_data", Context.MODE_PRIVATE)
    private val albumDir = File(context.filesDir, "image_album").apply { mkdirs() }

    fun memories(): List<String> = prefs.getStringSet("memories", emptySet())?.toList()?.sorted() ?: emptyList()

    fun memorySyncEnabled(): Boolean = prefs.getBoolean("memory_sync_enabled", true)

    fun setMemorySyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("memory_sync_enabled", enabled).apply()
    }

    fun addMemory(value: String) {
        val clean = value.trim()
        if (clean.isBlank()) return
        val updated = memories().toMutableSet().apply { add(clean) }
        prefs.edit().putStringSet("memories", updated).apply()
    }

    fun deleteMemory(value: String) {
        val updated = memories().toMutableSet().apply { remove(value) }
        prefs.edit().putStringSet("memories", updated).apply()
    }

    fun replaceMemories(values: List<String>) {
        prefs.edit().putStringSet("memories", values.map { it.trim() }.filter { it.isNotBlank() }.toSet()).apply()
    }

    fun clearImages() {
        images().forEach { it.file.delete() }
        prefs.edit().remove("album_items").apply()
    }

    fun restoreImage(bytes: ByteArray, prompt: String, createdAt: Long): SavedImage {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("یکی از تصاویر پشتیبان قابل خواندن نیست.")
        val file = File(albumDir, "naeem_$createdAt.jpg")
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        } finally {
            bitmap.recycle()
        }
        val all = prefs.getStringSet("album_items", emptySet())?.toMutableSet() ?: mutableSetOf()
        all.add("$createdAt|$prompt")
        prefs.edit().putStringSet("album_items", all).apply()
        return SavedImage(file, prompt, createdAt)
    }

    fun saveImage(bitmap: Bitmap, prompt: String): SavedImage {
        val timestamp = System.currentTimeMillis()
        val file = File(albumDir, "naeem_$timestamp.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        val all = prefs.getStringSet("album_items", emptySet())?.toMutableSet() ?: mutableSetOf()
        all.add("$timestamp|$prompt")
        prefs.edit().putStringSet("album_items", all).apply()
        return SavedImage(file, prompt, timestamp)
    }

    fun images(): List<SavedImage> {
        val entries = prefs.getStringSet("album_items", emptySet()) ?: emptySet()
        return entries.mapNotNull { entry ->
            val split = entry.split("|", limit = 2)
            val timestamp = split.firstOrNull()?.toLongOrNull() ?: return@mapNotNull null
            val file = File(albumDir, "naeem_$timestamp.jpg")
            if (!file.exists()) return@mapNotNull null
            SavedImage(file, split.getOrElse(1) { "" }, timestamp)
        }.sortedByDescending { it.createdAt }
    }

    fun deleteImage(item: SavedImage) {
        item.file.delete()
        val updated = (prefs.getStringSet("album_items", emptySet()) ?: emptySet())
            .filterNot { it.startsWith("${item.createdAt}|") }.toSet()
        prefs.edit().putStringSet("album_items", updated).apply()
    }
}
