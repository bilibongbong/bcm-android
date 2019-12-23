package com.bcm.messenger.chats.group.logic

import android.annotation.SuppressLint
import android.text.TextUtils
import com.bcm.messenger.chats.R
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.getSelectedLocale
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.event.GroupNameOrAvatarChanged
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.UserDataManager
import com.bcm.messenger.common.grouprepository.room.dao.GroupAvatarParamsDao
import com.bcm.messenger.common.grouprepository.room.entity.GroupAvatarParams
import com.bcm.messenger.common.grouprepository.room.entity.GroupInfo
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.BcmGroupNameUtil
import com.bcm.messenger.common.utils.CombineBitmapUtil
import com.bcm.messenger.common.utils.getString
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.EncryptUtils
import com.bcm.messenger.utility.InputLengthFilter
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import org.greenrobot.eventbus.EventBus
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.min

class GroupAutoGenerateLogic {
    companion object {
        const val TAG = "GroupAutoGenerateLogic"
    }

    private val generatingSet = HashSet<Long>()

    @SuppressLint("CheckResult")
    fun autoGenAvatarOrName(gid: Long) {
        Observable.create<Void> {
            try {
                val params = paramsDao().queryAvatarParams(gid)
                val gInfo = GroupInfoDataManager.queryOneGroupInfo(gid) ?: return@create

                if (gInfo.name.isNotEmpty() && gInfo.iconUrl.isNotEmpty()) {
                    return@create
                }

                if (gInfo.role == AmeGroupMemberInfo.VISITOR) {
                    return@create
                }

                val memberInfoList = if (null != params) {
                    val list = params.toUserList()
                    val mList = UserDataManager.queryGroupMemberList(gid, list).filter { member -> member.role != AmeGroupMemberInfo.VISITOR }.toMutableList()
                    if (mList.size < 4) {
                        val existList = mList.map { m -> m.uid.serialize() }
                        val dbTop4List = UserDataManager.queryTopNGroupMember(gid, 4).filter { m -> !existList.contains(m.uid.serialize()) }
                        mList.addAll(dbTop4List.subList(0, min(dbTop4List.size, 4 - mList.size)))
                        if (mList.isEmpty()) {
                            ALog.i(TAG, "$gid member list is empty")
                            return@create
                        }
                    }

                    if (!isHashChanged(mList, params)) {
                        ALog.i(TAG, "$gid hash state matched 2")
                        if (isAvatarAndNameReady(gInfo)) {
                            return@create
                        }
                        ALog.i(TAG, "$gid hash state matched but info not ready")
                    }
                    mList.map { m -> m.uid.serialize() }.toMutableList()
                } else {
                    ALog.i(TAG, "$gid params is null")
                    mutableListOf<String>()
                }

                if (generatingSet.contains(gid)) {
                    return@create
                }
                generatingSet.add(gid)

                ALog.i(TAG, "refreshing group avatar and name $gid")
                var combineName = ""
                var chnCombineName = ""
                var newParams: GroupAvatarParams? = null

                val queryMember = if (memberInfoList.isEmpty()) {
                    GroupLogic.queryTopMemberInfoList(gid, 4)
                } else {
                    GroupLogic.getGroupMemberInfos(gid, memberInfoList)
                }

                queryMember.subscribeOn(AmeDispatcher.singleScheduler)
                        .observeOn(AmeDispatcher.singleScheduler)
                        .map { list ->
                            combineName = combineGroupName(list, 0)
                            chnCombineName = combineGroupName(list, 1)

                            ALog.i(TAG, "name:$combineName cnName:$chnCombineName")
                            newParams = memberList2Params(gid, list)
                            if (gInfo.iconUrl.isEmpty()) {
                                list.map {
                                    val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, it.uid, false)
                                    val name = BcmGroupNameUtil.getGroupMemberName(recipient, it)
                                    CombineBitmapUtil.RecipientBitmapUnit(recipient, name)
                                }
                            } else {
                                if (null != newParams) {
                                    paramsDao().saveAvatarParams(listOf(newParams!!))
                                }
                                throw Exception("not need update group avatar")
                            }

                        }.flatMap {
                            CombineBitmapUtil.combineBitmap(it, 160, 160)
                                    .subscribeOn(AmeDispatcher.ioScheduler)
                        }.observeOn(AmeDispatcher.ioScheduler)
                        .map {
                            val oldPath = gInfo.spliceAvatar
                            val path = BcmFileUtils.saveBitmap2File(it, "group_avatar_${gid}_${System.currentTimeMillis()}.jpg")
                            gInfo.spliceName = combineName
                            gInfo.chnSpliceName = chnCombineName
                            gInfo.spliceAvatar = path
                            GroupLogic.updateAutoGenGroupNameAndAvatar(gid, combineName, chnCombineName, path)

                            if (TextUtils.isEmpty(oldPath)) {
                                BcmFileUtils.delete(oldPath)
                            }
                            path ?: throw Exception("bitmap save failed")
                        }.observeOn(AmeDispatcher.singleScheduler)
                        .doOnError {
                            ALog.e(TAG, "autoGenAvatarOrName", it)

                            generatingSet.remove(gid)

                            GroupLogic.updateAutoGenGroupNameAndAvatar(gid, combineName, chnCombineName, "")

                            val newInfo = GroupInfoDataManager.queryOneGroupInfo(gid)
                                    ?: return@doOnError

                            if (newInfo.spliceName == gInfo.spliceName && newInfo.iconUrl == gInfo.iconUrl) {
                                return@doOnError
                            }

                            if (isAvatarAndNameReady(newInfo)) {
                                if (null != newParams) {
                                    paramsDao().saveAvatarParams(listOf(newParams!!))
                                }
                            }

                            AmeDispatcher.mainThread.dispatch {
                                ALog.i(TAG, "post name:${groupName(newInfo)} ")
                                EventBus.getDefault().post(GroupNameOrAvatarChanged(gid, groupName(newInfo), groupAvatar(newInfo)))
                            }
                        }.subscribe {
                            if (null != newParams) {
                                paramsDao().saveAvatarParams(listOf(newParams!!))
                            }
                            generatingSet.remove(gid)
                            AmeDispatcher.mainThread.dispatch {
                                ALog.i(TAG, "post name:${groupName(gInfo)} ")
                                EventBus.getDefault().post(GroupNameOrAvatarChanged(gid, groupName(gInfo), groupAvatar(gInfo)))
                            }
                        }
            } finally {
                it.onComplete()
            }

        }.subscribeOn(AmeDispatcher.singleScheduler)
                .doOnError { }
                .subscribe { }
    }

    private fun memberList2Params(gid: Long, list: List<AmeGroupMemberInfo>): GroupAvatarParams {
        val params = GroupAvatarParams()
        params.gid = gid

        if (list.isNotEmpty()) {
            params.uid1 = list[0].uid.serialize()
            params.user1Hash = hashOfUser(list[0])
        }

        if (list.size > 1) {
            params.uid2 = list[1].uid.serialize()
            params.user2Hash = hashOfUser(list[1])
        }

        if (list.size > 2) {
            params.uid3 = list[2].uid.serialize()
            params.user3Hash = hashOfUser(list[2])
        }

        if (list.size > 3) {
            params.uid4 = list[3].uid.serialize()
            params.user4Hash = hashOfUser(list[3])
        }
        return params
    }

    private fun isHashChanged(memberInfoList: List<AmeGroupMemberInfo>, params: GroupAvatarParams): Boolean {
        if (memberInfoList.isEmpty()) {
            return false
        }

        if (memberInfoList.size != params.toUserList().size) {
            return true
        }

        if (!isHashInParams(hashOfUser(memberInfoList[0]), params)) {
            return true
        }

        if (memberInfoList.size == 1) {
            return false
        }

        if (!isHashInParams(hashOfUser(memberInfoList[1]), params)) {
            return true
        }

        if (memberInfoList.size == 2) {
            return false
        }

        if (!isHashInParams(hashOfUser(memberInfoList[2]), params)) {
            return true
        }

        if (memberInfoList.size == 3) {
            return false
        }

        if (!isHashInParams(hashOfUser(memberInfoList[3]), params)) {
            return true
        }

        return false
    }

    private fun isHashInParams(hash: String, params: GroupAvatarParams): Boolean {
        if (params.user1Hash == hash) {
            return true
        }

        if (params.user2Hash == hash) {
            return true
        }

        if (params.user3Hash == hash) {
            return true
        }

        if (params.user4Hash == hash) {
            return true
        }
        return false
    }

    private fun hashOfUser(memberInfo: AmeGroupMemberInfo): String {
        val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, memberInfo.uid, true)
        val name = BcmGroupNameUtil.getGroupMemberName(recipient, memberInfo)
        val avatar = recipient.avatar
        return EncryptUtils.encryptSHA1ToString("$name$avatar")
    }

    // language: 0-ENG, 1-CHN
    private fun combineGroupName(memberList: List<AmeGroupMemberInfo>, language: Int): String {
        ALog.i(TAG, "List count = ${memberList.size}")
        var spliceName = ""
        var index = 0
        for (member in memberList) {
            val uid = member.uid.toString()
            if (uid.isNotBlank() && uid != AMESelfData.uid) {
                val recipient = Recipient.from(AppContextHolder.APP_CONTEXT, member.uid, true)
                val name = BcmGroupNameUtil.getGroupMemberName(recipient, member)
                spliceName += InputLengthFilter.filterSpliceName(name, 10)
                spliceName += if (language == 1) "、" else ", "
                index++
            }
            if (index == 4) break
        }

        return if (spliceName.isEmpty()) {
            getString(R.string.chats_group_setting_default_name)
        } else {
            if (language == 1) {
                spliceName.substring(0, spliceName.length - 1)
            } else {
                spliceName.substring(0, spliceName.length - 2)
            }
        }
    }

    private fun groupName(gInfo: GroupInfo): String {
        return if (gInfo.name.isEmpty()) {
            if (getSelectedLocale(AppContextHolder.APP_CONTEXT).language == Locale.CHINESE.language) {
                gInfo.chnSpliceName ?: getString(R.string.chats_group_setting_default_name)
            } else {
                gInfo.spliceName ?: getString(R.string.chats_group_setting_default_name)
            }
        } else {
            gInfo.name
        }
    }

    private fun groupAvatar(gInfo: GroupInfo): String {
        return if (gInfo.iconUrl.isEmpty()) {
            gInfo.spliceAvatar ?: ""
        } else {
            gInfo.iconUrl
        }
    }

    private fun isAvatarAndNameReady(gInfo: GroupInfo): Boolean {
        return !TextUtils.isEmpty(groupName(gInfo)) && !TextUtils.isEmpty(groupAvatar(gInfo))
    }

    private fun paramsDao(): GroupAvatarParamsDao {
        return UserDatabase.getDatabase().groupAvatarParamsDao()
    }
}