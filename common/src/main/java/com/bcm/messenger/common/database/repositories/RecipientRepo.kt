package com.bcm.messenger.common.database.repositories

import androidx.sqlite.db.SimpleSQLiteQuery
import com.bcm.messenger.common.config.BcmFeatureSupport
import com.bcm.messenger.common.core.Address
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.db.UserDatabase
import com.bcm.messenger.common.database.model.IdentityDbModel
import com.bcm.messenger.common.database.model.RecipientDbModel
import com.bcm.messenger.common.database.records.PrivacyProfile
import com.bcm.messenger.common.database.records.RecipientSettings
import com.bcm.messenger.common.provider.AMESelfData
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.sms.OutgoingLocationMessage
import com.bcm.messenger.common.utils.GroupUtil
import com.bcm.messenger.common.utils.IdentityUtil
import com.bcm.messenger.common.utils.RxBus
import com.bcm.messenger.common.utils.isReleaseBuild
import com.bcm.messenger.utility.AmeTimeUtil
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.logger.ALog
import org.spongycastle.util.encoders.DecoderException
import org.whispersystems.libsignal.IdentityKey

/**
 * Created by Kin on 2019/9/24
 */
class RecipientRepo {
    private val TAG = "RecipientRepo"

    private val recipientDao = UserDatabase.getDatabase().getRecipientDao()

    enum class Relationship(val type: Int) {
        STRANGER(0),
        FRIEND(1),
        FOLLOW(2),
        REQUEST(3),
        FOLLOW_REQUEST(4),
        BREAK(5)
    }

    fun insertRecipients(settingsList: List<RecipientSettings>) {
        recipientDao.insertRecipients(settingsList)
    }


    fun setSupportFeatures(recipient: Recipient, supportFeatures: String) {
        if (recipientDao.updateSupportFeature(recipient.address.serialize(), supportFeatures) == 0) {
            val setting = recipient.settings
            setting.supportFeature = supportFeatures
            recipientDao.insertRecipient(setting)
        }

        try {
            val featureSupport = BcmFeatureSupport(supportFeatures)
            recipient.resolve().featureSupport = featureSupport
        } catch (e: DecoderException) {

        }
    }

    fun setBlocked(recipient: Recipient, isBlocked: Boolean) {
        val block = if (isBlocked) 1 else 0
        if (recipientDao.updateBlock(recipient.address.serialize(), block) == 0) {
            val setting = recipient.settings
            setting.block = block
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().isBlocked = isBlocked

        createBlockMessage(recipient, isBlocked)
    }

    private fun createBlockMessage(recipient: Recipient, isBlocked: Boolean) {
        if (recipient.isSelf) return

        val blockMessage = if (isBlocked) {
            AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_BLOCK,
                    recipient.address.serialize(), emptyList(), null)).toString()
        } else {
            AmeGroupMessage(AmeGroupMessage.SYSTEM_INFO, AmeGroupMessage.SystemContent(AmeGroupMessage.SystemContent.TIP_UNBLOCK,
                    recipient.address.serialize(), emptyList(), null)).toString()
        }
        val message = OutgoingLocationMessage(recipient, blockMessage, recipient.expireMessages * 1000L)
        val threadId = Repository.getThreadRepo().getThreadIdFor(recipient)
        val messageId = Repository.getChatRepo().insertOutgoingTextMessage(threadId, message, AmeTimeUtil.getMessageSendTime(), null)
        Repository.getChatRepo().setMessageSendSuccess(messageId)

