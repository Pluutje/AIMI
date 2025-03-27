package app.aaps.plugins.aps.openAPS

import android.text.Spanned
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfile
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.aps.Predictions
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.convertedToPercent
import app.aaps.core.ui.R
import app.aaps.core.utils.HtmlHelper
import dagger.android.HasAndroidInjector
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * Created by mike on 09.06.2016.
 */
open class APSResultObject(protected val injector: HasAndroidInjector) : APSResult {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var processedTbrEbData: ProcessedTbrEbData
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var decimalFormatter: DecimalFormatter

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    override var date: Long = 0
    override var reason: String = ""
    override var rate = -1.0
    override var percent = 0
    override var usePercent = false
    override var duration = -1
    override var isTempBasalRequested = false
    override var iob: IobTotal? = null

    //override var json: JSONObject? = JSONObject()
    override var hasPredictions = false
    override var smb = 0.0 // super micro bolus in units
    override var deliverAt: Long = 0


    override var targetBG = 0.0
    override var carbsReq = 0
    override var carbsReqWithin = 0
    override var variableSens: Double? = null
    override var isfMgdlForCarbs: Double? = null
    override var inputConstraints: Constraint<Double>? = null
    override var rateConstraint: Constraint<Double>? = null
    override var percentConstraint: Constraint<Int>? = null
    override var smbConstraint: Constraint<Double>? = null

    // Added only to compile
    override var algorithm: APSResult.Algorithm = APSResult.Algorithm.UNKNOWN
    override var scriptDebug: List<String>? = null
    override var iobData: Array<IobTotal>? = null
    override var glucoseStatus: GlucoseStatus? = null
    override var currentTemp: CurrentTemp? = null
    override var oapsProfile: OapsProfile? = null
    override var oapsProfileAutoIsf: OapsProfileAutoIsf? = null
    override var mealData: MealData? = null
    override var autosensResult: AutosensResult? = null

    override fun predictions(): Predictions? = null
    override fun rawData(): Any = Object()

    override val carbsRequiredText: String
        get() = rh.gs(R.string.carbsreq, carbsReq, carbsReqWithin)

    override fun resultAsString(): String {
        val pump = activePlugin.activePump
        if (isChangeRequested) {
            // rate
            var ret: String = if (rate == 0.0 && duration == 0) "${rh.gs(R.string.cancel_temp)} "
            else if (rate == -1.0) "${rh.gs(R.string.let_temp_basal_run)}\n"
            else if (usePercent) "${rh.gs(R.string.rate)}: ${decimalFormatter.to2Decimal(percent.toDouble())}% (${decimalFormatter.to2Decimal(percent * pump.baseBasalRate / 100.0)} U/h) " +
                "${rh.gs(R.string.duration)}: ${decimalFormatter.to2Decimal(duration.toDouble())} min "
            else "${rh.gs(R.string.rate)}: ${decimalFormatter.to2Decimal(rate)} U/h (${decimalFormatter.to2Decimal(rate / pump.baseBasalRate * 100)}%) " +
                "${rh.gs(R.string.duration)}: ${decimalFormatter.to2Decimal(duration.toDouble())} min "
            // smb
            if (smb != 0.0) ret += "SMB: ${decimalFormatter.toPumpSupportedBolus(smb, activePlugin.activePump.pumpDescription.bolusStep)} "
            if (isCarbsRequired) {
                ret += "$carbsRequiredText "
            }

            // reason
            ret += rh.gs(R.string.reason) + ": " + reason
            return ret
        }
        return if (isCarbsRequired) {
            carbsRequiredText
        } else rh.gs(R.string.nochangerequested)
    }
    /*fun updateAPSResult(delta: Float, bg: Float, basalaimi: Float, targetBG: Double) {
        val newRate = round(basalaimi.toDouble() * delta)
        val newDuration = 30
        if (delta <= 0.0f && bg <= 140.0f) {
            this.rate = 0.0
            this.duration = newDuration
            this.isTempBasalRequested = true
            this.isChangeRequested
        } else if (delta > 0.0f && bg > 80) {
            this.rate = newRate.toDouble()
            this.duration = newDuration
            this.isTempBasalRequested = true
            this.isChangeRequested
        }
        this.targetBG = targetBG
    }*/

