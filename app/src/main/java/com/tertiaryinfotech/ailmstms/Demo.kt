package com.tertiaryinfotech.ailmstms

import java.time.LocalDate
import java.time.ZoneId

// Public, fictional sample data only. Demo mode never authenticates or registers push tokens.
object Demo {
 val profile=Profile("demo","Alex Tan","alex@example.com",listOf("learner","trainer"))
 fun dashboard(role:String):Dashboard {
  val day=LocalDate.now(ZoneId.of("Asia/Singapore")).plusDays(3)
  val courses=listOf(Course("demo-ai","Building AI Applications","DEMO · AI-101",day.toString(),day.plusDays(1).toString(),"Classroom","Woods Square, Singapore",listOf("Slides" to "https://lms-tms.tertiaryinfotech.com/mobile/sample-slides.html","Learner guide" to "https://lms-tms.tertiaryinfotech.com/mobile/sample-guide.html","Activities" to "https://lms-tms.tertiaryinfotech.com/mobile/sample-activity.html") + (if(role=="trainer")listOf("Trainer slides" to "https://lms-tms.tertiaryinfotech.com/mobile/sample-trainer.html") else emptyList())),Course("demo-data","Data Analytics Essentials","DEMO · DATA-101",day.plusDays(7).toString(),day.plusDays(7).toString(),"Online","",emptyList()))
  val sessions=listOf(ClassSession("demo-1","demo-ai","Building AI Applications",day,day,"09:00","17:00",if(role=="trainer")"Teaching · Session 1" else "Session 1 · Foundations","Woods Square, Singapore","Classroom",""),ClassSession("demo-2","demo-ai","Building AI Applications",day.plusDays(1),day.plusDays(1),"09:00","17:00","Session 2 · Build and apply","Woods Square, Singapore","Classroom",""),ClassSession("demo-3","demo-data","Data Analytics Essentials",day.plusDays(7),day.plusDays(7),"09:00","17:00","Explore and visualise data","","Online",""))
  return Dashboard(courses,sessions)
 }
}
