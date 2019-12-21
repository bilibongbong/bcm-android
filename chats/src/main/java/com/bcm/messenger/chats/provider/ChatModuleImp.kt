package com.bcm.messenger.chats.provider

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.clean.CleanConversationStorageLogic
import com.bcm.messenger.chats.components.ChatCallFloatWindow
import com.bcm.messenger.chats.forward.ForwardActivity
import com.bcm.messenger.chats.group.logic.GroupMessageLogic
import com.bcm.messenger.chats.mediabrowser.ui.MediaBrowserActivity
import com.bcm.messenger.chats.privatechat.ChatRtcCallActivity
import com.bcm.messenger.chats.privatechat.logic.ChatMessageReceiver
import com.bcm.messenger.chats.privatechat.logic.MessageSender
import com.bcm.messenger.chats.privatechat.webrtc.WebRtcCallService
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.database.repositories.Repository
import com.bcm.messenger.common.event.ReEditEvent
import com.bcm.messenger.common.grouprepository.events.MessageEvent
import com.bcm.messenger.common.grouprepository.manager.MessageDataManager
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.grouprepository.room.entity.GroupMessage
import com.bcm.messenger.common.provider.AmeModuleCenter
import com.bcm.messenger.common.provider.IChatModule
import com.bcm.messenger.common.provider.bean.ConversationStorage
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.ui.popup.bottompopup.AmeBottomPopup
import com.bcm.messenger.common.utils.AmeAppLifecycle
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.common.utils.startForegroundServiceCompat
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.annotation.Route
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

/**
 * Created by zjl on 2018/3/14.
 */
@Route(routePath = ARouterConstants.Provider.PROVIDER_CONVERSATION_BASE)
class ChatModuleImp : IChatModule {
    private val chatMessageReceiver = ChatMessageReceiver()

    override fun initModule() {
        AmeModuleCenter.serverDispatcher().addListener(chatMessageReceiver)
    }

    override fun uninitModule() {
        AmeModuleCenter.serverDispatcher().removeListener(chatMessageReceiver)
    }

    private val TAG = "IConversationProvider"
    override fun checkHasRtcCall() {
        ALog.i(TAG, "checkHasRtcCall")
        WebRtcCallService.checkHasWebRtcCall()
    }

    override fun startRtcCallService(context: Context, address: String, callType: Int) {
        try {
            if (ChatCallFloatWindow.hasWebRtcCalling()) {
                ALog.i(TAG, "startRtcCallService fail, hasWebRtcCalling")
                return
            }
            val intent = Intent(context, WebRtcCallService::class.java)
            intent.action = WebRtcCallService.ACTION_OUTGOING_CALL
            intent.putExtra(WebRtcCallService.EXTRA_REMOTE_ADDRESS, Address.fromSerialized(address))
            intent.putExtra(ARouterConstants.PARAM.PRIVATE_CALL.PARAM_CALL_TYPE, callType)
            context.startForegroundServiceCompat(intent)

        } catch (ex: Exception) {
            ALog.e(TAG, "startRtcCallService fail", ex)
        }
    }

    override fun startRtcCallActivity(context: Context, callType: Int?) {
        try {
            ALog.d(TAG, "startRtcCallActivity")
            if (ChatCallFloatWindow.hasWebRtcCalling()) {
                ALog.d(TAG, "hasWebRtcCalling, not stat activity")
                return
            }
            val activityIntent = Intent(context, ChatRtcCallActivity::class.java)
            if (callType != null) {
                activityIntent.putExtra(ARouterConstants.PARAM.PRIVATE_CALL.PARAM_CALL_TYPE, callType)
            }
            if(context is Activity) {
                context.startActivity(activityIntent)
            }else {
                activityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(activityIntent)
            }
        } catch (ex: Exception) {
            ALog.e(TAG, "startRtcCallService fail", ex)
        }
    }

