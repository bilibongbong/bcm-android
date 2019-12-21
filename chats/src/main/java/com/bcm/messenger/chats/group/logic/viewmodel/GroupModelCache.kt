package com.bcm.messenger.chats.group.logic.viewmodel

import android.annotation.SuppressLint
import com.bcm.messenger.common.core.corebean.AmeGroupInfo
import com.bcm.messenger.common.core.corebean.AmeGroupMemberInfo
import com.bcm.messenger.common.core.corebean.BcmGroupJoinRequest
import com.bcm.messenger.common.grouprepository.manager.BcmGroupJoinManager
import com.bcm.messenger.common.grouprepository.manager.GroupInfoDataManager
import com.bcm.messenger.common.grouprepository.manager.UserDataManager
import com.bcm.messenger.common.grouprepository.modeltransform.GroupJoinRequestTransform
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.utility.dispatcher.AmeDispatcher
import com.bcm.messenger.utility.logger.ALog
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

/**
 * Created by bcm.social.01 on 2018/6/26.
 */
@SuppressLint("CheckResult")
class GroupModelCache(group: AmeGroupInfo, private val cacheReady: () -> Unit) {
    var info:AmeGroupInfo = group
        private set
    val myRole:Long get() = info.role
    var isCacheReady: Boolean = false

    private var memberList: LinkedHashMap<String, AmeGroupMemberInfo>? = null
    private var joinRequestList:ArrayList<BcmGroupJoinRequest> = arrayListOf()

