package com.ritmo.treinos.online

import android.content.Context
import androidx.work.*
import com.ritmo.treinos.RitmoApplication
import com.ritmo.treinos.data.RitmoDatabase
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RankingSynchronizer(private val db: RitmoDatabase, private val online: OnlineService) {
    suspend fun sync(): Boolean {
        if(!online.configured || online.account.value==null || online.recovery.value) return true
        online.loadProfile()
        val owner=online.rankingOwner() ?: return true
        for(event in db.rankingDao().pending(owner)) {
            if(online.rankingOwner()!=owner) break
            try {
                val xp=online.submit(event)
                db.rankingDao().update(event.copy(status="synced",awardedXp=xp,error=null))
                online.syncError.value=null
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                // Auth/server/network failures remain pending; malformed events cannot poison the queue.
                if(e is OnlineException && e.status in listOf(400,409,422) && e.code in listOf("invalid_time","invalid_event","overlapping_event","22023")) {
                    db.rankingDao().update(event.copy(status="rejected",error=e.message)); continue
                }
                online.syncError.value=e.message ?: "Sem conexão. O treino permanece pendente."
                return false
            }
        }
        online.loadProfile()
        return true
    }
}
class RankingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val app=applicationContext as RitmoApplication
        return try { if(app.rankingSync.sync()) Result.success() else Result.retry() }
        catch(e: Exception) { if(e is CancellationException) throw e; app.online.syncError.value="Sincronização pendente. Entre novamente ou confira a conexão."; Result.retry() }
    }
}
object RankingWork {
    fun schedule(context: Context) {
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork("ritmo-ranking-sync",ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<RankingWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
    }
    fun periodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("ritmo-ranking-periodic",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RankingWorker>(15,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork("ritmo-ranking-sync"); WorkManager.getInstance(context).cancelUniqueWork("ritmo-ranking-periodic") }
}
