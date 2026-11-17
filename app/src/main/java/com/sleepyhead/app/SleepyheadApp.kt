package com.sleepyhead.app

import android.app.Application
import com.sleepyhead.app.data.Store
import com.sleepyhead.app.travel.TravelCheck
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SleepyheadApp : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        // Keep the lock-start alarm in step with the settings.
        MainScope().launch {
            store.data.map { it.settings }.distinctUntilChanged().collect { TravelCheck.scheduleNext(this@SleepyheadApp, it) }
        }
    }
}
