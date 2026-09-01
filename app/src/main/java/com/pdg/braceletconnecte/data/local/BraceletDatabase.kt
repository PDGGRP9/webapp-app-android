package com.pdg.braceletconnecte.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MeasurementEntity::class], version = 1, exportSchema = false)
abstract class BraceletDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        fun build(context: Context): BraceletDatabase =
            Room.databaseBuilder(context, BraceletDatabase::class.java, "bracelet.db")
                // Student project: on a schema change we start from scratch
                // rather than writing migrations. Measurements already sent to
                // the backend are not lost anyway.
                .fallbackToDestructiveMigration()
                .build()
    }
}