    @SuppressLint("CheckResult")
    override fun deleteMessage(context: Context, isGroup: Boolean, conversationId: Long, messageSet: Set<Any>, callback: ((fail: Set<Any>) -> Unit)?) {
        if(messageSet.isEmpty()) {
            callback?.invoke(emptySet())
            return
        }

  
        fun transformToEntity(messageDetail: AmeGroupMessageDetail): GroupMessage {

            val msg = GroupMessage()
            msg.gid = messageDetail.gid
            msg.send_or_receive = when (messageDetail.isSendByMe) {
                true -> GroupMessage.SEND
                false -> GroupMessage.RECEIVE
            }

            msg.mid = messageDetail.serverIndex
            msg.text = messageDetail.message.toString()
            msg.type = messageDetail.type
            msg.attachment_uri = messageDetail.attachmentUri
            msg.create_time = messageDetail.sendTime
            msg.from_uid = messageDetail.senderId
            msg.read_state = when (messageDetail.isRead) {
                true -> GroupMessage.READ_STATE_READ
                false -> GroupMessage.READ_STATE_UNREAD
            }

            msg.key_version = messageDetail.keyVersion

            msg.send_state = when (messageDetail.sendState) {
                AmeGroupMessageDetail.SendState.SEND_FAILED -> GroupMessage.SEND_FAILURE
                AmeGroupMessageDetail.SendState.SEND_SUCCESS -> GroupMessage.SEND_SUCCESS
                AmeGroupMessageDetail.SendState.SENDING -> GroupMessage.SENDING
                AmeGroupMessageDetail.SendState.RECEIVE_SUCCESS -> GroupMessage.RECEIVE_SUCCESS
                else -> GroupMessage.SEND_FAILURE
            }

            msg.content_type = messageDetail.message.type.toInt()
            msg.is_confirm = GroupMessage.CONFIRM_MESSAGE
            msg.extContent = messageDetail.extContentString
            return msg

        }
        AmePopup.bottom.newBuilder()
                .withTitle(context.getString(R.string.chats_delete_selected_message_title, messageSet.size))
                .withPopItem(AmeBottomPopup.PopupItem(context.getString(R.string.chats_delete_selected_message_button)) {
                    AmeAppLifecycle.showLoading()

                    Observable.create<Set<Any>> {
                        ALog.d("ConversationProviderImp", "delete ${messageSet.size} data")
                        val fail = mutableSetOf<Any>()
                        try {
                            if (isGroup) {
                                val groupMessageList = messageSet.map {
                                    transformToEntity(it as AmeGroupMessageDetail).apply {
                                        id = it.indexId
                                    }
                                }
                                MessageDataManager.deleteMessages(conversationId, groupMessageList)
                                EventBus.getDefault().post(MessageEvent(conversationId, messageSet.map {
                                    (it as AmeGroupMessageDetail).indexId
                                }))
                            } else {
                                val ids = mutableListOf<Long>()
                                messageSet.forEach {
                                    val m = it as MessageRecord
                                    ids.add(m.id)
                                }
                                Repository.getChatRepo().deleteMessages(conversationId, ids)
                            }
                        } catch (ex: Exception) {
                            ALog.e("ConversationProviderImp", "deleteMessage error", ex)
                            fail.addAll(messageSet)
                        } finally {
                            it.onNext(fail)
                            it.onComplete()
                        }
                    }.subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe {
                                AmeAppLifecycle.hideLoading()
                                if (it.isEmpty()) {
                                    AmeAppLifecycle.succeed(context.getString(R.string.chats_delete_success), true)
                                } else {
                                    AmeAppLifecycle.failure(context.getString(R.string.chats_delete_fail), false)
                                }
                                callback?.invoke(it)
                            }
                })
                .withDoneTitle(context.getString(R.string.common_cancel))
                .show(context as? FragmentActivity)
    }

    @SuppressLint("CheckResult")
    override fun recallMessage(context: Context, isGroup: Boolean, messageRecord: Any, callback: ((success: Boolean) -> Unit)?) {
        val title: String
        val button: String
        var privateMessage: MessageRecord? = null
        var groupMessage: AmeGroupMessageDetail? = null
        var isRecall = true
        if (isGroup) {
            groupMessage = messageRecord as AmeGroupMessageDetail
            if (groupMessage.isReeditable) {
                isRecall = false
                title = context.resources.getString(R.string.chats_reedit_the_selected_text_message)
                button = context.resources.getString(R.string.chats_text_reedit)
            } else {
                title = context.resources.getString(R.string.chats_reedit_the_selected_message)
                button = context.resources.getString(R.string.chats_text_recall)
            }
        } else {
            privateMessage = messageRecord as MessageRecord
            if (privateMessage.isMediaMessage()) {
                title = context.resources.getString(R.string.chats_reedit_the_selected_message)
                button = context.resources.getString(R.string.chats_text_recall)
            } else {
                isRecall = false
                title = context.resources.getString(R.string.chats_reedit_the_selected_text_message)
                button = context.resources.getString(R.string.chats_text_reedit)
            }
        }
        val activity = context as? FragmentActivity ?: return
        AmePopup.bottom.newBuilder()
                .withTitle(title)
                .withPopItem(AmeBottomPopup.PopupItem(button) {
                    fun recallResponse(success: Boolean) {
                        if (success) {
                            if (isGroup && groupMessage?.message?.isText() == true) {
                                EventBus.getDefault().post(ReEditEvent(GroupUtil.addressFromGid(groupMessage.gid),
                                        groupMessage.indexId, groupMessage.message.content.getDescribe(groupMessage.gid).toString()))
                            }
                        } else {
                            if (isRecall) {
                                AmeAppLifecycle.failure(context.getString(R.string.chats_recall_message_fail_description), true)
                            } else {
                                AmeAppLifecycle.failure(context.getString(R.string.chats_reedit_message_fail_description), true)
                            }
                        }
                    }

                    if (isGroup) {
                        GroupMessageLogic.messageSender.recallMessage(groupMessage, object : com.bcm.messenger.chats.group.logic.MessageSender.SenderCallback {
                            override fun call(messageDetail: AmeGroupMessageDetail?, indexId: Long, isSuccess: Boolean) {
                                recallResponse(isSuccess)
                            }
                        })
                    } else {
                        Observable.create(ObservableOnSubscribe<Boolean> { emiter ->
                            val masterSecret = BCMEncryptUtils.getMasterSecret(context)
                            MessageSender.recall(privateMessage, masterSecret, privateMessage?.isMediaMessage() == true)
                            emiter.onComplete()
                        }).subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe({ }, {
                                    ALog.e("ConversationProviderImp", "recall message fail", it)
                                    recallResponse(false)
                                })
                    }
                })
                .withDoneTitle(context.getString(R.string.common_cancel))
                .show(activity)

    }