    override fun resultAsSpanned(): Spanned {
        val pump = activePlugin.activePump
        if (isChangeRequested) {
            // rate
            var ret: String =
                if (rate == 0.0 && duration == 0) rh.gs(R.string.cancel_temp) + "<br>"
                else if (rate == -1.0) rh.gs(R.string.let_temp_basal_run) + "<br>"
                else if (usePercent) "<b>" + rh.gs(R.string.rate) + "</b>: " + decimalFormatter.to2Decimal(percent.toDouble()) + "% " +
                    "(" + decimalFormatter.to2Decimal(percent * pump.baseBasalRate / 100.0) + " U/h)<br>" +
                    "<b>" + rh.gs(R.string.duration) + "</b>: " + decimalFormatter.to2Decimal(duration.toDouble()) + " min<br>"
                else "<b>" + rh.gs(R.string.rate) + "</b>: " + decimalFormatter.to2Decimal(rate) + " U/h " +
                    "(" + decimalFormatter.to2Decimal(rate / pump.baseBasalRate * 100.0) + "%) <br>" +
                    "<b>" + rh.gs(R.string.duration) + "</b>: " + decimalFormatter.to2Decimal(duration.toDouble()) + " min<br>"

            // smb
            if (smb != 0.0) ret += "<b>" + "SMB" + "</b>: " + decimalFormatter.toPumpSupportedBolus(smb, activePlugin.activePump.pumpDescription.bolusStep) + "<br>"
            if (isCarbsRequired) {
                ret += "$carbsRequiredText<br>"
            }

            // reason
            ret += "<b>" + rh.gs(R.string.reason) + "</b>: " + reason.replace("<", "&lt;").replace(">", "&gt;")
            return HtmlHelper.fromHtml(ret)
        }
        return if (isCarbsRequired) {
            HtmlHelper.fromHtml(carbsRequiredText)
        } else HtmlHelper.fromHtml(rh.gs(R.string.nochangerequested))
    }

    override fun newAndClone(): APSResult {
        val newResult = APSResultObject(injector)
        doClone(newResult)
        return newResult
    }

    fun doClone(newResult: APSResult) {
        newResult.date = date
        newResult.reason = reason
        newResult.rate = rate
        newResult.duration = duration
        newResult.isTempBasalRequested = isTempBasalRequested
        //newResult.iob = iob
        //newResult.json = JSONObject(json.toString())
        newResult.hasPredictions = hasPredictions
        newResult.smb = smb
        newResult.deliverAt = deliverAt
        newResult.rateConstraint = rateConstraint
        newResult.smbConstraint = smbConstraint
        newResult.percent = percent
        newResult.usePercent = usePercent
        newResult.carbsReq = carbsReq
        newResult.carbsReqWithin = carbsReqWithin
        newResult.targetBG = targetBG
    }

    override fun json(): JSONObject? {
        val json = JSONObject()
        if (isChangeRequested) {
            json.put("rate", rate)
            json.put("duration", duration)
            json.put("reason", reason)
        }
        return json
    }

