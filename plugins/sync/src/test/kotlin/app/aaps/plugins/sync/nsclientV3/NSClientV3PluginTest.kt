package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.DS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.OE
import app.aaps.core.data.model.PS
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TB
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.plugins.sync.nsShared.StoreDataForDbImpl
import app.aaps.plugins.sync.nsclient.ReceiverDelegate
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.anyLong

@Suppress("SpellCheckingInspection")
internal class NSClientV3PluginTest : TestBaseWithProfile() {

    @Mock lateinit var receiverDelegate: ReceiverDelegate
    @Mock lateinit var dataSyncSelectorV3: DataSyncSelectorV3
    @Mock lateinit var nsAndroidClient: NSAndroidClient
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var virtualPump: VirtualPump
    @Mock lateinit var mockedProfileFunction: ProfileFunction
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var insulin: Insulin
    @Mock lateinit var l: L

    private lateinit var storeDataForDb: StoreDataForDbImpl
    private lateinit var sut: NSClientV3Plugin

    private var insulinConfiguration: ICfg = ICfg("Insulin", 360 * 60 * 1000, 60 * 60 * 1000)

    @BeforeEach
    fun mock() {
        Mockito.`when`(insulin.iCfg).thenReturn(insulinConfiguration)
        Mockito.`when`(activePlugin.activeInsulin).thenReturn(insulin)
    }

