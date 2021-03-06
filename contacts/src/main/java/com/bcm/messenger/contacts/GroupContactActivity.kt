package com.bcm.messenger.contacts

import android.os.Bundle
import com.bcm.route.annotation.Route
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.ui.CommonTitleBar2
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.contacts_activity_group_contact.*
import com.bcm.messenger.common.SwipeBaseActivity

/**
 * Created by wjh on 2019/7/1
 */
@Route(routePath = ARouterConstants.Activity.GROUP_CONTACT_MAIN)
class GroupContactActivity : SwipeBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.contacts_activity_group_contact)

        title_bar.setListener(object : CommonTitleBar2.TitleBarClickListener() {
            override fun onClickLeft() {
                onBackPressed()
            }

            override fun onClickRight() {
                BcmRouter.getInstance().get(ARouterConstants.Activity.CHAT_GROUP_CREATE).navigation(this@GroupContactActivity)
            }
        })
    }
}