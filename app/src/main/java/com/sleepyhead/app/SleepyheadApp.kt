package com.sleepyhead.app

import android.app.Application
import com.sleepyhead.app.data.Store

class SleepyheadApp : Application() {
    lateinit var store: Store
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
    }
}
