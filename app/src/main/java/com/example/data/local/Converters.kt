package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.ReviewMark
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val listType = Types.newParameterizedType(List::class.java, ReviewMark::class.java)
    private val adapter = moshi.adapter<List<ReviewMark>>(listType)

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    @TypeConverter
    fun fromReviewMarkList(list: List<ReviewMark>?): String? {
        if (list == null) return "[]"
        return adapter.toJson(list)
    }

    @TypeConverter
    fun toReviewMarkList(value: String?): List<ReviewMark>? {
        if (value.isNullOrBlank()) return emptyList()
        return adapter.fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        if (list == null) return "[]"
        return stringListAdapter.toJson(list)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrBlank()) return emptyList()
        return stringListAdapter.fromJson(value) ?: emptyList()
    }
}
