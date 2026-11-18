package com.craxiom.networksurvey.db

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.craxiom.networksurvey.model.SurveyEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SurveyTaskStoreAndroidTest {
    @Test fun taskLifecycleKeepsCapturedResultsAndSurvivesReopen() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "task-test-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = base.getDatabasePath(prefix + name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?) =
                base.openOrCreateDatabase(prefix + name, mode, factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,
                                             errorHandler: android.database.DatabaseErrorHandler?) =
                base.openOrCreateDatabase(prefix + name, mode, factory, errorHandler)
        }
        val constructor = SurveyTaskStore::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        var store = constructor.newInstance(context)
        try {
            val old = store.ensure()
            assertEquals(old.id, store.ensure().id)
            store.bindMission(old.id, "mission")
            store.bindMission(old.id, "mission")
            val next = store.start("next")
            store.append(SurveyEvent("late", old.id, System.currentTimeMillis(), 1, "clock",
                "speedtest", "speed_result", "late result"))
            assertTrue(store.events(old.id).any { it.id == "late" })
            assertFalse(store.events(next.id).any { it.id == "late" })
            assertEquals(1, store.taskMetadata(old.id).getJSONArray("mission_ids").length())
            store.close()
            store = constructor.newInstance(context)
            assertEquals(next.id, store.active.value?.id)
            store.finish()
            assertNull(store.observationTask())
            store.close()
            store = constructor.newInstance(context)
            assertNull(store.observationTask())
            assertNotEquals(next.id, store.ensure().id)
        } finally {
            store.close()
            android.database.sqlite.SQLiteDatabase.deleteDatabase(context.getDatabasePath("survey_tasks.db"))
        }
    }
}
