@file:Suppress("unused")

package co.sodalabs.pager.demo

import androidx.multidex.MultiDexApplication
import timber.log.Timber

class DemoApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())
    }
}