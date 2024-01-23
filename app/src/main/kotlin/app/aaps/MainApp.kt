package app.aaps

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.database.persistence.CompatDBHelper
import app.aaps.di.DaggerAppComponent
import app.aaps.implementation.lifecycle.ProcessLifecycleListener
import app.aaps.implementation.plugin.PluginStore
import app.aaps.implementation.receivers.NetworkChangeReceiver
import app.aaps.plugins.aps.openAPSAIMI.StepService
import app.aaps.plugins.aps.utils.StaticInjector
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.themes.ThemeSwitcherPlugin
import app.aaps.receivers.BTReceiver
import app.aaps.receivers.ChargingStateReceiver
import app.aaps.receivers.KeepAliveWorker
import app.aaps.receivers.TimeDateOrTZChangeReceiver
import app.aaps.ui.activityMonitor.ActivityMonitor
import app.aaps.ui.widget.Widget
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import rxdogtag2.RxDogTag
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

class MainApp : DaggerApplication() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var pluginStore: PluginStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var versionCheckersUtils: VersionCheckerUtils
    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var config: Config
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var plugins: List<@JvmSuppressWildcards PluginBase>
    @Inject lateinit var compatDBHelper: CompatDBHelper
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Suppress("unused") @Inject lateinit var staticInjector: StaticInjector// better avoid, here fake only to initialize
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var processLifecycleListener: Provider<ProcessLifecycleListener>
    @Inject lateinit var themeSwitcherPlugin: ThemeSwitcherPlugin
    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var rh: Provider<ResourceHelper>

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshWidget: Runnable
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate() {
        super.onCreate()

        aapsLogger.debug("onCreate")
        aapsLogger.debug("onCreate - début")
        copyModelToInternalStorage(this)
        aapsLogger.debug("onCreate - après copyModelToFileSystem")
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleListener.get())
        scope.launch {
            RxDogTag.install()
            setRxErrorHandler()
            LocaleHelper.update(this@MainApp)

            var gitRemote: String? = config.REMOTE
            var commitHash: String? = BuildConfig.HEAD
            if (gitRemote?.contains("NoGitSystemAvailable") == true) {
                gitRemote = null
                commitHash = null
            }
            disposable += compatDBHelper.dbChangeDisposable()
            registerActivityLifecycleCallbacks(activityMonitor)
            runOnUiThread { themeSwitcherPlugin.setThemeMode() }
            aapsLogger.debug("Version: " + config.VERSION_NAME)
            aapsLogger.debug("BuildVersion: " + config.BUILD_VERSION)
            aapsLogger.debug("Remote: " + config.REMOTE)
            registerLocalBroadcastReceiver()

            // trigger here to see the new version on app start after an update
            versionCheckersUtils.triggerCheckVersion()

            // Register all tabs in app here
            pluginStore.plugins = plugins
            configBuilder.initialize()

            // delayed actions to make rh context updated for translations
            handler.postDelayed(
                {
                    // check if identification is set
                    if (config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank())
                        notificationStore.add(Notification(Notification.IDENTIFICATION_NOT_SET, rh.get().gs(R.string.identification_not_set), Notification.INFO))
                    // log version
                    disposable += persistenceLayer.insertVersionChangeIfChanged(config.VERSION_NAME, BuildConfig.VERSION_CODE, gitRemote, commitHash).subscribe()
                    // log app start
                    if (sp.getBoolean(app.aaps.plugins.sync.R.string.key_ns_log_app_started_event, config.APS))
                        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                            therapyEvent = TE(
                                timestamp = dateUtil.now(),
                                type = TE.Type.NOTE,
                                note = rh.get().gs(app.aaps.core.ui.R.string.androidaps_start) + " - " + Build.MANUFACTURER + " " + Build.MODEL,
                                glucoseUnit = GlucoseUnit.MGDL
                            ),
                            action = Action.START_AAPS,
                            source = Sources.Aaps, note = "", listValues = listOf()
                        ).subscribe()
                }, 10000
            )
            WorkManager.getInstance(this@MainApp).enqueueUniquePeriodicWork(
                KeepAliveWorker.KA_0,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequest.Builder(KeepAliveWorker::class.java, 15, TimeUnit.MINUTES)
                    .setInputData(Data.Builder().putString("schedule", KeepAliveWorker.KA_0).build())
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build()
            )
            localAlertUtils.shortenSnoozeInterval()
            localAlertUtils.preSnoozeAlarms()
            doMigrations()

            //  schedule widget update
            refreshWidget = Runnable {
                handler.postDelayed(refreshWidget, 60000)
                Widget.updateWidget(this@MainApp, "ScheduleEveryMin")
            }
            handler.postDelayed(refreshWidget, 60000)
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            sensorManager.registerListener(StepService, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
            config.appInitialized = true
        }
    }
    private fun copyModelToInternalStorage(context: Context) {
        aapsLogger.debug("copyModelToInternalStorage - début")
        try {
            val assetManager = context.assets
            aapsLogger.debug("copyModelToInternalStorage - assetManager : $assetManager")

            // Copie de model.tflite
            val inputStreamModel = assetManager.open("model.tflite")
            aapsLogger.debug("copyModelToInternalStorage - inputStreamModel : $inputStreamModel")
            val externalFileModel = File(Environment.getExternalStorageDirectory().absolutePath + "/AAPS/ml", "model.tflite")
            externalFileModel.parentFile?.mkdirs() // Crée le dossier s'il n'existe pas
            val outputStreamModel = FileOutputStream(externalFileModel)
            inputStreamModel.copyTo(outputStreamModel)
            inputStreamModel.close()
            outputStreamModel.close()
            aapsLogger.debug("copyModelToInternalStorage - file.absolutePath : ${externalFileModel.absolutePath}")
            Log.d("ModelCopy", "Fichier 'model.tflite' copié dans ${externalFileModel.absolutePath}")

            // Copie de modelUAM.tflite
            val inputStreamUAM = assetManager.open("modelUAM.tflite")
            aapsLogger.debug("copyModelToInternalStorage - inputStreamUAM : $inputStreamUAM")
            val externalFileUAM = File(Environment.getExternalStorageDirectory().absolutePath + "/AAPS/ml", "modelUAM.tflite")
            externalFileUAM.parentFile?.mkdirs() // Crée le dossier s'il n'existe pas
            val outputStreamUAM = FileOutputStream(externalFileUAM)
            inputStreamUAM.copyTo(outputStreamUAM)
            inputStreamUAM.close()
            outputStreamUAM.close()
            aapsLogger.debug("copyModelToInternalStorage - file.absolutePath : ${externalFileUAM.absolutePath}")
            Log.d("ModelCopy", "Fichier 'modelUAM.tflite' copié dans ${externalFileUAM.absolutePath}")

        } catch (e: Exception) {
            Log.e("ModelCopyError", "Erreur lors de la copie: ${e.message}")
        }
    }

    /*private fun copyModelToInternalStorage(context: Context) {
        aapsLogger.debug("copyModelToInternalStorage - début")
        try {
            val assetManager = context.assets
            aapsLogger.debug("copyModelToInternalStorage - assetManager : $assetManager")
            val inputStream = assetManager.open("model.tflite")
            aapsLogger.debug("copyModelToInternalStorage - inputStream : $inputStream")
            val externalFile = File(Environment.getExternalStorageDirectory().absolutePath + "/AAPS/ml", "model.tflite")
            val assetsList = context.assets.list("") // "" pour le dossier racine de assets
            aapsLogger.debug("copyModelToInternalStorage - assetsList : $assetsList")
            // Crée le dossier 'aaps/ml' s'il n'existe pas
            externalFile.parentFile?.mkdirs()
            val outputStream = FileOutputStream(externalFile)
            aapsLogger.debug("copyModelToInternalStorage - outputStream : $outputStream")
            inputStream.copyTo(outputStream)

            inputStream.close()
            outputStream.close()
            aapsLogger.debug("copyModelToInternalStorage - file.absolutePath : ${externalFile.absolutePath}")
            Log.d("ModelCopy", "Fichier 'model.tflite' copié dans ${externalFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("ModelCopyError", "Erreur lors de la copie: ${e.message}")
        }
    }*/
    private fun setRxErrorHandler() {
        RxJavaPlugins.setErrorHandler { t: Throwable ->
            var e = t
            if (e is UndeliverableException) {
                e = e.cause!!
            }
            if (e is IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return@setErrorHandler
            }
            if (e is InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return@setErrorHandler
            }
            if (e is NullPointerException || e is IllegalArgumentException) {
                // that's likely a bug in the application
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            if (e is IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), e)
                return@setErrorHandler
            }
            aapsLogger.warn(LTag.CORE, "Undeliverable exception received, not sure what to do", e.localizedMessage)
        }
    }

    @Suppress("SpellCheckingInspection")
    private fun doMigrations() {
        // set values for different builds
        if (!sp.contains(R.string.key_ns_alarms)) sp.putBoolean(R.string.key_ns_alarms, config.NSCLIENT)
        if (!sp.contains(R.string.key_ns_announcements)) sp.putBoolean(R.string.key_ns_announcements, config.NSCLIENT)
        // 3.1.0
        if (sp.contains("ns_wifionly")) {
            if (sp.getBoolean("ns_wifionly", false)) {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_cellular, false)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_wifi, true)
            } else {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_cellular, true)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_wifi, false)
            }
            sp.remove("ns_wifionly")
        }
        if (sp.contains("ns_charginonly")) {
            if (sp.getBoolean("ns_charginonly", false)) {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_battery, false)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_charging, true)
            } else {
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_battery, true)
                sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_charging, true)
            }
            sp.remove("ns_charginonly")
        }
        if (!sp.contains(app.aaps.plugins.sync.R.string.key_ns_log_app_started_event))
            sp.putBoolean(app.aaps.plugins.sync.R.string.key_ns_log_app_started_event, config.APS)
        if (preferences.getIfExists(StringKey.MaintenanceEmail) == "logs@androidaps.org")
            preferences.put(StringKey.MaintenanceEmail, "logs@aaps.app")
        // fix values for theme switching
        sp.putString(app.aaps.plugins.main.R.string.value_dark_theme, "dark")
        sp.putString(app.aaps.plugins.main.R.string.value_light_theme, "light")
        sp.putString(app.aaps.plugins.main.R.string.value_system_theme, "system")
        // 3.3
        if (preferences.get(IntKey.OverviewEatingSoonDuration) == 0) preferences.remove(IntKey.OverviewEatingSoonDuration)
        if (preferences.get(UnitDoubleKey.OverviewEatingSoonTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewEatingSoonTarget)
        if (preferences.get(IntKey.OverviewActivityDuration) == 0) preferences.remove(IntKey.OverviewActivityDuration)
        if (preferences.get(UnitDoubleKey.OverviewActivityTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewActivityTarget)
        if (preferences.get(IntKey.OverviewHypoDuration) == 0) preferences.remove(IntKey.OverviewHypoDuration)
        if (preferences.get(UnitDoubleKey.OverviewHypoTarget) == 0.0) preferences.remove(UnitDoubleKey.OverviewHypoTarget)
        if (preferences.get(UnitDoubleKey.OverviewLowMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewLowMark)
        if (preferences.get(UnitDoubleKey.OverviewHighMark) == 0.0) preferences.remove(UnitDoubleKey.OverviewHighMark)
        if (preferences.getIfExists(BooleanKey.GeneralSimpleMode) == null)
            preferences.put(BooleanKey.GeneralSimpleMode, !preferences.get(BooleanKey.GeneralSetupWizardProcessed))
        // convert Double to Int
        if (preferences.getIfExists(IntKey.ApsDynIsfAdjustmentFactor) != null)
            preferences.put(IntKey.ApsDynIsfAdjustmentFactor, preferences.get(IntKey.ApsDynIsfAdjustmentFactor))
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerAppComponent
            .builder()
            .application(this)
            .build()
    }

    private fun registerLocalBroadcastReceiver() {
        var filter = IntentFilter()
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(TimeDateOrTZChangeReceiver(), filter)
        filter = IntentFilter()
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        registerReceiver(NetworkChangeReceiver(), filter)
        filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(ChargingStateReceiver(), filter)
        filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(BTReceiver(), filter)
    }

    override fun onTerminate() {
        aapsLogger.debug(LTag.CORE, "onTerminate")
        unregisterActivityLifecycleCallbacks(activityMonitor)
        uiInteraction.stopAlarm("onTerminate")
        super.onTerminate()
    }
}
