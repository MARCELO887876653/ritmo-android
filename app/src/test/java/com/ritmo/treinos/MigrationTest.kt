package com.ritmo.treinos

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.ritmo.treinos.data.RitmoDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class MigrationTest {
    @Test fun migrate104Schema2To110PreservesActiveAndHistoricalData(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "migration-104-110.db"; context.deleteDatabase(name)
        val path=context.getDatabasePath(name); path.parentFile!!.mkdirs()
        val schema=JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("schema2.json")).bufferedReader().readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path,null).use { old ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) { val e=entities.getJSONObject(i); old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName"))); val indices=e.optJSONArray("indices") ?: org.json.JSONArray(); for(x in 0 until indices.length()) old.execSQL(indices.getJSONObject(x).getString("createSql").replace("\${TABLE_NAME}",e.getString("tableName"))) }
            val setup=schema.getJSONArray("setupQueries"); for(i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO exercises VALUES(1,'Supino','supino','nota antiga',1)")
            old.execSQL("INSERT INTO templates VALUES(1,'Treino A')")
            old.execSQL("INSERT INTO template_exercises VALUES(1,1,0)")
            old.execSQL("INSERT INTO workout_sessions VALUES(1,1,'A',1000,2000),(2,1,'A',3000,NULL)")
            old.execSQL("INSERT INTO exercise_sessions VALUES(1,1,1,'Supino',0,'histórico'),(2,2,1,'Supino',0,'ativo')")
            old.execSQL("INSERT INTO exercise_sets VALUES(1,1,1,32.5,10,1),(2,2,1,34.0,9,1)")
            old.version=2
        }
        val db=RitmoDatabase.open(context,name)
        assertEquals(2,db.dao().workouts().size); assertEquals(2L,db.dao().active()!!.id)
        assertTrue(db.dao().exercises().single().archived); assertEquals("nota antiga",db.dao().exercises().single().notes)
        assertEquals(listOf(32.5,34.0),db.dao().sets().map{it.weight}); assertEquals(listOf("histórico","ativo"),db.dao().exerciseSessions().map{it.notes})
        assertTrue(db.dao().workouts().all {it.rankingOwnerId==null&&it.rankingEventId==null}); assertTrue(db.rankingDao().pending("any").isEmpty())
        db.close(); context.deleteDatabase(name)
    }
    @Test fun migrateRealSchema1To2KeepsAllWorkoutData(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "migration-test.db"; context.deleteDatabase(name)
        val path = context.getDatabasePath(name); path.parentFile!!.mkdirs()
        val schema = JSONObject(requireNotNull(javaClass.classLoader!!.getResourceAsStream("schema1.json")).bufferedReader().readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) { val entity = entities.getJSONObject(i); old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))); val indices = (entity.optJSONArray("indices") ?: org.json.JSONArray()); for (x in 0 until indices.length()) old.execSQL(indices.getJSONObject(x).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName"))) }
            val setup = schema.getJSONArray("setupQueries"); for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO exercises(id,name,normalizedName,notes) VALUES(1,'Supino reto','supino reto','nota')")
            old.execSQL("INSERT INTO templates(id,name) VALUES(1,'Treino A')")
            old.execSQL("INSERT INTO template_exercises(templateId,exerciseId,position) VALUES(1,1,0)")
            old.execSQL("INSERT INTO workout_sessions(id,templateId,name,startedAt,endedAt) VALUES(1,1,'Treino A',1000,2000)")
            old.execSQL("INSERT INTO exercise_sessions(id,workoutSessionId,exerciseId,name,position,notes) VALUES(1,1,1,'Supino reto',0,'registro')")
            old.execSQL("INSERT INTO exercise_sets(id,exerciseSessionId,number,weight,reps,completed) VALUES(1,1,1,32.0,10,1)")
            old.version = 1
        }
        val migrated = RitmoDatabase.open(context, name)
        assertEquals(1, migrated.dao().exercises().size); assertFalse(migrated.dao().exercises().single().archived)
        assertEquals("nota", migrated.dao().exercises().single().notes)
        assertEquals(32.0, migrated.dao().sets().single().weight, 0.0); assertEquals(10, migrated.dao().sets().single().reps)
        assertEquals(1, migrated.dao().workouts().size); assertEquals("registro", migrated.dao().exerciseSessions().single().notes)
        migrated.close(); context.deleteDatabase(name)
    }
}
