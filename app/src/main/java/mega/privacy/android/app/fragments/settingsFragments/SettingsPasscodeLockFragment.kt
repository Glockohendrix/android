package mega.privacy.android.app.fragments.settingsFragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import mega.privacy.android.app.R
import mega.privacy.android.app.activities.settingsActivities.PasscodeLockActivity
import mega.privacy.android.app.activities.settingsActivities.PasscodeLockActivity.Companion.ACTION_RESET_PASSCODE_LOCK
import mega.privacy.android.app.activities.settingsActivities.PasscodeLockActivity.Companion.ACTION_SET_PASSCODE_LOCK
import mega.privacy.android.app.constants.SettingsConstants.*
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.Constants.INVALID_POSITION
import mega.privacy.android.app.utils.LogUtil
import mega.privacy.android.app.utils.PasscodeUtil
import mega.privacy.android.app.utils.TextUtil.isTextEmpty
import javax.inject.Inject

@AndroidEntryPoint
class SettingsPasscodeLockFragment : SettingsBaseFragment() {

    companion object {
        private const val IS_REQUIRE_PASSCODE_DIALOG_SHOWN = "IS_REQUIRE_PASSCODE_DIALOG_SHOWN"
        private const val REQUIRE_PASSCODE_DIALOG_OPTION = "REQUIRE_PASSCODE_DIALOG_OPTION"
    }

    @Inject
    lateinit var passcodeUtil: PasscodeUtil
    private lateinit var requirePasscodeDialog: AlertDialog

    private var passcodeSwitch: SwitchPreferenceCompat? = null
    private var resetPasscode: Preference? = null
    private var requirePasscode: Preference? = null

    private var passcodeLock = false

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_passcode)
        passcodeSwitch = findPreference(KEY_PASSCODE_ENABLE)
        passcodeSwitch?.setOnPreferenceClickListener {
            if (passcodeLock) {
                disablePasscode()
            } else {
                passcodeSwitch?.isChecked = false
                intentToPasscodeLock(false)
            }

            true
        }

        resetPasscode = findPreference(KEY_RESET_PASSCODE)
        resetPasscode?.setOnPreferenceClickListener {
            intentToPasscodeLock(true)
            true
        }

        requirePasscode = findPreference(KEY_REQUIRE_PASSCODE)
        requirePasscode?.setOnPreferenceClickListener {
            showRequirePasscodeDialog(INVALID_POSITION)
            true
        }

        prefs = dbH.preferences

        if (prefs == null || prefs.passcodeLockEnabled == null
            || !prefs.passcodeLockEnabled.toBoolean() || isTextEmpty(prefs.passcodeLockCode)
        ) {
            disablePasscode()
        } else {
            enablePasscode()
        }

        if (savedInstanceState != null
            && savedInstanceState.getBoolean(IS_REQUIRE_PASSCODE_DIALOG_SHOWN, false)
        ) {
            showRequirePasscodeDialog(savedInstanceState.getInt(REQUIRE_PASSCODE_DIALOG_OPTION))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (isRequirePasscodeDialogShow()) {
            outState.putBoolean(IS_REQUIRE_PASSCODE_DIALOG_SHOWN, true)
            outState.putInt(
                REQUIRE_PASSCODE_DIALOG_OPTION,
                requirePasscodeDialog.listView.checkedItemPosition
            )
        }

        super.onSaveInstanceState(outState)
    }

    private fun showRequirePasscodeDialog(selectedPosition: Int) {
        requirePasscodeDialog = passcodeUtil.showRequirePasscodeDialog(selectedPosition)
        requirePasscodeDialog.setOnDismissListener {
            requirePasscode?.summary =
                passcodeUtil.getRequiredPasscodeText(dbH.passcodeRequiredTime)
        }
    }

    private fun isRequirePasscodeDialogShow(): Boolean =
        this::requirePasscodeDialog.isInitialized && requirePasscodeDialog.isShowing

    private fun enablePasscode() {
        passcodeLock = true
        passcodeSwitch?.isChecked = true
        preferenceScreen.addPreference(resetPasscode)
        preferenceScreen.addPreference(requirePasscode)
        requirePasscode?.summary = passcodeUtil.getRequiredPasscodeText(dbH.passcodeRequiredTime)
    }

    private fun disablePasscode() {
        passcodeLock = false
        passcodeSwitch?.isChecked = false
        preferenceScreen.removePreference(resetPasscode)
        preferenceScreen.removePreference(requirePasscode)
        passcodeUtil.disablePasscode()
    }

    /**
     * Launches an Intent to open passcode screen.
     */
    private fun intentToPasscodeLock(reset: Boolean) {
        val intent = Intent(context, PasscodeLockActivity::class.java)
        intent.action = if (reset) ACTION_RESET_PASSCODE_LOCK else ACTION_SET_PASSCODE_LOCK
        startActivityForResult(intent, Constants.SET_PIN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode != Constants.SET_PIN) return

        if (resultCode == Activity.RESULT_OK) {
            enablePasscode()
        } else {
            LogUtil.logWarning("Set PIN ERROR")
        }
    }

    override fun onDestroy() {
        if (isRequirePasscodeDialogShow()) {
            requirePasscodeDialog.dismiss()
        }

        super.onDestroy()
    }
}