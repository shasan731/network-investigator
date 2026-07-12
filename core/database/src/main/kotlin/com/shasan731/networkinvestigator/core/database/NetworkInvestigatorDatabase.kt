package com.shasan731.networkinvestigator.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TargetEntity::class, InvestigationEntity::class, DiagnosticTaskEntity::class, DnsResultEntity::class,
        ReachabilityResultEntity::class, TracerouteResultEntity::class, HttpResultEntity::class, TlsResultEntity::class,
        PortResultEntity::class, LanDeviceEntity::class, WifiMeasurementEntity::class, ConnectivitySessionEntity::class,
        ConnectivitySampleEntity::class, IncidentEntity::class, IncidentInvestigationCrossRef::class, AttachmentEntity::class,
        SavedRequestEntity::class, ProviderCacheEntity::class, UserNoteEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(DatabaseConverters::class)
abstract class NetworkInvestigatorDatabase : RoomDatabase() {
    abstract fun investigationDao(): InvestigationDao
    abstract fun incidentDao(): IncidentDao
    abstract fun connectivityDao(): ConnectivityDao
    abstract fun wifiDao(): WifiDao
    abstract fun lanDeviceDao(): LanDeviceDao
    abstract fun providerCacheDao(): ProviderCacheDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun savedRequestDao(): SavedRequestDao
    abstract fun userNoteDao(): UserNoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ProviderCacheEntity_expiresAt ON ProviderCacheEntity(expiresAt)")
            }
        }
    }
}
