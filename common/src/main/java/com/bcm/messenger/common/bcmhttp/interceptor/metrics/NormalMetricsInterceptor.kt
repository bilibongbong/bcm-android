package com.bcm.messenger.common.bcmhttp.interceptor.metrics

import com.bcm.messenger.common.metrics.ReportUtil
import okhttp3.Request

/**
 * 
 */
class NormalMetricsInterceptor : MetricsInterceptor() {
    override fun onComplete(req: Request, succeed:Boolean, code:Int, duration: Long) {
        val url = req.url()
        if (succeed) {
            ReportUtil.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        } else {
            //lbs 
            ReportUtil.addNetworkReportData(url.host(), url.port(), req.method(), url.encodedPath(), code.toString(), duration)
        }
    }
}