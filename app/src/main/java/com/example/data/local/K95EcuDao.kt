package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface K95EcuDao {
    @Query("SELECT * FROM k95_ecu ORDER BY commonName, id")
    fun observeAll(): Flow<List<K95EcuEntity>>

    @Query("SELECT * FROM k95_ecu WHERE id = :id")
    suspend fun getById(id: String): K95EcuEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: K95EcuEntity)

    // Source refresh must never replace a previously recorded physical observation.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCandidates(entities: List<K95EcuEntity>)
}
