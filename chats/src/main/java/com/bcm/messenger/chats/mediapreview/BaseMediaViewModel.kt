package com.bcm.messenger.chats.mediapreview

import androidx.lifecycle.ViewModel
import com.bcm.messenger.chats.mediapreview.bean.MediaViewData
import com.bcm.messenger.common.crypto.encrypt.BCMEncryptUtils
import com.bcm.messenger.utility.AppContextHolder

/**
 * Created by Kin on 2018/10/31
 */
abstract class BaseMediaViewModel : ViewModel() {

    protected val masterSecret = BCMEncryptUtils.getMasterSecret(AppContextHolder.APP_CONTEXT)

    abstract fun getCurrentData(threadId: Long, indexId: Long, result: (data: MediaViewData) -> Unit)

    abstract fun getAllMediaData(threadId: Long, indexId: Long, reverse: Boolean = false, result: (dataList: List<MediaViewData>) -> Unit)

    abstract fun deleteData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?)

    abstract fun saveData(data: MediaViewData?, result: ((success: Boolean) -> Unit)?)
}