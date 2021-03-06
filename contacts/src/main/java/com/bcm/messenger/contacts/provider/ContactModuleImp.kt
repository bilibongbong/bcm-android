package com.bcm.messenger.contacts.provider

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.core.RecipientProfileLogic
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.HomeTopEvent
import com.bcm.messenger.common.finder.BcmFinderManager
import com.bcm.messenger.common.provider.*
import com.bcm.messenger.common.provider.IContactModule.Companion.TAG
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.ui.activity.SearchActivity
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.contacts.BcmUserCardActivity
import com.bcm.messenger.contacts.R
import com.bcm.messenger.contacts.logic.BcmContactLogic
import com.bcm.messenger.contacts.logic.ContactRequestReceiver
import com.bcm.messenger.contacts.search.CurrentSearchFragment
import com.bcm.messenger.contacts.search.RecentSearchFragment
import com.bcm.messenger.login.jobs.MultiDeviceBlockedUpdateJob
import com.bcm.messenger.utility.AmeURLUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by ling on 2018/3/14.
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_CONTACTS_BASE)
class ContactModuleImp : IContactModule {
    private val contactRequestReceiver = ContactRequestReceiver()

    override fun initModule() {
        AmeModuleCenter.serverDispatcher().addListener(contactRequestReceiver)
    }

    override fun uninitModule() {
        AmeModuleCenter.serverDispatcher().removeListener(contactRequestReceiver)
    }

    override fun clear() {
        BcmFinderManager.get().clearRecord()
    }

    override fun openSearch(context: Context) {
        SearchActivity.callSearchActivity(context, "", false, false, CurrentSearchFragment::class.java.name, RecentSearchFragment::class.java.name, 0)
    }

