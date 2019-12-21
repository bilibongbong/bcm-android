package com.bcm.messenger.common

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.os.MessageQueue
import android.view.WindowManager
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.core.setLocale
import com.bcm.messenger.common.crypto.MasterSecret
import com.bcm.messenger.common.preferences.TextSecurePreferences
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IUmengModule
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.imid.swipebacklayout.lib.SwipeBackLayout
import me.imid.swipebacklayout.lib.Utils
import me.imid.swipebacklayout.lib.app.SwipeBackActivityBase
import me.imid.swipebacklayout.lib.app.SwipeBackActivityHelper
import java.util.concurrent.TimeUnit

open class SwipeBaseActivity : AppCompatActivity(), SwipeBackActivityBase {
    private val TAG = "SwipeBaseActivity"

    private lateinit var mHelper: SwipeBackActivityHelper
    private var disableDefaultTransition = false
    private var disabledClipboardCheck = false
    private var disabledLightStatusBar = false

    private var checkDisposable: Disposable? = null
    private var checkStartTime = System.currentTimeMillis()

    private val idleHandler = MessageQueue.IdleHandler {
        if (!disabledLightStatusBar) {
            window?.setStatusBarLightMode()
        }
        return@IdleHandler false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!disableDefaultTransition) {
            val enterAnim = intent.getIntExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, R.anim.common_slide_from_right)
            overridePendingTransition(enterAnim, R.anim.common_slide_silent)
        } else {
            val enterAnim = intent.getIntExtra(ARouterConstants.PARAM.PARAM_ENTER_ANIM, 0)
            if (enterAnim != 0) {
                overridePendingTransition(enterAnim, R.anim.common_slide_silent)
            }
        }
        super.onCreate(savedInstanceState)
        ALog.d(TAG, "onCreate: $localClassName")
        window.setTranslucentStatus()
        mHelper = SwipeBackActivityHelper(this)
        mHelper.onActivityCreate()
        swipeBackLayout.setScrimColor(getColorCompat(R.color.common_color_transparent))

        Looper.myQueue().addIdleHandler(idleHandler)

        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.onActivityCreate(this)
    }

    override fun finish() {
        hideKeyboard()
        super.finish()
        if (!disableDefaultTransition) {
            val exitAnim = intent.getIntExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, R.anim.common_slide_to_right)
            overridePendingTransition(R.anim.common_slide_silent, exitAnim)
        } else {
            val exitAnim = intent.getIntExtra(ARouterConstants.PARAM.PARAM_EXIT_ANIM, 0)
            if (exitAnim != 0) {
                overridePendingTransition(R.anim.common_slide_silent, exitAnim)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.onActivityResume(this)

        updateScreenshotSecurity()

        if (!disabledClipboardCheck && ClipboardUtil.clipboardChanged) {
            checkClipboardDelay()
        }
    }

    override fun onPause() {
        super.onPause()
        AmeProvider.get<IUmengModule>(ARouterConstants.Provider.PROVIDER_UMENG)?.onActivityPause(this)

        // Must check at onPause
        if (System.currentTimeMillis() - checkStartTime < 1000) {
            ALog.i(TAG, "SwipeBaseActivity onPause, Start check clipboard time to pause time less then 1 seconds, means have pin lock, canceled check")
            checkDisposable?.dispose()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Looper.myQueue().removeIdleHandler(idleHandler)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mHelper.onPostCreate()
    }

    override fun getSwipeBackLayout(): SwipeBackLayout {
        return mHelper.swipeBackLayout
    }

    override fun setSwipeBackEnable(enable: Boolean) {
        swipeBackLayout.setEnableGesture(enable)
    }

    override fun scrollToFinishActivity() {
        Utils.convertActivityToTranslucent(this)
        swipeBackLayout.scrollToFinishActivity()
    }

    /**
     * Set custom context in order to set language
     */
    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(setLocale(newBase))
    }

    /**
     * AppCompat ver 1.1.0 bug fix.
     * Cannot set locale through the attachBaseContext due to a bug in AppCompat 1.1.0.
     * Check after updating the AppCompat.
     */
    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    /**
     * Disable default activity transition animation if you want to specify a custom one.
     * MUST invoke before invoking super.onCreate(Bundle), and then invoke overridePendingTransition(Int, Int)
     */
    protected open fun disableDefaultTransitionAnimation() {
        disableDefaultTransition = true
    }

    fun getMasterSecret(): MasterSecret = BCMEncryptUtils.getMasterSecret(this) ?: throw Exception("getMasterSecret is null")

    protected open fun disableClipboardCheck() {
        disabledClipboardCheck = true
    }

    protected open fun disableStatusBarLightMode() {
        disabledLightStatusBar = true
    }

    
    fun <T : Fragment> initFragment(@IdRes target: Int,
                                            fragment: T,
                                            extras: Bundle?): T {
        if (extras != null) {
            fragment.arguments = extras
        }
        supportFragmentManager.beginTransaction()
                .replace(target, fragment)
                .commitAllowingStateLoss()
        return fragment
    }

    fun updateScreenshotSecurity() {
        if (isScreenSecurityEnabled(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun isScreenSecurityEnabled(context: Context): Boolean {
        return try {
            if (AppUtil.isMainProcess()) {
                TextSecurePreferences.isScreenSecurityEnabled(context)
            } else {
                ApplicationService.impl?.isScreenSecurityEnabled == true
            }
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Check clipboard data, have default delay time 1 Sec
     *
     * @param delayTime Delay time, unit is seconds
     */
    private fun checkClipboardDelay(delayTime: Long = 1L) {
        ALog.i(TAG, "Delay to check clipboard, delay time is $delayTime seconds")
        checkStartTime = System.currentTimeMillis()
        checkDisposable = Observable.timer(delayTime, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    ClipboardUtil.checkClipboard(this)
                }
    }
}