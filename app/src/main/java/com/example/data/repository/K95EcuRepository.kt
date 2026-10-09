package com.example.data.repository

import com.example.data.local.K95EcuDao
import com.example.data.local.K95EcuMapper
import com.example.data.model.K95Ecu
import com.example.data.model.K95EcuStatus
import kotlinx.coroutines.flow.map

class K95EcuRepository(private val dao: K95EcuDao, private val mapper: K95EcuMapper = K95EcuMapper()) {
    val inventory = dao.observeAll().map { rows -> rows.map(mapper::toDomain) }

    suspend fun importCandidates(ecus: List<K95Ecu>) {
        require(ecus.all { it.status == K95EcuStatus.DATABASE_CANDIDATE || it.status == K95EcuStatus.UNVERIFIED })
        dao.insertCandidates(ecus.map(mapper::toEntity))
    }

    suspend fun save(ecu: K95Ecu) { dao.upsert(mapper.toEntity(ecu)) }
    suspend fun getById(id: String): K95Ecu? = dao.getById(id)?.let(mapper::toDomain)
}