    init {
        Observable.create(ObservableOnSubscribe<Pair<List<BcmGroupJoinRequest>,
                LinkedHashMap<String, AmeGroupMemberInfo>>> {

            val mlist = LinkedHashMap<String, AmeGroupMemberInfo>()
            val owner = UserDataManager.queryGroupMemberByRole(info.gid, AmeGroupMemberInfo.OWNER.toInt())

            if (owner.isNotEmpty()){
                mlist[owner[0].uid.serialize()] = owner[0]
            }

            val memberList = UserDataManager.queryGroupMemberByRole(info.gid, AmeGroupMemberInfo.MEMBER.toInt())
            for (u in memberList){
                mlist[u.uid.serialize()] = u
            }

            val joinList = GroupJoinRequestTransform.bcmJoinGroupRequestListFromDb(BcmGroupJoinManager.queryJoinRequestByGid(info.gid))

            it.onNext(Pair(joinList, mlist))
            it.onComplete()
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.first.isNotEmpty()) {
                        joinRequestList.addAll(it.first)
                    }

                    if (memberList == null) {
                        memberList = it.second
                    }

                    isCacheReady = true
                    cacheReady()
                }, {
                    ALog.e("GroupModelCache", "init", it)
                })
    }

    fun updateGroupInfo(group:AmeGroupInfo) {
        this.info = group
    }

    @SuppressLint("CheckResult")
    fun reloadMemberList(finished: () -> Unit) {
        Observable.create(ObservableOnSubscribe<LinkedHashMap<String, AmeGroupMemberInfo>> {
            val mlist = LinkedHashMap<String, AmeGroupMemberInfo>()

            val owner = UserDataManager.queryGroupMemberByRole(info.gid, AmeGroupMemberInfo.OWNER.toInt())

            if (owner.isNotEmpty()) {
                mlist[owner[0].uid.serialize()] = owner[0]
            }

            val memberList = UserDataManager.queryGroupMemberByRole(info.gid, AmeGroupMemberInfo.MEMBER.toInt())
            for (u in memberList) {
                mlist[u.uid.serialize()] = u
            }

            it.onNext(mlist)
            it.onComplete()
        }).subscribeOn(AmeDispatcher.ioScheduler)
                .observeOn(AmeDispatcher.mainScheduler)
                .subscribe({
                    memberList = it
                    ALog.i("GroupModelCache", "reloadMemberList finish")
                    finished()
                }, {
                    ALog.e("GroupModelCache", "reloadMemberList", it)
                })
    }

    fun getMemberList(): ArrayList<AmeGroupMemberInfo> {
        val array = ArrayList<AmeGroupMemberInfo>()

        val list = memberList?.values?.toList()
        if (null != list) {
            array.addAll(sortMemberList(list))
        }
        return array
    }

    private fun sortMemberList(list: List<AmeGroupMemberInfo>): List<AmeGroupMemberInfo> {
        return list.sortedWith(Comparator { o1, o2 ->
            if (o1.role == AmeGroupMemberInfo.OWNER && o2.role != AmeGroupMemberInfo.OWNER) {
                -1
            } else if (o2.role == AmeGroupMemberInfo.OWNER && o1.role != AmeGroupMemberInfo.OWNER) {
                1
            }
            else {
                when {
                    o1.createTime > o2.createTime -> 1
                    o1.createTime < o2.createTime -> -1
                    else -> 0
                }
            }
        })
    }

    fun getMemberWithoutSelf(): AmeGroupMemberInfo? {
        val keys = memberList?.keys
        if (null != keys){
            for (uuid in keys){
                if (uuid != AMESelfData.uid){
                    return memberList?.get(uuid)
                }
            }
        }
        return null
    }

    fun getMember(uuid: String): AmeGroupMemberInfo? {
        if (uuid.isNotEmpty()){
            return memberList?.get(uuid)
        }
        return null
    }

    fun removeMember(list:List<String>?){
        if (list != null){
            val removedList = ArrayList<AmeGroupMemberInfo>()
            for (u in list){
                val remove = memberList?.remove(u)
                if (null != remove){
                    removedList.add(remove)
                }

                if (null != remove){
                    if (remove.uid.serialize() == AMESelfData.uid){
                        info.role = AmeGroupMemberInfo.VISITOR
                        AmeDispatcher.io.dispatch{
                            GroupInfoDataManager.updateGroupRole(info.gid, AmeGroupMemberInfo.VISITOR)
                        }
                    }
                }
            }
        }
    }


    fun addMember(list:List<AmeGroupMemberInfo>?){
        if (list != null){
            for (u in list){
                memberList?.put(u.uid.serialize(), u)

                if (u.uid.serialize() == AMESelfData.uid){
                    info.role = u.role
                    AmeDispatcher.io.dispatch {
                        GroupInfoDataManager.updateGroupRole(info.gid, u.role)
                    }
                }

                if (u.role == AmeGroupMemberInfo.OWNER){
                    info.owner = u.uid.serialize()
                }
            }
        }
    }

    fun updateMyInfo(newName: String?, newGroupName:String?, newKeyConfig: AmeGroupMemberInfo.KeyConfig?) {
        val member = UserDataManager.queryGroupMember(info.gid, AMESelfData.uid)
        val memoryMember = getMember(AMESelfData.uid)
        if (null != newName) {
            member?.nickname = newName
            memoryMember?.nickname = newName
        }

        if (newKeyConfig != null) {
            member?.keyConfig = newKeyConfig
            memoryMember?.keyConfig = newKeyConfig
        }

        if (newGroupName != null){
            member?.customNickname = newGroupName
            memoryMember?.customNickname = newGroupName
        }

        if (null != member) {
            AmeDispatcher.io.dispatch {
                UserDataManager.updateGroupMember(member)
            }
        }
    }

    fun updateMemberInfoList(members: List<AmeGroupMemberInfo>) {
        for (i in members) {
            memberList?.set(i.uid.serialize(), i)
        }

        AmeDispatcher.io.dispatch {
            UserDataManager.insertGroupMembers(members)
        }
    }

    fun getJoinRequest(mid: Long): List<BcmGroupJoinRequest> {
        val list = ArrayList<BcmGroupJoinRequest>()
        for (i in joinRequestList) {
            if (i.mid == mid) {
                list.add(i)
            }
        }
        return list
    }

    fun getJoinRequestList(): List<BcmGroupJoinRequest> {
        val uidfilter = mutableSetOf<String>()
        return joinRequestList.filter {
            if(it.isWatingAccepted()) {
                val key = "${it.inviter}${it.uid}"
               if (!uidfilter.contains(key)) {
                   uidfilter.add(key)
                    true
               } else {
                   false
               }
            } else {
                false
            }

        }
    }

    fun readAllJoinRequest() {
        val list = joinRequestList.filter { !it.read }
        for (i in list){
            i.read = true
        }

        AmeDispatcher.io.dispatch {
            val reqIdList = list.map { it.reqId }
            BcmGroupJoinManager.readAllJoinRequest(reqIdList)
        }
    }

    fun readJoinRequests(reqList:List<BcmGroupJoinRequest>) {
        for (i in reqList) {
            i.read = true
        }

        AmeDispatcher.io.dispatch {
            val reqIdList = reqList.map { it.reqId }
            BcmGroupJoinManager.readAllJoinRequest(reqIdList)
        }
    }

    fun refreshJoinRequestList(finish:()->Unit) {
        Observable.create<List<BcmGroupJoinRequest>> {
            val joinList = GroupJoinRequestTransform.bcmJoinGroupRequestListFromDb(BcmGroupJoinManager.queryJoinRequestByGid(info.gid)).sortedByDescending {
                it.timestamp
            }
            it.onNext(joinList)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    ALog.e("GroupModelCache", "refreshJoinRequestCache failed", it)
                }.subscribe {
                    joinRequestList.clear()
                    joinRequestList.addAll(it)
                    finish()
                }
    }

    fun getJoinRequestListUnreadCount(): Int {
        var count = 0
        for (i in joinRequestList) {
            if (!i.read && i.isWatingAccepted()) {
                ++count
            }
        }
        return count
    }

    fun updateShareSetting(shareEnable: Boolean, shareCode: String) {
        val gInfo = this.info
        gInfo.shareEnable = shareEnable
        gInfo.shareCode = shareCode
    }

    fun updateNeedConfirmSetting(needConfirm:Boolean) {
        val gInfo = this.info
        gInfo.needConfirm = needConfirm
    }
}