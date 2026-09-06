package com.tertiaryinfotech.ailmstms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

class LmsViewModel(app:Application):AndroidViewModel(app) {
 val repo=(app as LmsApplication).repo
 var isDemo by mutableStateOf(false);private set
 fun enterDemo(){isDemo=true;role="learner";profile=Demo.profile;dashboard=Demo.dashboard(role);error=null}
 var profile by mutableStateOf<Profile?>(null);private set
 var dashboard by mutableStateOf(Dashboard(emptyList(),emptyList()));private set
 var role by mutableStateOf(repo.store.role);private set
 var busy by mutableStateOf(false);private set
 var error by mutableStateOf<String?>(null);private set
 var sent by mutableStateOf(false);private set
 var email by mutableStateOf("")
 var otp by mutableStateOf("")
 var pushEnabled by mutableStateOf(repo.store.pushEnabled);private set
 init { if(repo.store.token.isNotEmpty())run { profile=runCatching { Profile.parse(JSONObject(repo.store.profile)) }.getOrNull();refresh() } }
 private fun action(block:suspend ()->Unit) { if(busy)return;busy=true;error=null;viewModelScope.launch { try {block()}catch(e:CancellationException){throw e}catch(e:Exception){if(e is ApiException && e.status==401){repo.store.clear();profile=null;pushEnabled=false;sent=false;otp="";dashboard=Dashboard(emptyList(),emptyList())};error=if(e is java.net.UnknownHostException)"You appear to be offline. Check your connection and retry." else e.message?:"Something went wrong. Please try again."}finally{busy=false} } }
 fun sendOtp()=action { repo.sendOtp(email.trim().lowercase());sent=true }
 fun changeEmail(){sent=false;otp="";error=null}
 fun login()=action { profile=repo.login(email.trim().lowercase(),otp);role=repo.store.role;pushEnabled=repo.store.pushEnabled;otp="";dashboard=repo.dashboard() }
 fun refresh()=action { if(isDemo){dashboard=Demo.dashboard(role);return@action};dashboard=repo.dashboard(); if(repo.store.pushEnabled) PushSyncWorker.enqueue(getApplication()) }
 fun switchRole(value:String)=action { if(profile?.roles?.contains(value)==true) {if(isDemo){role=value;dashboard=Demo.dashboard(value);return@action};repo.store.role=value;role=value;dashboard=Dashboard(emptyList(),emptyList());dashboard=repo.dashboard()} }
 fun setPush(enabled:Boolean)=action { if(isDemo)return@action;repo.registerPush(enabled);pushEnabled=enabled; if(enabled)PushSyncWorker.enqueue(getApplication()) }
 fun logout()=action { if(!isDemo)runCatching {repo.logout()};getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancelAll();isDemo=false;profile=null;dashboard=Dashboard(emptyList(),emptyList());pushEnabled=false;sent=false;otp="" }
}
