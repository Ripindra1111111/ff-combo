package com.kanha.ffcombo

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object KeyAuth {
    private const val APP_NAME = "ff-combo"
    private const val OWNER_ID = "a4cXspglCR"
    private const val SECRET = "f1bcc1f95a120571a677a0642f485890913f42196e37ad33a16f2eb24cef55ca"
    private const val VERSION = "1.0"
    private const val API_URL = "https://keyauth.win/api/1.2/"

    private val client = OkHttpClient()
    private var sessionId = ""
    
    fun init(): Boolean {
        val json = JSONObject().apply {
            put("type", "init")
            put("ver", VERSION)
            put("name", APP_NAME)
            put("ownerid", OWNER_ID)
        }
        
        val request = Request.Builder()
            .url(API_URL)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = JSONObject(response.body?.string() ?: "")
                if (body.getBoolean("success")) {
                    sessionId = body.getString("sessionid")
                    true
                } else false
            }
        } catch (e: Exception) { false }
    }

    fun login(key: String): String? {
        if (sessionId.isEmpty()) return "Lỗi kết nối server"
        
        val json = JSONObject().apply {
            put("type", "license")
            put("key", key)
            put("sessionid", sessionId)
            put("name", APP_NAME)
            put("ownerid", OWNER_ID)
        }
        
        val request = Request.Builder()
            .url(API_URL)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return "Lỗi HTTP"
                val body = JSONObject(response.body?.string() ?: "")
                if (body.getBoolean("success")) null
                else body.optString("message", "Key sai hoặc hết hạn")
            }
        } catch (e: Exception) { "Lỗi mạng" }
    }
}