    override fun openContactDataActivity(context: Context, address: Address, nick: String?) {
        val intent = Intent(context, BcmUserCardActivity::class.java)
        intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, address)
        intent.putExtra(ARouterConstants.PARAM.PARAM_NICK, nick)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun openContactDataActivity(context: Context, address: Address, fromGroup: Long) {
        val intent = Intent(context, BcmUserCardActivity::class.java)
        intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, address)
        intent.putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, fromGroup)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun openContactDataActivity(context: Context, address: Address, nick: String?, fromGroup: Long) {
        val intent = Intent(context, BcmUserCardActivity::class.java)
        intent.putExtra(ARouterConstants.PARAM.PARAM_ADDRESS, address)
        intent.putExtra(ARouterConstants.PARAM.PARAM_GROUP_ID, fromGroup)
        intent.putExtra(ARouterConstants.PARAM.PARAM_NICK, nick)
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun discernScanData(context: Context, qrCode: String, callback: ((result: Boolean) -> Unit)?) {

        if (AmeURLUtil.isLegitimateUrl(qrCode)) {

            discernLink(context, qrCode, callback)
        } else {

            val userProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_USER_BASE).navigationWithCast<IUserModule>()
            Observable.create<Pair<String?, String?>> {
                try {
                    val accountDiscernPair = userProvider.checkBackupAccountValid(qrCode)
                    it.onNext(accountDiscernPair)
                }catch (ex: Exception) {
                    it.onNext(Pair(null, ex.message))
                }
                it.onComplete()
            }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        if (!it.first.isNullOrEmpty()) {
                            userProvider.showImportAccountWarning(context) {
                                callback?.invoke(false)
                            }
                        }else {
                            val recipientSetting = Recipient.fromJson(qrCode)
                            if (recipientSetting == null || recipientSetting.uid.isNullOrEmpty()) {
                                BcmRouter.getInstance().get(ARouterConstants.Activity.QR_DISPLAY).putString(ARouterConstants.PARAM.PARAM_QR_CODE, qrCode).navigation(context)
                                callback?.invoke(false)
                            } else {
                                scanRecipientQrCode(context, qrCode, true, callback)
                            }
                        }
                    }



        }
    }

    override fun discernLink(context: Context, link: String, callback: ((result: Boolean) -> Unit)?) {
        val shareContent = AmeGroupMessage.GroupShareContent.fromLink(link)
        if (shareContent != null) {
            val groupProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_GROUP_BASE).navigationWithCast<IGroupModule>()
            groupProvider.queryGroupInfo(shareContent.groupId) {groupInfo ->
                if (groupInfo == null || groupInfo.role == AmeGroupMemberInfo.VISITOR) {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.GROUP_SHARE_DESCRIPTION).putString(ARouterConstants.PARAM.GROUP_SHARE.GROUP_SHARE_CONTENT, shareContent.toString()).navigation(context)
                    callback?.invoke(true)
                }else {
                    AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)?.gotoHome(HomeTopEvent(true, HomeTopEvent.ConversationEvent(ARouterConstants.Activity.CHAT_GROUP_CONVERSATION, -1L, null, shareContent.groupId)))
                    callback?.invoke(true)
                }
            }

        }
        else if (PrivacyProfile.isShortLink(link)) {
            AmeAppLifecycle.showLoading()
            RecipientProfileLogic.checkShareLink(context, link) {result ->
                AmeAppLifecycle.hideLoading()
                if (result == null) {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, link).navigation(context)
                    callback?.invoke(true)
                }else {
                    openContactDataActivity(context, Address.fromSerialized(result.uid), result.name)
                    callback?.invoke(true)
                }
            }
        }
        else {
            BcmRouter.getInstance().get(ARouterConstants.Activity.WEB).putString(ARouterConstants.PARAM.WEB_URL, link).navigation(context)
            callback?.invoke(true)
        }
    }

    private fun scanRecipientQrCode(context: Context, qrCode: String, showConfirm: Boolean, callback: ((result: Boolean) -> Unit)?) {
        ALog.d(TAG, "scanRecipientQrCode: $qrCode")
        val recipientQR = Recipient.fromJson(qrCode)
        if (recipientQR == null) {
            if (showConfirm) {
                AmeAppLifecycle.failure(context.getString(R.string.contacts_requests_add_friend_fail), true)
            }
            callback?.invoke(false)
            return
        }
        Observable.create(ObservableOnSubscribe<Address> {

            val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(recipientQR.uid), false)
            it.onNext(recipient.address)
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    openContactDataActivity(context, it, recipientQR.profileName)
                    callback?.invoke(true)
                }, {
                    ALog.logForSecret(TAG, "scanRecipientQrCode error", it)
                    callback?.invoke(false)
                })

    }

    @SuppressLint("CheckResult")
    override fun blockContact(addressList: List<Address>, block: Boolean, callback: ((successList: List<Address>) -> Unit)?) {

        var completeCount = 0
        val resultList = mutableListOf<Address>()

        fun notify(success: Boolean, address: Address, emitter: ObservableEmitter<Boolean>) {
            completeCount++
            if (success) {
                resultList.add(address)
            }

            if (completeCount >= addressList.size) {
                emitter.onNext(resultList.size > 0)
                emitter.onComplete()
            }

        }

        Observable.create(ObservableOnSubscribe<Boolean> {

            for (address in addressList) {
                val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, address, false)
                Repository.getRecipientRepo()?.setBlocked(recipient, block)
                notify(true, address, it)
            }

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                    callback?.invoke(resultList)

                }, {
                    ALog.e(TAG, "blockContact block: $block error", it)
                    callback?.invoke(resultList)
                })

    }

    @SuppressLint("CheckResult")
    override fun blockContact(address: Address, block: Boolean, callback: ((result: Boolean) -> Unit)?) {

        Observable.create(ObservableOnSubscribe<Boolean> {

            val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, address, false)
            if (recipient.isBlocked == block) {
                it.onNext(true)
                it.onComplete()
                return@ObservableOnSubscribe
            }
            Repository.getRecipientRepo()?.setBlocked(recipient, block)

            AmeModuleCenter.accountJobMgr()?.add(MultiDeviceBlockedUpdateJob(AppContextHolder.APP_CONTEXT))

            it.onNext(true)
            it.onComplete()

        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke(it)
                }, {
                    ALog.e(TAG, "blockContact block: $block error", it)
                    callback?.invoke(false)
                })

    }

    override fun doForLogin() {
        BcmContactLogic.doForLogin()
    }

    override fun doForLogOut() {
        BcmContactLogic.doForLogout()
    }

    override fun addFriend(targetUid: String, memo: String, handleBackground: Boolean, callback: ((result: Boolean) -> Unit)?) {
        Observable.create<Boolean> {
            BcmContactLogic.addFriend(targetUid, memo, handleBackground) { res ->
                it.onNext(res)
                it.onComplete()
            }

        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke(it)
                }, {
                    callback?.invoke(false)
                })
    }

    override fun deleteFriend(targetUid: String, callback: ((result: Boolean) -> Unit)?) {
        Observable.create<Boolean> {
            BcmContactLogic.deleteFriend(targetUid) { res ->
                it.onNext(res)
                it.onComplete()
            }

        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke(it)
                }, {
                    callback?.invoke(false)
                })
    }

    override fun handleFriendPropertyChanged(targetUid: String, callback: ((result: Boolean) -> Unit)?) {
        Observable.create<Boolean> {

            BcmContactLogic.handleFriendPropertyChanged(Recipient.from(AppContextHolder.APP_CONTEXT, Address.fromSerialized(targetUid), false)) { res ->
                it.onNext(res)
                it.onComplete()
            }

        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    callback?.invoke(it)
                }, {
                    callback?.invoke(false)
                })
    }


    override fun checkNeedRequestAddFriend(context: Context, recipient: Recipient) {
        BcmContactLogic.checkRequestFriendForOldVersion(recipient)
    }

    override fun updateThreadRecipientSource(threadRecipientList: List<Recipient>) {

        Observable.create<Unit> {
            BcmContactLogic.contactFinder.updateSourceWithThread(threadRecipientList, Recipient.getRecipientComparator())
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({

                }, {

                })

    }
}