    override fun forwardMessage(context: Context, isGroup: Boolean, conversationId: Long, messageSet: Set<Any>, callback: ((fail: Set<Any>) -> Unit)?) {
        if (messageSet.isNotEmpty()) {
            if(messageSet.size > 15) {
                AmeAppLifecycle.failure(context.getString(R.string.chats_max_forward_error), true)
                callback?.invoke(messageSet)
                return
            }
            var hasCannotForwardType = false
            val msgIdList = mutableListOf<Long>()
            if (isGroup) {
                val selectedList = messageSet.sortedBy { msg -> (msg as AmeGroupMessageDetail).sendTime }
                selectedList.forEach { detail ->
                    val msg = detail as AmeGroupMessageDetail
                    if (msg.message.type == AmeGroupMessage.AUDIO ||
//                            msg.message.type == AmeGroupMessage.CONTACT ||
                            msg.message.type == AmeGroupMessage.CHAT_HISTORY) {
                        hasCannotForwardType = true
                    } else {
                        msgIdList.add(msg.indexId)
                    }
                }
            } else {
                val selectedList = messageSet.sortedBy { msg -> (msg as MessageRecord).dateReceive }
                selectedList.forEach { msg ->
                    val record = msg as MessageRecord
                    if (record.isAudioCall() || record.isVideoCall()) {
                        hasCannotForwardType = true
                    } else if (record.isLocation()) {
                        val groupMessage = AmeGroupMessage.messageFromJson(record.body)
                        if (groupMessage.type == AmeGroupMessage.AUDIO ||
//                                    groupMessage.type == AmeGroupMessage.CONTACT ||
                                groupMessage.type == AmeGroupMessage.CHAT_HISTORY) {
                            hasCannotForwardType = true
                        } else {
                            msgIdList.add(record.id)
                        }
                    } else if (record.isMediaMessage() && record.hasAudios()) {
                        hasCannotForwardType = true
                    } else {
                        msgIdList.add(record.id)
                    }
                }
            }
            val activity = context as? FragmentActivity ?: AmeAppLifecycle.current() as FragmentActivity
            if (hasCannotForwardType) {

                AmePopup.center.newBuilder()
                        .withTitle(context.getString(R.string.chats_forward_multiple_notice))
                        .withContent(context.getString(R.string.chats_forward_multiple_contains_ban_messages))
                        .withOkTitle(context.getString(R.string.common_popup_ok))
                        .withCancelTitle(context.getString(R.string.common_cancel))
                        .withOkListener {
                            val intent = Intent(context, ForwardActivity::class.java).apply {
                                putExtra(ForwardActivity.MULTI_INDEX_ID, msgIdList.toLongArray())
                                putExtra(ForwardActivity.GID, if (isGroup) conversationId else ARouterConstants.PRIVATE_MEDIA_CHAT)
                            }
                            if(activity == null) {
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }else {
                                activity.startActivity(intent)
                            }
                            callback?.invoke(emptySet())

                        }.show(activity)
            } else {
                val intent = Intent(context, ForwardActivity::class.java).apply {
                    putExtra(ForwardActivity.MULTI_INDEX_ID, msgIdList.toLongArray())
                    putExtra(ForwardActivity.GID, if (isGroup) conversationId else ARouterConstants.PRIVATE_MEDIA_CHAT)
                }
                if(activity == null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }else {
                    activity.startActivity(intent)
                }

                callback?.invoke(emptySet())
            }
        }
    }

    override fun toConversationBrowser(address: Address, deleteMode: Boolean) {
        MediaBrowserActivity.router(address, deleteMode)
    }

    override fun queryAllConversationStorageSize(callback: ((result: ConversationStorage) -> Unit)?) {
        CleanConversationStorageLogic.addCallback(callback = object : CleanConversationStorageLogic.ConversationStorageCallback {
            override fun onCollect(finishedConversation: Address?, allFinished: Boolean) {
                if (finishedConversation == null && allFinished) {
                    callback?.invoke(CleanConversationStorageLogic.getAllConversationStorageSize())
                }
            }

            override fun onClean(finishedConversation: Address?, allFinished: Boolean) {
            }

        })
        CleanConversationStorageLogic.collectionAllConversationStorageSize()
    }

    override fun finishAllConversationStorageQuery() {
        CleanConversationStorageLogic.removeCallback()
        CleanConversationStorageLogic.clearCache()
    }

}