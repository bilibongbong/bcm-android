package com.bcm.messenger.wallet

import android.os.Bundle
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.CommonTitleBar2
import kotlinx.android.synthetic.main.wallet_activity_main.*
import com.bcm.messenger.common.SwipeBaseActivity

/**
 * Created by wjh on 2019/7/2
 */
@Route(routePath = ARouterConstants.Activity.WALLET_MAIN)
class WalletActivity : SwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        disableStatusBarLightMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.wallet_activity_main)

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                finish()
            }
        })
    }
}