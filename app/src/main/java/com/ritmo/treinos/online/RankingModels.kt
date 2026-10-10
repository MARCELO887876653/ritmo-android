package com.ritmo.treinos.online

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// No workout foreign key: selective history deletion cannot cause retries with new IDs.
@Entity(tableName = "ranking_events", indices = [Index(value = ["ownerId", "status"])])
data class RankingEvent(@PrimaryKey val eventId: String, val ownerId: String, val startedAt: Long,
    val completedAt: Long, val status: String = "pending", val awardedXp: Int = 0, val error: String? = null)
@Dao interface RankingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(event: RankingEvent)
    @Query("SELECT * FROM ranking_events WHERE ownerId=:owner ORDER BY completedAt,eventId") fun observe(owner: String): Flow<List<RankingEvent>>
    @Query("SELECT * FROM ranking_events WHERE ownerId=:owner AND status='pending' ORDER BY completedAt,eventId") suspend fun pending(owner: String): List<RankingEvent>
    @Update suspend fun update(event: RankingEvent)
    @Query("DELETE FROM ranking_events WHERE ownerId=:owner") suspend fun clear(owner: String)
}
data class Account(val id: String, val email: String)
data class OnlineProfile(val nickname: String, val enabled: Boolean)
data class RankingEntry(val position: Long, val nickname: String, val xp: Long, val level: Long)
data class RankingBoard(val entries: List<RankingEntry> = emptyList(), val me: RankingEntry? = null, val updatedAt: String = "")
data class XpSummary(val total: Long = 0, val today: Long = 0, val activeDays: Long = 0, val workouts: Long = 0) {
    val level get() = 1 + total / 500
    val badges get() = listOf("Primeiro passo" to (workouts >= 1), "5 dias no seu ritmo" to (activeDays >= 5), "10 dias de constância" to (activeDays >= 10), "Nível 5" to (level >= 5))
}
class OnlineException(val status: Int, val code: String, message: String) : Exception(message)
