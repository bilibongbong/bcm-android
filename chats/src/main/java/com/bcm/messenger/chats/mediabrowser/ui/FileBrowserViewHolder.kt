package com.bcm.messenger.chats.mediabrowser.ui

import android.view.View
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.RecyclerView
import com.bcm.messenger.chats.R
import com.bcm.messenger.chats.mediabrowser.MediaBrowseData
import com.bcm.messenger.chats.mediabrowser.MediaHandleViewModel
import com.bcm.messenger.chats.mediabrowser.bean.FileBrowserData
import com.bcm.messenger.chats.util.ChatPreviewClickListener
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.core.AmeGroupMessage
import com.bcm.messenger.common.database.records.MessageRecord
import com.bcm.messenger.common.grouprepository.model.AmeGroupMessageDetail
import com.bcm.messenger.common.provider.IContactModule
import com.bcm.messenger.common.utils.*
import com.bcm.messenger.utility.permission.PermissionUtil
import com.bcm.messenger.common.utils.DateUtils
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.utility.AmeURLUtil
import com.bcm.route.api.BcmRouter
import kotlinx.android.synthetic.main.chats_file_browser_view.view.*

/**
 * Created by zjl on 2018/10/16.
 */
class FileBrowserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val TAG = "FileBrowserViewHolder"

    private var viewModel: MediaHandleViewModel? = null
    private var mediaBrowseData: MediaBrowseData? = null
    private var mData: FileBrowserData? = null

    fun bindData(data: FileBrowserData) {
        mData = data
        mediaBrowseData = data.data
        mediaBrowseData?.let {
            it.setThumbnail( itemView.browser_file_img_background, itemView.browser_file_img, 40.dp2Px(), 20.dp2Px(), R.drawable.chats_message_file_icon_grey)
            itemView.browser_file_name.text = it.name
            itemView.browser_file_sub_content.text = DateUtils.formatFileTime(it.time)
        }

        if (!data.isInSelecting) {
            itemView.setOnLongClickListener {
                data.deleteCallback?.multiSelect()
                return@setOnLongClickListener true
            }
        } else {
            itemView.setOnLongClickListener(null)
        }

        viewModel = ViewModelProviders.of(itemView.context as AppCompatActivity).get(MediaHandleViewModel::class.java)
        if (data.isInSelecting) {
            itemView.file_select.visibility = View.VISIBLE
            itemView.setOnClickListener {
                changeSelectView(itemView.file_select)
            }
        } else {
            itemView.file_select.visibility = View.GONE
            itemView.setOnClickListener { v ->
                itemClick(v)
            }
        }

        val selection = viewModel?.selection?.value
        itemView.file_select.isChecked = selection?.selectionList?.contains(data.data) == true
    }


    private fun changeSelectView(selectView: CheckBox) {
        selectView.isChecked = !selectView.isChecked
        if (!selectView.isChecked) {
            mediaBrowseData?.let {
                val selection = viewModel?.selection?.value
                if (selection != null) {
                    if (selection.selectionList.remove(it)) {
                        selection.fileByteSize -= it.fileSize()
                        viewModel?.selection?.postValue(selection)
                    }
                }
            }
        } else {
            mediaBrowseData?.let {
                val selection = viewModel?.selection?.value
                if (selection != null) {
                    selection.fileByteSize += it.fileSize()
                    selection.selectionList.add(it)
                    viewModel?.selection?.postValue(selection)
                }
            }
        }
    }

    private fun itemClick(v: View) {
        PermissionUtil.checkStorage(v.context) {
            if (it) {
                mediaBrowseData?.let {
                    if (it.msgSource is AmeGroupMessageDetail) {  //
                        groupItemClick(v, it.msgSource)
                    } else if (it.msgSource is MessageRecord) {  //
                        privateItemClick(v, it.msgSource)
                    }
                }
            }
        }
    }

    private fun groupItemClick(v: View, messageDetailRecord: AmeGroupMessageDetail) {
        val content = messageDetailRecord.message.content
        if (content is AmeGroupMessage.FileContent){
            ChatPreviewClickListener().onClick(v, messageDetailRecord)
        } else if(content is AmeGroupMessage.LinkContent){
            val contactProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
            contactProvider.discernLink(v.context, AmeURLUtil.getHttpUrl(content.url))
        }
    }

    private fun privateItemClick(v: View, messageRecord: MessageRecord) {
        if (messageRecord.isMediaMessage()){
            ChatPreviewClickListener().onClick(v, messageRecord)
        } else {
            val contactProvider = BcmRouter.getInstance().get(ARouterConstants.Provider.PROVIDER_CONTACTS_BASE).navigationWithCast<IContactModule>()
            contactProvider.discernLink(v.context, AmeURLUtil.getHttpUrl(messageRecord.body))
        }
    }

}