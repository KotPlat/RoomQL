package com.roomql.demo.data

import android.content.Context
import androidx.room.Room

/**
 * Single database instance for the demo, seeded on first use.
 *
 * A real app would inject this; the demo keeps it deliberately plain so the RoomQL parts
 * stay the interesting thing on screen.
 */
object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

    private fun build(context: Context): AppDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "catalogue.db",
        ).build()

    /** Populates the catalogue if it is empty. Safe to call on every launch. */
    fun seedIfEmpty(db: AppDatabase) {
        if (db.productDao().count() == 0) {
            CatalogueSeed.populate(db)
        }
    }
}
