package com.bcm.messenger.chats.thread

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import android.widget.TextSwitcher
import android.widget.Toast
import androidx.core.content.ContextCompat.getColor
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.event.ServiceConnectEvent
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAdHocModule
import com.bcm.messenger.common.provider.ILoginModule
import com.bcm.messenger.common.ui.popup.ToastUtil
import com.bcm.messenger.common.utils.AppUtil.getString
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.StringAppearanceUtil
import com.bcm.messenger.utility.bcmhttp.utils.ServerCodeUtil
import com.bcm.messenger.utility.network.IConnectionListener
import com.bcm.messenger.utility.network.NetworkUtil
import com.bcm.netswitchy.proxy.IProxyStateChanged
import com.bcm.netswitchy.proxy.ProxyManager
import com.bcm.route.api.BcmRouter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MessageListTitleView: TextSwitcher,IConnectionListener,IProxyStateChanged {

    private val INIT = 0
    private val OFFLINE = 1
    private val CONNECTING = 2
    private val CONNECTED = 3
    private val PROXY_CONNECTING = 4
    private val PROXY_TRY = 5

    private var customProxyConnecting = false

    private var mHasNoticeLowVersionWarning = false
    private var state = INIT

    constructor(context: Context) : super(context, null) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    fun init() {
        EventBus.getDefault().register(this)
        NetworkUtil.addListener(this)
        ProxyManager.setListener(this)

        inAnimation = AnimationUtils.loadAnimation(context, R.anim.common_popup_drop_in)
        outAnimation = AnimationUtils.loadAnimation(context, R.anim.common_popup_drop_out)

        getChildAt(0).setOnClickListener {
            jump()
        }

        getChildAt(1).setOnClickListener {
            jump()
        }

        update()
    }

    fun unInit() {
        EventBus.getDefault().unregister(this)
        NetworkUtil.removeListener(this)
    }

    private fun jump() {
        if (PROXY_TRY == state) {
            BcmRouter.getInstance().get(ARouterConstants.Activity.PROXY_SETTING)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .navigation()
        } else if(OFFLINE == state) {
            val adhocProvider = AmeProvider.get<IAdHocModule>(ARouterConstants.Provider.PROVIDER_AD_HOC)
            adhocProvider?.configHocMode()
        }
    }

    override fun onNetWorkStateChanged() {
        update()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(e: ServiceConnectEvent) {
        update()
    }

    override fun onProxyConnecting(proxyName: String, isOfficial: Boolean) {
        if (!isOfficial) {
            customProxyConnecting = true
            update()
        }
    }

    override fun onProxyConnectFinished() {
        customProxyConnecting = false
        update()
    }

    override fun onProxyListChanged() {
        val isOffline = getState() != CONNECTED
        if (ProxyManager.hasCustomProxy() && !ProxyManager.isProxyRunning() && isOffline) {
            ProxyManager.startProxy()
        }
        update()
    }

    private fun update() {
        val state = getState()
        if (state == this.state) {
            return
        }

        this.state = state

        isEnabled = (state == OFFLINE || state == PROXY_TRY )
        getChildAt(0).isEnabled = isEnabled
        getChildAt(1).isEnabled = isEnabled

        val spanText = when(state) {
            OFFLINE -> {
                val builder = SpannableStringBuilder()
                val offlineString = SpannableString(getString(R.string.chats_network_disconnected))
                offlineString.setSpan(StyleSpan(Typeface.BOLD), 0, offlineString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                offlineString.setSpan(AbsoluteSizeSpan(20, true), 0, offlineString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                offlineString.setSpan(ForegroundColorSpan(getColor(context, R.color.common_color_black)), 0, offlineString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                builder.append(offlineString)

                val spanString = SpannableString(getString(R.string.chats_try_airchat))
                spanString.setSpan(StyleSpan(Typeface.BOLD), 0, spanString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(AbsoluteSizeSpan(12, true), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(ForegroundColorSpan(getColor(context, R.color.common_color_379BFF)), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append(spanString)
                builder.append(StringAppearanceUtil.addImage(context, " ", R.drawable.chats_main_status_right_icon, 12.dp2Px(), 0))

                builder
            }
            PROXY_TRY -> {
                val builder = SpannableStringBuilder()
                val offlineString = SpannableString(getString(R.string.chats_server_can_not_reach))
                offlineString.setSpan(StyleSpan(Typeface.BOLD), 0, offlineString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                offlineString.setSpan(AbsoluteSizeSpan(20, true), 0, offlineString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                offlineString.setSpan(ForegroundColorSpan(getColor(context, R.color.common_color_black)), 0, offlineString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                builder.append(offlineString)

                val spanString = SpannableString(getString(R.string.chats_network_try_proxy))
                spanString.setSpan(StyleSpan(Typeface.BOLD), 0, spanString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(AbsoluteSizeSpan(12, true), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(ForegroundColorSpan(getColor(context, R.color.common_color_379BFF)), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.append(spanString)
                builder.append(StringAppearanceUtil.addImage(context, " ", R.drawable.chats_main_status_right_icon, 12.dp2Px(), 0))

                builder
            }
            CONNECTING, PROXY_CONNECTING -> {
                if (ServerCodeUtil.pullWebSocketError() == ServerCodeUtil.CODE_LOW_VERSION) {
                    if (!mHasNoticeLowVersionWarning) {
                        mHasNoticeLowVersionWarning = true
                        ToastUtil.show(AppContextHolder.APP_CONTEXT, getString(R.string.common_too_low_version_notice), Toast.LENGTH_LONG)
                    }
                }


                val t = if (state == PROXY_CONNECTING) {
                    getString(R.string.chats_try_proxy_doing)
                } else {
                    getString(R.string.chats_network_connecting)
                }

                val spanString = SpannableString(t)
                spanString.setSpan(StyleSpan(Typeface.BOLD), 0, spanString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(AbsoluteSizeSpan(30, true), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(ForegroundColorSpan(getColor(context, R.color.common_color_black)), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                spanString

            }
            else -> {
                val spanString = SpannableString(getString(R.string.chats_title))
                spanString.setSpan(StyleSpan(Typeface.BOLD), 0, spanString.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(AbsoluteSizeSpan(30, true), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spanString.setSpan(ForegroundColorSpan(getColor(context, R.color.common_color_black)), 0, spanString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                spanString
            }
        }

        setText(spanText)
    }

    private fun getState(): Int{
        return when {
            !NetworkUtil.isConnected() -> {
                OFFLINE
            }
            isServiceDisconnected()  -> {
                if (ProxyManager.hasCustomProxy()) {
                    if (!customProxyConnecting) {
                        CONNECTING
                    } else {
                        PROXY_CONNECTING
                    }
                } else {
                    PROXY_TRY
                }
            }
            else -> {
                CONNECTED
            }
        }
    }

    private fun isServiceDisconnected(): Boolean {
        val loginProvider = AmeProvider.get<ILoginModule>(ARouterConstants.Provider.PROVIDER_LOGIN_BASE)
        return loginProvider?.serviceConnectedState() == ServiceConnectEvent.STATE.DISCONNECTED
    }

}