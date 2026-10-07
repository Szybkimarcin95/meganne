package com.example.data.repository

import android.content.Context
import com.example.data.model.K95ServiceMap
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class K95ServiceMapRepository(context: Context) {
    private val appContext = context.applicationContext

    private val adapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(K95ServiceMap::class.java)

    fun load(): K95ServiceMap? = runCatching {
        appContext.assets
            .open("service-map/k95-service-map.json")
            .bufferedReader()
            .use { reader -> adapter.fromJson(reader.readText()) }
    }.getOrNull()
}