    override val predictionsAsGv: MutableList<GV>
        get() {
            val array: MutableList<GV> = ArrayList()
            val startTime = date

            val predictions = predictions()
            predictions?.IOB?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.IOB_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.aCOB?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.A_COB_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.COB?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.COB_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.UAM?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.UAM_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            predictions?.ZT?.let { iob ->
                for (i in 1 until iob.size) {
                    val gv = GV(
                        raw = 0.0,
                        noise = 0.0,
                        value = iob[i].toDouble(),
                        timestamp = startTime + i * 5 * 60 * 1000L,
                        sourceSensor = SourceSensor.ZT_PREDICTION,
                        trendArrow = TrendArrow.NONE
                    )
                    array.add(gv)
                }
            }
            return array
        }
    override val latestPredictionsTime: Long
        get() {
            var latest: Long = 0
            val startTime = date
            val predictions = predictions()
            predictions?.IOB?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.aCOB?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.COB?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.UAM?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            predictions?.ZT?.let { if (it.isNotEmpty()) latest = max(latest, startTime + (it.size - 1) * 5 * 60 * 1000L) }
            return latest
        }
    override val isChangeRequested: Boolean
        get() {
            val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()
            // closed loop mode: handle change at driver level
            if (closedLoopEnabled.value()) {
                aapsLogger.debug(LTag.APS, "DEFAULT: Closed mode")
                return isTempBasalRequested || isBolusRequested
            }

            // open loop mode: try to limit request
            if (!isTempBasalRequested && !isBolusRequested) {
                aapsLogger.debug(LTag.APS, "FALSE: No request")
                return false
            }
            val now = System.currentTimeMillis()
            val activeTemp = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
            val pump = activePlugin.activePump
            val profile = profileFunction.getProfile()
            if (profile == null) {
                aapsLogger.error("FALSE: No Profile")
                return false
            }
            return if (usePercent) {
                if (activeTemp == null && percent == 100) {
                    aapsLogger.debug(LTag.APS, "FALSE: No temp running, asking cancel temp")
                    return false
                }
                if (activeTemp != null && abs(percent - activeTemp.convertedToPercent(now, profile)) < pump.pumpDescription.basalStep) {
                    aapsLogger.debug(LTag.APS, "FALSE: Temp equal")
                    return false
                }
                // always report zero temp
                if (percent == 0) {
                    aapsLogger.debug(LTag.APS, "TRUE: Zero temp")
                    return true
                }
                // always report high temp
                if (pump.pumpDescription.tempBasalStyle == PumpDescription.PERCENT) {
                    val pumpLimit = pump.pumpDescription.pumpType.tbrSettings()?.maxDose ?: 0.0
                    if (percent.toDouble() == pumpLimit) {
                        aapsLogger.debug(LTag.APS, "TRUE: Pump limit")
                        return true
                    }
                }
                // report change bigger than 30%
                var percentMinChangeChange = preferences.get(IntKey.LoopOpenModeMinChange).toDouble()
                percentMinChangeChange /= 100.0
                val lowThreshold = 1 - percentMinChangeChange
                val highThreshold = 1 + percentMinChangeChange
                var change = percent / 100.0
                if (activeTemp != null) change = percent / activeTemp.convertedToPercent(now, profile).toDouble()
                if (change < lowThreshold || change > highThreshold) {
                    aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + change * 100.0 + "%")
                    true
                } else {
                    aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + change * 100.0 + "%")
                    false
                }
            } else {
                if (activeTemp == null && rate == pump.baseBasalRate) {
                    aapsLogger.debug(LTag.APS, "FALSE: No temp running, asking cancel temp")
                    return false
                }
                if (activeTemp != null && abs(rate - activeTemp.convertedToAbsolute(now, profile)) < pump.pumpDescription.basalStep) {
                    aapsLogger.debug(LTag.APS, "FALSE: Temp equal")
                    return false
                }
                // always report zero temp
                if (rate == 0.0) {
                    aapsLogger.debug(LTag.APS, "TRUE: Zero temp")
                    return true
                }
                // always report high temp
                if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
                    val pumpLimit = pump.pumpDescription.pumpType.tbrSettings()?.maxDose ?: 0.0
                    if (rate == pumpLimit) {
                        aapsLogger.debug(LTag.APS, "TRUE: Pump limit")
                        return true
                    }
                }
                // report change bigger than 30%
                var percentMinChangeChange = preferences.get(IntKey.LoopOpenModeMinChange).toDouble()
                percentMinChangeChange /= 100.0
                val lowThreshold = 1 - percentMinChangeChange
                val highThreshold = 1 + percentMinChangeChange
                var change = rate / profile.getBasal()
                if (activeTemp != null) change = rate / activeTemp.convertedToAbsolute(now, profile)
                if (change < lowThreshold || change > highThreshold) {
                    aapsLogger.debug(LTag.APS, "TRUE: Outside allowed range " + change * 100.0 + "%")
                    true
                } else {
                    aapsLogger.debug(LTag.APS, "TRUE: Inside allowed range " + change * 100.0 + "%")
                    false
                }
            }
        }
}