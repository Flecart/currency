package io.github.currency.companion

import android.app.Application
import androidx.room.Room
import io.github.currency.companion.data.*

class CurrencyApplication : Application() {
    val repository by lazy {
        CurrencyRepository(Room.databaseBuilder(this, CurrencyDatabase::class.java, "currency.db").addMigrations(CurrencyDatabase.MIGRATION_1_2, CurrencyDatabase.MIGRATION_2_3, CurrencyDatabase.MIGRATION_3_4, CurrencyDatabase.MIGRATION_4_5).build())
    }
    val gateway by lazy { SttGateway(this) }
}
