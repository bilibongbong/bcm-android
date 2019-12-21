package com.bcm.messenger.me.ui.scan

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.BaseFragment
import com.bcm.messenger.common.core.AmeFileUploader
import com.bcm.messenger.common.core.RecipientProfileLogic
import com.bcm.messenger.common.recipients.Recipient
import com.bcm.messenger.common.recipients.RecipientModifiedListener
import com.bcm.messenger.common.ui.popup.AmePopup
import com.bcm.messenger.common.utils.BcmFileUtils
import com.bcm.messenger.common.utils.createScreenShot
import com.bcm.messenger.common.utils.dp2Px
import com.bcm.messenger.me.BuildConfig
import com.bcm.messenger.me.R
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.QREncoder
import com.bcm.messenger.utility.QuickOpCheck
import com.bcm.messenger.utility.logger.ALog
import com.bcm.route.api.BcmRouter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.me_fragment_my_qr_code.*
import java.io.File

/**
 * Created by wjh on 2019/7/3
 */
class MyQRFragment : BaseFragment(), RecipientModifiedListener {

    private val TAG = "MyQRFragment"
    private lateinit var recipient: Recipient

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.me_fragment_my_qr_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recipient = Recipient.fromSelf(AppContextHolder.APP_CONTEXT, true)
        recipient.addListener(this)

        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        recipient.removeListener(this)
    }

    private fun initView() {
        setSelfData()

        qr_code_forward.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            forwardToChats()
        }

        qr_code_share.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            shareToOtherApp()
        }

        qr_code_save.setOnClickListener {
            if (QuickOpCheck.getDefault().isQuick) {
                return@setOnClickListener
            }
            saveQRCode()
        }
    }

    private fun setSelfData() {
        qr_code_avatar.showPrivateAvatar(recipient)
        qr_code_name.text = recipient.name
        initQrCode(recipient)
    }

    private fun initQrCode(recipient: Recipient?) {
        if (recipient != null) {
            if (recipient.isResolving) {
                return
            }
            val shortLink = recipient.privacyProfile.shortLink
            if (!shortLink.isNullOrEmpty()) {
                Observable.create<Bitmap> {
                    ALog.d(TAG, "initQrCode: $shortLink")
                    val qrEncoder = QREncoder(shortLink, dimension = 250.dp2Px(), charset = "utf-8")
                    val bitmap = qrEncoder.encodeAsBitmap()
                    it.onNext(bitmap)
                    it.onComplete()
                }.subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            qr_code_image?.setImageBitmap(it)
                        }, {
                            ALog.e(TAG, "initQrCode error", it)
                            qr_code_image?.setImageDrawable(null)
                        })
            }else {
                RecipientProfileLogic.updateShareLink(AppContextHolder.APP_CONTEXT, recipient) {

                }
            }
        }
    }

    private fun saveQRCode() {
        val bitmap = qr_code_layout.createScreenShot()
        Observable.create<String> {
            val path = BcmFileUtils.saveBitmap2File(bitmap, "BCM-QR-Code.jpg", AmeFileUploader.DCIM_DIRECTORY)
            if (path == null) {
                it.onError(Exception("Save QR code error"))
                return@create
            }
            it.onNext(path)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    AmePopup.result.failure(activity, getString(R.string.me_save_fail), true)
                }
                .subscribe {
                    MediaScannerConnection.scanFile(activity, arrayOf(it), arrayOf(BcmFileUtils.IMAGE_PNG), null)
                    AmePopup.result.succeed(activity, getString(R.string.me_save_success), true)
                }
    }

    private fun forwardToChats() {
        val bitmap = qr_code_layout.createScreenShot()
        Observable.create<String> {
            val path = BcmFileUtils.saveBitmap2File(bitmap, "BCM-QR-Code.jpg")
            if (path == null) {
                it.onError(RuntimeException("Save QR code error"))
                return@create
            }
            it.onNext(path)
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { }
                .subscribe {
                    BcmRouter.getInstance().get(ARouterConstants.Activity.FORWARD).putString("__uri", it).navigation()
                }
    }

    private fun shareToOtherApp() {
        val bitmap = qr_code_layout.createScreenShot()
        Observable.create<Uri> {
            val path = BcmFileUtils.saveBitmap2File(bitmap, "BCM-QR-Code.jpg")
            if (path == null) {
                it.onError(RuntimeException("Save QR code error"))
                return@create
            }
            it.onNext(FileProvider.getUriForFile(AppContextHolder.APP_CONTEXT, BuildConfig.BCM_APPLICATION_ID + ".fileprovider", File(path)))
            it.onComplete()
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError { }
                .subscribe {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, it)
                    }
                    val shareIntent = Intent.createChooser(intent, getString(R.string.me_backup_share_to))
                    startActivity(shareIntent)
                }
    }

    override fun onModified(recipient: Recipient) {
        this.recipient = recipient
        setSelfData()
    }
}