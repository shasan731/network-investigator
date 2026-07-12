package com.shasan731.networkinvestigator.di

import android.content.Context
import androidx.room.Room
import com.shasan731.networkinvestigator.core.database.*
import com.shasan731.networkinvestigator.core.datastore.AppPreferences
import com.shasan731.networkinvestigator.core.diagnostics.InvestigationEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): NetworkInvestigatorDatabase = Room.databaseBuilder(context, NetworkInvestigatorDatabase::class.java, "network-investigator.db").addMigrations(NetworkInvestigatorDatabase.MIGRATION_1_2).build()
    @Provides fun investigationDao(db: NetworkInvestigatorDatabase) = db.investigationDao()
    @Provides fun incidentDao(db: NetworkInvestigatorDatabase) = db.incidentDao()
    @Provides fun connectivityDao(db: NetworkInvestigatorDatabase) = db.connectivityDao()
    @Provides fun wifiDao(db: NetworkInvestigatorDatabase) = db.wifiDao()
    @Provides fun lanDeviceDao(db: NetworkInvestigatorDatabase) = db.lanDeviceDao()
    @Provides fun maintenanceDao(db: NetworkInvestigatorDatabase) = db.maintenanceDao()
    @Provides fun savedRequestDao(db: NetworkInvestigatorDatabase) = db.savedRequestDao()
    @Provides fun userNoteDao(db: NetworkInvestigatorDatabase) = db.userNoteDao()
    @Provides @Singleton fun incidentRepository(incidentDao: IncidentDao, investigationDao: InvestigationDao) = IncidentRepository(incidentDao, investigationDao)
    @Provides @Singleton fun retentionRepository(dao: MaintenanceDao) = RetentionRepository(dao)
    @Provides @Singleton fun repository(dao: InvestigationDao) = InvestigationRepository(dao)
    @Provides @Singleton fun preferences(@ApplicationContext context: Context) = AppPreferences(context)
    @Provides fun engine() = InvestigationEngine()
}
