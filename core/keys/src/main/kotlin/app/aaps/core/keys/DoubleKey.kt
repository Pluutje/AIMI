package app.aaps.core.keys

enum class DoubleKey(
    override val key: Int,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    override val defaultedBySM: Boolean = false,
    override val calculatedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : DoublePreferenceKey {

    OverviewInsulinButtonIncrement1(R.string.key_insulin_button_increment_1, 0.5, -5.0, 5.0, defaultedBySM = true, dependency = BooleanKey.OverviewShowInsulinButton),
    OverviewInsulinButtonIncrement2(R.string.key_insulin_button_increment_2, 1.0, -5.0, 5.0, defaultedBySM = true, dependency = BooleanKey.OverviewShowInsulinButton),
    OverviewInsulinButtonIncrement3(R.string.key_insulin_button_increment_3, 2.0, -5.0, 5.0, defaultedBySM = true, dependency = BooleanKey.OverviewShowInsulinButton),
    ActionsFillButton1(R.string.key_fill_button_1, 0.3, 0.05, 20.0, defaultedBySM = true, hideParentScreenIfHidden = true),
    ActionsFillButton2(R.string.key_fill_button_2, 0.0, 0.05, 20.0, defaultedBySM = true),
    ActionsFillButton3(R.string.key_fill_button_3, 0.0, 0.05, 20.0, defaultedBySM = true),
    SafetyMaxBolus(R.string.key_safety_max_bolus, 3.0, 0.1, 60.0),
    ApsMaxBasal(R.string.key_openaps_max_basal, 1.0, 0.1, 25.0, defaultedBySM = true, calculatedBySM = true),
    ApsSmbMaxIob(R.string.key_openaps_smb_max_iob, 3.0, 0.0, 70.0, defaultedBySM = true, calculatedBySM = true),
    ApsAmaMaxIob(R.string.key_openaps_ama_max_iob, 1.5, 0.0, 25.0, defaultedBySM = true, calculatedBySM = true),
    ApsMaxDailyMultiplier(R.string.key_openaps_max_daily_safety_multiplier, 3.0, 1.0, 10.0, defaultedBySM = true),
    ApsMaxCurrentBasalMultiplier(R.string.key_openaps_current_basal_safety_multiplier, 4.0, 1.0, 10.0, defaultedBySM = true),
    ApsAmaBolusSnoozeDivisor(R.string.key_openaps_ama_bolus_snooze_dia_divisor, 2.0, 1.0, 10.0, defaultedBySM = true),
    ApsAmaMin5MinCarbsImpact(R.string.key_openaps_ama_min_5m_carbs_impact, 3.0, 1.0, 12.0, defaultedBySM = true),
    ApsSmbMin5MinCarbsImpact(R.string.key_openaps_smb_min_5m_carbs_impact, 8.0, 1.0, 12.0, defaultedBySM = true),
    AbsorptionCutOff(R.string.key_absorption_cutoff, 6.0, 4.0, 10.0),
    AbsorptionMaxTime(R.string.key_absorption_maxtime, 6.0, 4.0, 10.0),
    AutosensMin(R.string.key_openaps_autosens_min, 0.7, 0.1, 1.0, defaultedBySM = true, hideParentScreenIfHidden = true),
    AutosensMax(R.string.key_openaps_autosens_max, 1.2, 0.5, 3.0, defaultedBySM = true),
    EquilMaxBolus(R.string.key_equil_maxbolus, 10.0, 0.1, 25.0),
    OApsAIMIMaxSMB(R.string.key_openapsaimi_max_smb,1.0,0.05,15.0),
    OApsAIMIHighBGMaxSMB(R.string.key_openapsaimi_high_bg_max_smb,1.0,0.05,15.0),
    OApsAIMIweight(R.string.key_aimiweight,50.0,1.0,200.0),
    OApsAIMICHO(R.string.key_cho,50.0,1.0,150.0),
    OApsAIMITDD7(R.string.key_tdd7,40.0,1.0,150.0),
    OApsAIMIMorningFactor(R.string.key_oaps_aimi_morning_factor,50.0,1.0,150.0),
    OApsAIMIAfternoonFactor(R.string.key_oaps_aimi_afternoon_factor,50.0,1.0,150.0),
    OApsAIMIEveningFactor(R.string.key_oaps_aimi_evening_factor,50.0,1.0,150.0),
    OApsAIMIMealFactor(R.string.key_oaps_aimi_meal_factor,50.0,1.0,150.0),
    OApsAIMILunchFactor(R.string.key_oaps_aimi_lunch_factor,50.0,1.0,150.0),
    OApsAIMIDinnerFactor(R.string.key_oaps_aimi_dinner_factor,50.0,1.0,150.0),
    OApsAIMIHCFactor(R.string.key_oaps_aimi_HC_factor,50.0,1.0,150.0),
    OApsAIMISnackFactor(R.string.key_oaps_aimi_snack_factor,50.0,1.0,150.0),
    OApsAIMIHyperFactor(R.string.key_oaps_aimi_hyper_factor,60.0,1.0,150.0),
    OApsAIMIsleepFactor(R.string.key_oaps_aimi_sleep_factor,60.0,1.0,150.0),
    OApsAIMIMealPrebolus(R.string.key_prebolus_meal_mode,2.0,0.1, 10.0),
    OApsAIMILunchPrebolus(R.string.key_prebolus_lunch_mode,2.0,0.1, 10.0),
    OApsAIMIDinnerPrebolus(R.string.key_prebolus_dinner_mode,2.0,0.1, 10.0),
    OApsAIMISnackPrebolus(R.string.key_prebolus_snack_mode,1.0,0.1, 10.0),
    OApsAIMIHighCarbPrebolus(R.string.key_prebolus_highcarb_mode,5.0,0.1, 10.0)

}