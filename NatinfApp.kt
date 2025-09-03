package com.natinf.searchpro

import android.app.Application
import com.natinf.searchpro.data.AppCtx

class NatinfApp: Application() {
    override fun onCreate() {
        super.onCreate()
        AppCtx.appContext = applicationContext
    }
}
