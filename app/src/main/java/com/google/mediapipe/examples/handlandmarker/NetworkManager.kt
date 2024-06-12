package com.google.mediapipe.examples.handlandmarker

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.examples.handlandmarker.fragment.RegisterFragment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class NetworkManager private constructor(context: Context) {

    private val client: OkHttpClient
    private val gson= com.google.gson.Gson()

    init {
        client = createOkHttpClient(context)
    }

    companion object {
        const val BASE_URL="https://150.158.130.111:5000"
        const val PREFIX_BASE64="data:image/png;base64,"
        @Volatile
        private var INSTANCE: NetworkManager? = null

        fun getInstance(context: Context): NetworkManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkManager(context).also { INSTANCE = it }
            }
        }
    }

    data class RegisterRequest(
        val username: String,
        val left_images:List<String>,
        val right_images:List<String>)

    private fun createOkHttpClient(context: Context): OkHttpClient {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val inputStream = context.assets.open("cert.pem")
        val certificate = inputStream.use {
            certificateFactory.generateCertificate(it) as X509Certificate
        }

        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca", certificate)
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        val trustManagers = trustManagerFactory.trustManagers

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, java.security.SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun postImage(endpoint: String, image: Bitmap, callback: Callback) {
        val url="$BASE_URL$endpoint"
        // convert bitmap to base64
        val byteArrayOutputStream = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        val base64Image = android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
        val base64Full="$PREFIX_BASE64$base64Image"
        // create request
        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", null, base64Full.toRequestBody("text/plain".toMediaType()))
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("Content-Type", "multipart/form-data; boundary=1d78c868-e5b2-40c4-8a92-653584190dd3")
            .build()

        client.newCall(request).enqueue(callback)
    }

    fun postImages(endpoint:String,images:RegisterFragment.PalmPrintRegistrationData,callback: Callback) {
        val url="$BASE_URL$endpoint"

        val leftImagesBase64=images.leftHandPrints.map{bitmap->
            val byteArrayOutputStream=ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG,100,byteArrayOutputStream)
            val byteArray=byteArrayOutputStream.toByteArray()
            val base64Image=android.util.Base64.encodeToString(byteArray,android.util.Base64.DEFAULT)
            "$PREFIX_BASE64$base64Image"
        }
        val rightImagesBase64=images.rightHandPrints.map{bitmap->
            val byteArrayOutputStream=ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG,100,byteArrayOutputStream)
            val byteArray=byteArrayOutputStream.toByteArray()
            val base64Image=android.util.Base64.encodeToString(byteArray,android.util.Base64.DEFAULT)
            "$PREFIX_BASE64$base64Image"
        }
        // create request
        val registrationRequest=RegisterRequest(
            username = images.userName,
            left_images = leftImagesBase64,
            right_images = rightImagesBase64
        )
        val jsonRequestBody=gson.toJson(registrationRequest).toRequestBody("application/json".toMediaType())


        val request = Request.Builder()
            .url(url)
            .post(jsonRequestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(callback)
    }
}
