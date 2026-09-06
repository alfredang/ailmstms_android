package com.tertiaryinfotech.ailmstms

import org.json.JSONObject
import org.json.JSONArray
import java.time.*
import java.time.format.DateTimeFormatter

fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONObject.text(key:String) = if(isNull(key)) "" else optString(key,"")
data class Profile(val id:String,val name:String,val email:String,val roles:List<String>) {
 companion object { fun parse(o:JSONObject)=Profile(o.text("id"),o.text("fullName"),o.text("email"),(o.optJSONArray("roles")?:JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) }.filter { it in listOf("learner","trainer") } }) }
}
data class Course(val id:String,val title:String,val code:String,val start:String,val end:String,val format:String,val venue:String,val resources:List<Pair<String,String>>) {
 companion object { fun parse(o:JSONObject)=Course(o.text("id"),o.text("title"),o.text("code"),o.text("startDate"),o.text("endDate"),o.text("format"),o.text("venue"),listOf("Slides" to "slidesURL","Learner guide" to "guideURL","Activities" to "activitiesURL","Trainer slides" to "trainerSlidesURL").map { it.first to o.text(it.second) }.filter { isSafeLink(it.second) }) }
}
fun isSafeLink(url:String)=runCatching { val u=java.net.URI(url);u.scheme=="https" && !u.host.isNullOrBlank() && u.userInfo==null }.getOrDefault(false)
data class ClassSession(val id:String,val courseId:String,val title:String,val date:LocalDate,val endDate:LocalDate,val time:String,val endTime:String,val subtitle:String,val venue:String,val format:String,val meeting:String) {
 val startsAt:Instant get()=date.atTime(runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(9,0))).atZone(ZoneId.of("Asia/Singapore")).toInstant()
 val endsAt:Instant get()=endDate.atTime(runCatching { LocalTime.parse(endTime) }.getOrDefault(LocalTime.of(18,0))).atZone(ZoneId.of("Asia/Singapore")).toInstant()
 val whenText:String get()=date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))+" · "+time.take(5)+"–"+endTime.take(5)+" SGT"
 companion object { fun parse(o:JSONObject):ClassSession?=runCatching { ClassSession(o.text("id"),o.text("courseID"),o.text("title"),LocalDate.parse(o.text("startDate").take(10)),LocalDate.parse(o.text("endDate").ifEmpty { o.text("startDate") }.take(10)),o.text("startTime"),o.text("endTime"),o.text("subtitle"),o.text("venue"),o.text("format"),o.text("meetingURL")) }.getOrNull() }
}
data class Dashboard(val courses:List<Course>,val sessions:List<ClassSession>) {
 companion object { fun parse(o:JSONObject)=Dashboard((o.optJSONArray("courses")?:JSONArray()).objects().map(Course::parse),(o.optJSONArray("sessions")?:JSONArray()).objects().mapNotNull(ClassSession::parse).sortedBy { it.startsAt }) }
}
