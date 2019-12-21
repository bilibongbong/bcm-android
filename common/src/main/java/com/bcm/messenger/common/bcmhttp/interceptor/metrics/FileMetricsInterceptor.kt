package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.metrics.API_TOPIC_ALIYUN
import com.bcm.messenger.common.metrics.API_TOPIC_AWS_S3
import com.bcm.messenger.common.metrics.API_TOPIC_BS2
import com.bcm.messenger.common.metrics.ReportUtil
import okhttp3.Request

/**
 * 
 */
class FileMetricsInterceptor : MetricsInterceptor() {
    override fun onComplete(req: Request, succeed: Boolean, code: Int, duration: Long) {
        val url = req.url()
        val topic = when {
            // bs2dl belongs to bs2 topic
            url.host().contains("ameim.bs2dl.yy.com") -> API_TOPIC_BS2
            // Aliyun belongs to aliyun topic
            url.host().contains("aliyuncs.com") -> API_TOPIC_ALIYUN
            // AWS or newer servers belongs to aws topic
            else -> API_TOPIC_AWS_S3
        }

        // Files report using "reqMethod_host" format.
        if (succeed) {
            ReportUtil.addCustomNetworkReportData(topic
                    , url.host()
                    , 0
                    , req.method()
                    , url.host()
                    , code.toString()
                    , duration)
        } else {
            ReportUtil.addCustomNetworkReportData(topic
                    , url.host()
                    , 0
                    , req.method()
                    , url.host()
                    , code.toString()
                    , duration)
        }
    }
}