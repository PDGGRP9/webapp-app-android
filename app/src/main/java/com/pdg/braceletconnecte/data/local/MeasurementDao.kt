package com.pdg.braceletconnecte.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {

    /**
     * Returns one row id per inserted measurement, or -1 for those that already
     * existed. That -1 is what lets the logs report how many measurements were
     * duplicates.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(measurements: List<MeasurementEntity>): List<Long>

    @Query("SELECT * FROM measurements WHERE sent = 0 ORDER BY ts ASC LIMIT :limit")
    suspend fun pendingToUpload(limit: Int): List<MeasurementEntity>

    @Query("UPDATE measurements SET sent = 1 WHERE deviceUid = :deviceUid AND ts = :ts")
    suspend fun markSent(deviceUid: String, ts: Long)

    @Query("SELECT COUNT(*) FROM measurements WHERE sent = 0")
    fun pendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM measurements")
    suspend fun total(): Int
}
