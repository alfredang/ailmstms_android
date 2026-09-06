package com.tertiaryinfotech.ailmstms

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status:Int,message:String):IOException(message)
class SessionStore(context:Context) {
 private val prefs=EncryptedSharedPreferences.create(context,"secure_session",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 var token:String get()=prefs.getString("token","")!!;set(v){prefs.edit().putString("token",v).apply()}
 var profile:String get()=prefs.getString("profile","")!!;set(v){prefs.edit().putString("profile",v).apply()}
 var role:String get()=prefs.getString("role","learner")!!;set(v){prefs.edit().putString("role",v).apply()}
 var pushEnabled:Boolean get()=prefs.getBoolean("pushEnabled",false);set(v){prefs.edit().putBoolean("pushEnabled",v).apply()}
 var pushToken:String get()=prefs.getString("pushToken","")!!;set(v){prefs.edit().putString("pushToken",v).apply()}
 fun clear(){val device=pushToken;prefs.edit().clear().putString("pushToken",device).commit()}
}
class Repository(val store:SessionStore,private val base:String=BuildConfig.API_BASE_URL) {
 private val http=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).followRedirects(false).build()
 suspend fun request(path:String,body:JSONObject?=null,method:String=if(body==null)"GET" else "POST"):JSONObject=withContext(Dispatchers.IO) {
  val builder=Request.Builder().url(base+path).header("Accept","application/json")
  if(store.token.isNotEmpty())builder.header("Authorization","Bearer ${store.token}")
  if(method!="GET")builder.method(method,(body?:JSONObject()).toString().toRequestBody("application/json".toMediaType()))
  http.newCall(builder.build()).execute().use { response ->
   val raw=response.body?.string().orEmpty()
   val json=runCatching { JSONObject(raw) }.getOrElse { throw ApiException(response.code,"The service is temporarily unavailable. Please try again.") }
   if(!response.isSuccessful || (json.has("success")&&!json.optBoolean("success"))) throw ApiException(response.code,json.text("error").ifEmpty { "Unable to complete the request. Please try again." })
   json
  }
 }
 suspend fun sendOtp(email:String) { request("/api/android/send-otp",JSONObject().put("email",email)) }
 suspend fun login(email:String,otp:String):Profile {
  val data=request("/api/android/login",JSONObject().put("email",email).put("otp",otp).put("loginType","otp")).getJSONObject("data")
  val user=data.getJSONObject("user");val profile=Profile.parse(user)
  require(profile.roles.isNotEmpty()){ "This app is for learners and trainers only." }
  val token=data.text("token");require(token.startsWith("lms_")){"Invalid sign-in response. Please try again."}
  store.token=token;store.profile=user.toString();store.role=profile.roles.first()
  return profile
 }
 suspend fun dashboard()=Dashboard.parse(request("/api/android/dashboard?role=${store.role}"))
 suspend fun registerPush(enabled:Boolean) { if(store.pushToken.isNotEmpty())request("/api/android/device",JSONObject().put("token",store.pushToken).put("enabled",enabled));store.pushEnabled=enabled }
 suspend fun logout() {
  try { if(store.pushToken.isNotEmpty())request("/api/android/device",JSONObject().put("token",store.pushToken),"DELETE")
  request("/api/auth/logout",JSONObject()) } finally {store.clear()}
 }
}
