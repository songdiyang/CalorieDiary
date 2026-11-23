package com.calorie.diary.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.calorie.diary.data.seed.FoodSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FoodEntity::class, MealEntryEntity::class, WaterEntity::class,
        GroupEntity::class, GroupMemberEntity::class, MealTombstoneEntity::class,
        CachedFriendEntity::class, CachedFriendMessageEntity::class,
        CachedGroupEntity::class, CachedGroupMessageEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun waterDao(): WaterDao
    abstract fun groupDao(): GroupDao
    abstract fun mealTombstoneDao(): MealTombstoneDao
    abstract fun chatCacheDao(): ChatCacheDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `groups` (" +
                        "`groupId` TEXT NOT NULL, `name` TEXT NOT NULL, `ownerId` TEXT NOT NULL, " +
                        "`calorieGoal` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `group_members` (" +
                        "`groupId` TEXT NOT NULL, `userId` TEXT NOT NULL, `nickname` TEXT NOT NULL, " +
                        "`avatarEmoji` TEXT NOT NULL, `joinedAt` INTEGER NOT NULL, " +
                        "`lastActiveAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `userId`))"
                )
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN ownerName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN groupId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `game_wallet` (" +
                        "`id` INTEGER NOT NULL, `coins` INTEGER NOT NULL, " +
                        "`lastCheckInDate` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inventory_items` (" +
                        "`itemId` TEXT NOT NULL, `quantity` INTEGER NOT NULL, " +
                        "`acquiredAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO game_wallet (id, coins, lastCheckInDate) " +
                        "VALUES (1, 0, '')"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 云端游戏数据不再依赖本地表；旧表仅保留，避免破坏性删除。
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `water_logs_new` (" +
                        "`date` TEXT NOT NULL, `ml` INTEGER NOT NULL, " +
                        "`ownerId` TEXT NOT NULL DEFAULT '', " +
                        "PRIMARY KEY(`date`, `ownerId`))"
                )
                db.execSQL(
                    "INSERT INTO water_logs_new (date, ml, ownerId) " +
                        "SELECT date, ml, '' FROM water_logs"
                )
                db.execSQL("DROP TABLE water_logs")
                db.execSQL("ALTER TABLE water_logs_new RENAME TO water_logs")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 旧记录上传云端时使用的 client_ref 是 android:<id>，迁移时按同一规则回填。
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN clientRef TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE meal_entries SET clientRef = 'android:' || id")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `meal_tombstones` (" +
                        "`clientRef` TEXT NOT NULL, `ownerId` TEXT NOT NULL, PRIMARY KEY(`clientRef`))"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_friends` (" +
                        "`ownerId` TEXT NOT NULL, `userId` TEXT NOT NULL, `nickname` TEXT NOT NULL, " +
                        "`avatarEmoji` TEXT NOT NULL, `friendsSince` TEXT NOT NULL, " +
                        "`lastMessage` TEXT, `lastMessageAt` TEXT, `publicId` TEXT NOT NULL, " +
                        "`sparkCount` INTEGER NOT NULL, PRIMARY KEY(`ownerId`, `userId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_friend_messages` (" +
                        "`ownerId` TEXT NOT NULL, `messageId` INTEGER NOT NULL, `friendId` TEXT NOT NULL, " +
                        "`senderId` TEXT NOT NULL, `content` TEXT NOT NULL, `messageType` TEXT NOT NULL, " +
                        "`calories` INTEGER, `entryDate` TEXT, `itemsJson` TEXT NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, PRIMARY KEY(`ownerId`, `messageId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_friend_messages_owner_friend_time` " +
                        "ON `cached_friend_messages` (`ownerId`, `friendId`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_groups` (" +
                        "`ownerId` TEXT NOT NULL, `groupId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`groupOwnerId` TEXT NOT NULL, `calorieGoal` INTEGER NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, `lastMessage` TEXT, `lastMessageAt` TEXT, " +
                        "`sparkDays` INTEGER NOT NULL, PRIMARY KEY(`ownerId`, `groupId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_group_messages` (" +
                        "`ownerId` TEXT NOT NULL, `messageId` INTEGER NOT NULL, `groupId` TEXT NOT NULL, " +
                        "`senderId` TEXT NOT NULL, `content` TEXT NOT NULL, `messageType` TEXT NOT NULL, " +
                        "`calories` INTEGER, `entryDate` TEXT, `itemsJson` TEXT NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, PRIMARY KEY(`ownerId`, `messageId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_group_messages_owner_group_time` " +
                        "ON `cached_group_messages` (`ownerId`, `groupId`, `createdAt`)"
                )
            }
        }

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "calorie_diary.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
                    )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                get(context).foodDao().let { dao ->
                                    FoodSeed.foods.forEach { dao.insert(it) }
                                }
                            }
                        }
                    })
                    .build()
                    .also { instance = it }
            }
        }
    }
}