        RxBus.post(recipient.address.serialize(), threadId)
    }

    fun setMuted(recipient: Recipient, until: Long) {
        if (recipientDao.updateMute(recipient.address.serialize(), until) == 0) {
            val setting = recipient.settings
            setting.muteUntil = until
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().setMuted(until)
    }

    fun setExpireTime(recipient: Recipient, expiresTime: Long) {
        if (recipientDao.updateExpireTime(recipient.address.serialize(), expiresTime) == 0) {
            val setting = recipient.settings
            setting.expiresTime = expiresTime
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().expireMessages = expiresTime.toInt()
        recipient.resolve().notifyListeners()
    }

    fun setProfileKey(recipient: Recipient, profileKey: ByteArray?) {
        val profileKeyString = if (profileKey == null) null else Base64.encodeBytes(profileKey)
        if (recipientDao.updateProfileKey(recipient.address.serialize(), profileKeyString) == 0) {
            val setting = recipient.settings
            setting.profileKey = profileKeyString
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().profileKey = profileKey
        recipient.resolve().notifyListeners()
    }

    fun setProfileName(recipient: Recipient, profileName: String?) {
        if (recipientDao.updateProfileName(recipient.address.serialize(), profileName) == 0) {
            val setting = recipient.settings
            setting.profileName = profileName
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().profileName = profileName
        recipient.resolve().notifyListeners()
    }

    fun setProfileAvatar(recipient: Recipient, profileAvatar: String?) {
        if (recipientDao.updateProfileAvatar(recipient.address.serialize(), profileAvatar) == 0) {
            val setting = recipient.settings
            setting.profileAvatar = profileAvatar
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().profileAvatar = profileAvatar
        recipient.resolve().notifyListeners()
    }

    fun setProfileSharing(recipient: Recipient, isEnabled: Boolean) {
        val sharing = if (isEnabled) 1 else 0
        if (recipientDao.updateProfileSharing(recipient.address.serialize(), sharing) == 0) {
            val setting = recipient.settings
            setting.profileSharingApproval = sharing
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().isProfileSharing = isEnabled
    }

    fun setProfile(recipient: Recipient, profileKey: ByteArray?, profileName: String?, profileAvatar: String?) {
        val profileKeyString = if (profileKey == null) null else Base64.encodeBytes(profileKey)
        if (recipientDao.updateProfile(recipient.address.serialize(), profileName, profileKeyString, profileAvatar) == 0) {
            val setting = recipient.settings
            setting.profileKey = profileKeyString
            setting.profileAvatar = profileAvatar
            setting.profileName = profileName
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().setProfile(profileKey, profileName, profileAvatar)
        setIdentityKey(recipient.address, recipient.identityKey)
    }

    fun updateProfileName(recipient: Recipient, name: String?) {
        if (name == null) return

        if (recipientDao.updateProfileName(recipient.address.serialize(), name) == 0) {
            val setting = recipient.settings
            setting.profileName = name
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().updateName(name)
        setIdentityKey(recipient.address, recipient.identityKey)
    }

    fun setLocalProfile(recipient: Recipient, localName: String?, localAvatar: String?) {
        if (recipientDao.updateLocalProfile(recipient.address.serialize(), localName, localAvatar) == 0) {
            val setting = recipient.settings
            setting.localName = localName
            setting.localAvatar = localAvatar
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().setLocalProfile(localName, localAvatar)
    }

    fun setPrivacyProfile(recipient: Recipient, profile: PrivacyProfile) {
        if (recipientDao.updatePrivacyProfile(recipient.address.serialize(), profile.toString()) == 0) {
            val setting = recipient.settings
            setting.privacyProfile = profile
            recipientDao.insertRecipient(setting)
        }

        recipient.resolve().privacyProfile = profile
    }

    private fun setIdentityKey(address: Address, identityKeyValue: String?) {
        try {
            if (identityKeyValue.isNullOrEmpty()) {
                ALog.w(TAG, "Identity key is missing on profile")
                return
            }

            val identityKey = IdentityKey(Base64.decode(identityKeyValue), 0)
            if (Repository.getIdentityRepo().getIdentityRecord(address.serialize()) == null) {
                ALog.w(TAG, "IdentityKey first use...")
                return
            }
            ALog.d(TAG, "IdentityKey save")
            IdentityUtil.saveIdentity(AppContextHolder.APP_CONTEXT, address.serialize(), identityKey, true)
        } catch (e: Exception) {
            ALog.w(TAG, e.message)
        }
    }

    fun getAllUsers() = recipientDao.queryAllRecipients()

    fun getFriendsFromContact() = recipientDao.queryAllFriendsAndFollowers(AMESelfData.uid)

    fun getContactsFromOneSide() = recipientDao.queryOneSideRecipients(AMESelfData.uid)

    fun getBlockedUsers() = recipientDao.queryAllBlockedRecipients(AMESelfData.uid)

    fun getGroupRecipients() = recipientDao.queryAllGroupRecipients()

    fun getRecipients(uids: Collection<String>) = realQueryRecipients(uids)

    fun getRecipient(uid: String) = recipientDao.queryRecipient(uid)

    fun getRecipientsLiveData() = recipientDao.queryFriendFollowersLiveData(AMESelfData.uid)

    fun leaveGroup(groupIds: Collection<Long>) {
        val groupList = groupIds.map { GroupUtil.addressFromGid(it).serialize() }
        recipientDao.deleteRecipients(groupList)
    }

    fun updateBcmContacts(settingList: List<RecipientSettings>, notify: Boolean) {
        UserDatabase.getDatabase().runInTransaction {
            realUpdateBcmContacts(settingList, notify)
        }
    }

    /**
     * （notify: block，）
     */
    private fun realUpdateBcmContacts(settingList: List<RecipientSettings>, notify: Boolean) {

        class UpdateSettings(var settings: RecipientSettings?, var needUpdate: Boolean, var createFriendMsg: Boolean, var createBlockMsg: Boolean)

        if (settingList.isEmpty()) return

        val updateMap = HashMap<String, UpdateSettings>(settingList.size)
        settingList.forEach {
            updateMap[it.uid] = UpdateSettings(it, true, false, false)
        }
        val dbSettingList = realQueryRecipients(updateMap.keys)
        ALog.d(TAG, "realQueryRecipients update: ${settingList.size}, result: ${dbSettingList.size}")
        dbSettingList.forEach { dbSettings ->

            val newUpdateSettings = updateMap[dbSettings.uid]
            val newSettings = newUpdateSettings?.settings
            if (newSettings != null) {
                if (!isReleaseBuild()) {
                    ALog.d(TAG, "updateBcmContacts dbSettings: $dbSettings, newSetting: $newSettings")
                }
                var hasUpdate = false
                if (dbSettings.profileName.isNullOrEmpty() && !newSettings.profileName.isNullOrEmpty()) {
                    dbSettings.setTemporaryProfile(newSettings.profileName, dbSettings.profileAvatar)
                    hasUpdate = true
                }
                if (newSettings.contactVersion == RecipientSettings.CONTACT_SYNC_VERSION) { //，，，
                    if (dbSettings.localName != newSettings.localName) {
                        dbSettings.localName = newSettings.localName
                        hasUpdate = true
                    }
                    val dbPrivacyProfile = dbSettings.privacyProfile
                    if (dbPrivacyProfile.nameKey.isNullOrEmpty() && !newSettings.privacyProfile.nameKey.isNullOrEmpty()) {
                        dbPrivacyProfile.nameKey = newSettings.privacyProfile.nameKey
                        hasUpdate = true
                    }
                    if (dbPrivacyProfile.avatarKey.isNullOrEmpty() && !newSettings.privacyProfile.avatarKey.isNullOrEmpty()) {
                        dbPrivacyProfile.avatarKey = newSettings.privacyProfile.avatarKey
                        hasUpdate = true
                    }
                }

                if (newSettings.relationship != dbSettings.relationship) {

                    if (newSettings.relationship == Relationship.STRANGER.type) {

                        if (dbSettings.uid != AMESelfData.uid) {
                            dbSettings.localAvatar = ""
                            dbSettings.localName = ""
                            val privacyProfile = dbSettings.privacyProfile
                            privacyProfile.name = ""
                            privacyProfile.nameKey = ""
                            privacyProfile.avatarHD = ""
                            privacyProfile.avatarHDUri = ""
                            privacyProfile.avatarLD = ""
                            privacyProfile.avatarLDUri = ""
                            privacyProfile.avatarKey = ""
                            dbSettings.privacyProfile = privacyProfile
                            dbSettings.profileKey = ""
                            dbSettings.profileAvatar = ""
                        }

                        if (dbSettings.relationship == Relationship.FRIEND.type && dbSettings.block == 0) {
                            newUpdateSettings.createBlockMsg = true
                        }

                    } else if (newSettings.relationship == Relationship.FRIEND.type) {

                        if (dbSettings.relationship == Relationship.REQUEST.type) {
                            newUpdateSettings.createFriendMsg = true
                        }
                        if (dbSettings.block == 1) {
                            newUpdateSettings.createBlockMsg = true
                        }
                    }

                    ALog.logForSecret(TAG, "updateBcmContacts, uid: ${newSettings.uid} update relationship: ${newSettings.relationship}")
                    dbSettings.relationship = newSettings.relationship

                }else {
                    ALog.logForSecret(TAG, "updateBcmContacts, no need update uid: ${newSettings.uid}, relationship: ${newSettings.relationship}, hasUpdate: $hasUpdate")

                    if (!hasUpdate) {
                        newUpdateSettings.needUpdate = false
                    }
                }
                newUpdateSettings.settings = dbSettings
            }
        }

        updateMap.values.forEach {
            if (it.needUpdate) {
                it.settings?.let { s ->
                    if (!isReleaseBuild()) {
                        ALog.d(TAG, "updateBcmContacts, newSettings: $s")
                    }
                    updateOrInsert(s)
                }
            }
        }

        if (notify) {
            val blockChangeUsers = mutableListOf<RecipientSettings>()
            updateMap.values.forEach {
                it.settings?.let { settings ->
                    val recipient = Recipient.fromSnapshot(AppContextHolder.APP_CONTEXT, Address.fromSerialized(settings.uid), settings)
                    if (it.createFriendMsg) {
                        if (settings.relationship == Relationship.FRIEND.type) {
                            ALog.logForSecret(TAG, "updateBcmContacts, create friendMsg uid: ${settings.uid}")
                            createFriendMessage(recipient, true)
                        } else if (settings.relationship == Relationship.STRANGER.type) {
                            ALog.logForSecret(TAG, "updateBcmContacts, create strangerMsg uid: ${settings.uid}")
                            createFriendMessage(recipient, false)
                        } else {
                            ALog.logForSecret(TAG, "updateBcmContacts, do nothing uid: " + settings.uid + ", relation: " + settings.relationship)
                        }
                    }
                    if (it.createBlockMsg) {
                        if (settings.relationship == Relationship.FRIEND.type) {
                            ALog.logForSecret(TAG, "updateBcmContacts, unblock uid: ${settings.uid}")
                            settings.block = 0
                            recipient.resolve().isBlocked = false
                            createBlockMessage(recipient, false)
                            blockChangeUsers.add(settings)
                        } else if (settings.relationship == Relationship.STRANGER.type) {
                            ALog.logForSecret(TAG, "updateBcmContacts, block uid: ${settings.uid}")

                            settings.block = 1
                            recipient.resolve().isBlocked = true
                            createBlockMessage(recipient, true)
                            blockChangeUsers.add(settings)
                        }

                    }
                }
            }
            recipientDao.updateRecipients(blockChangeUsers)
        }
    }

    private fun createFriendMessage(recipient: Recipient, isFriend: Boolean) {
        if (recipient.isSelf) return
        val messageBody = AmeGroupMessage(AmeGroupMessage.FRIEND, AmeGroupMessage.FriendContent(
                if (isFriend) AmeGroupMessage.FriendContent.ADD else AmeGroupMessage.FriendContent.DELETE,
                recipient.address.serialize()
        ))
        val expiresTime = if (recipient.expireMessages > 0) {
            recipient.expireMessages * 1000L
        } else {
            0L
        }
        val textMessage = OutgoingLocationMessage(recipient, messageBody.toString(), expiresTime)
        val threadId = Repository.getThreadRepo().getThreadIdFor(recipient)
        val messageId = Repository.getChatRepo().insertOutgoingTextMessage(threadId, textMessage, AmeTimeUtil.getMessageSendTime(), null)
        Repository.getChatRepo().setMessageSendSuccess(messageId)

        RxBus.post(recipient.address.serialize(), threadId)
    }

    private fun updateOrInsert(settings: RecipientSettings) {
        if (recipientDao.updateRecipient(settings) <= 0) {
            recipientDao.insertRecipient(settings)
        }
    }

    private fun realQueryRecipients(uids: Collection<String>): List<RecipientSettings> {
        val clauseBuilder = StringBuilder("")
        uids.forEachIndexed { index, s ->
            clauseBuilder.append("'")
            clauseBuilder.append(s)
            clauseBuilder.append("'")
            if (index < uids.size - 1) {
                clauseBuilder.append(", ")
            }
        }
        val sql = """
        SELECT ${RecipientDbModel.TABLE_NAME}.uid, block, mute_until, expires_time, local_name, local_avatar, profile_key, profile_name, profile_avatar, profile_sharing_approval, privacy_profile, relationship, support_feature, identities.`key` AS identityKey 
        FROM ${RecipientDbModel.TABLE_NAME} 
        LEFT OUTER JOIN ${IdentityDbModel.TABLE_NAME} ON (${RecipientDbModel.TABLE_NAME}.uid = ${IdentityDbModel.TABLE_NAME}.uid) 
        WHERE ${RecipientDbModel.TABLE_NAME}.uid IN ($clauseBuilder)
        """
        return recipientDao.queryRecipients(SimpleSQLiteQuery(sql))
    }
}