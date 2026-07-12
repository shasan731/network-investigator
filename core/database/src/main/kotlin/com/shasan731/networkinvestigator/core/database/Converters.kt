package com.shasan731.networkinvestigator.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DatabaseConverters {
    @TypeConverter fun stringsToJson(value: List<String>?): String? = value?.let { Json.encodeToString(it) }
    @TypeConverter fun jsonToStrings(value: String?): List<String>? = value?.let { Json.decodeFromString(it) }
}