    @BeforeEach
    fun prepare() {
        storeDataForDb = StoreDataForDbImpl(aapsLogger, rxBus, persistenceLayer, preferences, uel, config, nsClientSource, virtualPump)
        sut =
            NSClientV3Plugin(
                aapsLogger, rh, preferences, aapsSchedulers, rxBus, context, fabricPrivacy,
                receiverDelegate, config, dateUtil, dataSyncSelectorV3, persistenceLayer,
                nsClientSource, storeDataForDb, decimalFormatter, l
            )
        sut.nsAndroidClient = nsAndroidClient
        Mockito.`when`(mockedProfileFunction.getProfile(anyLong())).thenReturn(validProfile)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddDeviceStatus() = runTest {
        val deviceStatus = DS(
            timestamp = 10000,
            suggested = "{\"temp\":\"absolute\",\"bg\":133,\"tick\":-6,\"eventualBG\":67,\"targetBG\":99,\"insulinReq\":0,\"deliverAt\":\"2023-01-02T15:29:33.374Z\",\"sensitivityRatio\":1,\"variable_sens\":97.5,\"predBGs\":{\"IOB\":[133,127,121,116,111,106,101,97,93,89,85,81,78,75,72,69,67,65,62,60,58,57,55,54,52,51,50,49,48,47,46,45,45,44,43,43,42,42,41,41,41,41,40,40,40,40,39],\"ZT\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39,39,39,39,39,39,39],\"UAM\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39]},\"reason\":\"COB: 0, Dev: 0.1, BGI: -0.3, ISF: 5.4, CR: 13, Target: 5.5, minPredBG 2.2, minGuardBG 2.1, IOBpredBG 2.2, UAMpredBG 2.2; minGuardBG 2.1<4.0\",\"COB\":0,\"IOB\":0.692,\"duration\":90,\"rate\":0,\"timestamp\":\"2023-01-02T15:29:39.460Z\"}",
            iob = "{\"iob\":0.692,\"basaliob\":-0.411,\"activity\":0.0126,\"time\":\"2023-01-02T15:29:39.460Z\"}",
            enacted = "{\"temp\":\"absolute\",\"bg\":133,\"tick\":-6,\"eventualBG\":67,\"targetBG\":99,\"insulinReq\":0,\"deliverAt\":\"2023-01-02T15:29:33.374Z\",\"sensitivityRatio\":1,\"variable_sens\":97.5,\"predBGs\":{\"IOB\":[133,127,121,116,111,106,101,97,93,89,85,81,78,75,72,69,67,65,62,60,58,57,55,54,52,51,50,49,48,47,46,45,45,44,43,43,42,42,41,41,41,41,40,40,40,40,39],\"ZT\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39,39,39,39,39,39,39],\"UAM\":[133,127,121,115,110,105,101,96,92,88,84,81,77,74,71,69,66,64,62,59,58,56,54,53,51,50,49,48,47,46,45,44,44,43,42,42,41,41,40,40,40,39]},\"reason\":\"COB: 0, Dev: 0.1, BGI: -0.3, ISF: 5.4, CR: 13, Target: 5.5, minPredBG 2.2, minGuardBG 2.1, IOBpredBG 2.2, UAMpredBG 2.2; minGuardBG 2.1<4.0\",\"COB\":0,\"IOB\":0.692,\"duration\":90,\"rate\":0,\"timestamp\":\"2023-01-02T15:29:39.460Z\"}",
            device = "openaps://samsung SM-G970F",
            pump = "{\"battery\":{\"percent\":75},\"status\":{\"status\":\"normal\",\"timestamp\":\"2023-01-02T15:20:20.656Z\"},\"extended\":{\"Version\":\"3.1.0.3-dev-e-295e1ad18f-2022.12.24\"," +
                "\"LastBolus\":\"02.01.23 15:24\",\"LastBolusAmount\":\"1\",\"TempBasalAbsoluteRate\":\"0\",\"TempBasalStart\":\"02.01.23 16:20\",\"TempBasalRemaining\":\"55\",\"BaseBasalRate\":\"0" +
                ".41\",\"ActiveProfile\":\"L29_U200 IC\"},\"reservoir\":\"133\",\"clock\":\"2023-01-02T15:25:05.826Z\"}",
            uploaderBattery = 60,
            isCharging = false,
            configuration = "{\"insulin\":5,\"insulinConfiguration\":{},\"sensitivity\":2,\"sensitivityConfiguration\":{\"openapsama_min_5m_carbimpact\":8,\"absorption_cutoff\":4,\"autosens_max\":1.2,\"autosens_min\":0.7},\"overviewConfiguration\":{\"units\":\"mmol\",\"QuickWizard\":\"[]\",\"eatingsoon_duration\":60,\"eatingsoon_target\":4,\"activity_duration\":180,\"activity_target\":7.5,\"hypo_duration\":90,\"hypo_target\":8,\"low_mark\":3.9,\"high_mark\":10,\"statuslights_cage_warning\":72,\"statuslights_cage_critical\":96,\"statuslights_iage_warning\":120,\"statuslights_iage_critical\":150,\"statuslights_sage_warning\":168,\"statuslights_sage_critical\":336,\"statuslights_sbat_warning\":25,\"statuslights_sbat_critical\":5,\"statuslights_bage_warning\":720,\"statuslights_bage_critical\":800,\"statuslights_res_warning\":30,\"statuslights_res_critical\":10,\"statuslights_bat_warning\":50,\"statuslights_bat_critical\":25,\"boluswizard_percentage\":70},\"safetyConfiguration\":{\"age\":\"resistantadult\",\"treatmentssafety_maxbolus\":10,\"treatmentssafety_maxcarbs\":70}}"
        )
        val dataPair = DataSyncSelector.PairDeviceStatus(deviceStatus, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createDeviceStatus(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("devicestatus", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdDeviceStatuses).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.createDeviceStatus(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsAdd("devicestatus", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdDeviceStatuses).hasSize(2) // still only 1
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddEntries() = runTest {
        val glucoseValue = GV(
            timestamp = 10000,
            isValid = true,
            raw = 101.0,
            value = 99.0,
            trendArrow = TrendArrow.DOUBLE_UP,
            noise = 1.0,
            sourceSensor = SourceSensor.DEXCOM_G4_WIXEL,
            ids = IDs(nightscoutId = "nightscoutId")
        )
        val dataPair = DataSyncSelector.PairGlucoseValue(glucoseValue, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createSgv(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("entries", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdGlucoseValues).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateSvg(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("entries", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdGlucoseValues).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddFood() = runTest {
        val food = FD(
            isValid = true,
            name = "name",
            category = "category",
            subCategory = "subcategory",
            portion = 2.0,
            carbs = 20,
            fat = 21,
            protein = 22,
            energy = 23,
            unit = "g",
            gi = 25,
            ids = IDs(
                nightscoutId = "nightscoutId"
            )
        )
        val dataPair = DataSyncSelector.PairFood(food, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createFood(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("food", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdFoods).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateFood(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("food", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdFoods).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddBolus() = runTest {
        val bolus = BS(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            type = BS.Type.SMB,
            notes = "aaaa",
            isBasalInsulin = false,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairBolus(bolus, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBoluses).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBoluses).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddCarbs() = runTest {
        val carbs = CA(
            timestamp = 10000,
            isValid = true,
            amount = 1.0,
            duration = 0,
            notes = "aaaa",
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairCarbs(carbs, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdCarbs).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdCarbs).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddBolusCalculatorResult() = runTest {
        val bolus = BCR(
            timestamp = 10000,
            isValid = true,
            targetBGLow = 110.0,
            targetBGHigh = 120.0,
            isf = 30.0,
            ic = 2.0,
            bolusIOB = 1.1,
            wasBolusIOBUsed = true,
            basalIOB = 1.2,
            wasBasalIOBUsed = true,
            glucoseValue = 150.0,
            wasGlucoseUsed = true,
            glucoseDifference = 30.0,
            glucoseInsulin = 1.3,
            glucoseTrend = 15.0,
            wasTrendUsed = true,
            trendInsulin = 1.4,
            cob = 24.0,
            wasCOBUsed = true,
            cobInsulin = 1.5,
            carbs = 36.0,
            wereCarbsUsed = true,
            carbsInsulin = 1.6,
            otherCorrection = 1.7,
            wasSuperbolusUsed = true,
            superbolusInsulin = 0.3,
            wasTempTargetUsed = false,
            totalInsulin = 9.1,
            percentageCorrection = 70,
            profileName = " sss",
            note = "ddd",
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairBolusCalculatorResult(bolus, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBolusCalculatorResults).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdBolusCalculatorResults).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddEffectiveProfileSwitch() = runTest {
        val profileSwitch = EPS(
            timestamp = 10000,
            isValid = true,
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = validProfile.units,
            originalProfileName = "SomeProfile",
            originalCustomizedName = "SomeProfile (150%, 1h)",
            originalTimeshift = 3600000,
            originalPercentage = 150,
            originalDuration = 3600000,
            originalEnd = 0,
            iCfg = activePlugin.activeInsulin.iCfg.also {
                it.insulinEndTime = (validProfile.dia * 3600 * 1000).toLong()
            },
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairEffectiveProfileSwitch(profileSwitch, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdEffectiveProfileSwitches).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdEffectiveProfileSwitches).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddProfileSwitch() = runTest {
        val profileSwitch = PS(
            timestamp = 10000,
            isValid = true,
            basalBlocks = validProfile.basalBlocks,
            isfBlocks = validProfile.isfBlocks,
            icBlocks = validProfile.icBlocks,
            targetBlocks = validProfile.targetBlocks,
            glucoseUnit = validProfile.units,
            profileName = "SomeProfile",
            timeshift = 0,
            percentage = 100,
            duration = 0,
            iCfg = activePlugin.activeInsulin.iCfg.also {
                it.insulinEndTime = (validProfile.dia * 3600 * 1000).toLong()
            },
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairProfileSwitch(profileSwitch, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdProfileSwitches).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdProfileSwitches).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddExtendedBolus() = runTest {
        val extendedBolus = EB(
            timestamp = 10000,
            isValid = true,
            amount = 2.0,
            isEmulatingTempBasal = false,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairExtendedBolus(extendedBolus, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdExtendedBoluses).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdExtendedBoluses).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddOffilineEvent() = runTest {
        val offlineEvent = OE(
            timestamp = 10000,
            isValid = true,
            reason = OE.Reason.DISCONNECT_PUMP,
            duration = 30000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairOfflineEvent(offlineEvent, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdOfflineEvents).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdOfflineEvents).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddTemporaryBasal() = runTest {
        val temporaryBasal = TB(
            timestamp = 10000,
            isValid = true,
            type = TB.Type.NORMAL,
            rate = 2.0,
            isAbsolute = true,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairTemporaryBasal(temporaryBasal, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdTemporaryBasals).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3", validProfile)
        assertThat(storeDataForDb.nsIdTemporaryBasals).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddTemporaryTarget() = runTest {
        val temporaryTarget = TT(
            timestamp = 10000,
            isValid = true,
            reason = TT.Reason.ACTIVITY,
            highTarget = 100.0,
            lowTarget = 99.0,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairTemporaryTarget(temporaryTarget, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTemporaryTargets).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTemporaryTargets).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddTherapyEvent() = runTest {
        val therapyEvent = TE(
            timestamp = 10000,
            isValid = true,
            type = TE.Type.ANNOUNCEMENT,
            note = "ccccc",
            enteredBy = "dddd",
            glucose = 101.0,
            glucoseType = TE.MeterType.FINGER,
            glucoseUnit = GlucoseUnit.MGDL,
            duration = 3600000,
            ids = IDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )
        val dataPair = DataSyncSelector.PairTherapyEvent(therapyEvent, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createTreatment(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTherapyEvents).hasSize(1)
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("treatments", dataPair, "1/3")
        assertThat(storeDataForDb.nsIdTherapyEvents).hasSize(2)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun nsAddProfile() = runTest {

        val dataPair = DataSyncSelector.PairProfileStore(getValidProfileStore().data, 1000)
        // create
        Mockito.`when`(nsAndroidClient.createProfileStore(anyObject())).thenReturn(CreateUpdateResponse(201, "aaa"))
        sut.nsAdd("profile", dataPair, "1/3")
        // verify(dataSyncSelectorV3, Times(1)).confirmLastProfileStore(1000)
        // verify(dataSyncSelectorV3, Times(1)).processChangedProfileStore()
        // update
        Mockito.`when`(nsAndroidClient.updateTreatment(anyObject())).thenReturn(CreateUpdateResponse(200, "aaa"))
        sut.nsUpdate("profile", dataPair, "1/3")
        // verify(dataSyncSelectorV3, Times(2)).confirmLastProfileStore(1000)
        // verify(dataSyncSelectorV3, Times(2)).processChangedProfileStore()
    }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        sut.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
