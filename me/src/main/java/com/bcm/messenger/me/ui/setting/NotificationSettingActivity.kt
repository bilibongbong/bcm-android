package com.bcm.messenger.me.ui.setting

import android.os.Bundle
import android.provider.Settings
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.messenger.me.R
import kotlinx.android.synthetic.main.me_activity_notification_setting.*
import com.bcm.messenger.common.SwipeBaseActivity

/**
 * Created by Kin on 2018/11/20
 */
class NotificationSettingActivity : SwipeBaseActivity() {
    private val TAG = "NotificationSettingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.me_activity_notification_setting)

        initView()
    }

    private fun initView() {
        notification_title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })



        setting_notification.setSwitchEnable(false)
        setting_notification.setSwitchStatus(TextSecurePreferences.isNotificationsEnabled(AppContextHolder.APP_CONTEXT))
        setting_notification.setOnClickListener {
            val currentStatus = setting_notification.getSwitchStatus()
            setting_notification.setSwitchStatus(!currentStatus)
            TextSecurePreferences.setNotificationsEnabled(AppContextHolder.APP_CONTEXT, !currentStatus)
        }

        setting_sound.setSwitchEnable(false)
        setting_sound.setSwitchStatus(!TextSecurePreferences.getNotificationRingtone(AppContextHolder.APP_CONTEXT).isNullOrEmpty())
        setting_sound.setOnClickListener {
            val currentStatus = setting_sound.getSwitchStatus()
            setting_sound.setSwitchStatus(!currentStatus)
            if (currentStatus) {
                TextSecurePreferences.setNotificationRingtone(this, "")
            } else {
                TextSecurePreferences.setNotificationRingtone(this, Settings.System.DEFAULT_NOTIFICATION_URI.toString())
            }
        }

        setting_vibration.setSwitchEnable(false)
        setting_vibration.setSwitchStatus(TextSecurePreferences.isNotificationVibrateEnabled(AppContextHolder.APP_CONTEXT))
        setting_vibration.setOnClickListener {
            val currentStatus = setting_vibration.getSwitchStatus()
            setting_vibration.setSwitchStatus(!currentStatus)
            TextSecurePreferences.setNotificationVibrateEnabled(AppContextHolder.APP_CONTEXT, !currentStatus)
        }
    }
}