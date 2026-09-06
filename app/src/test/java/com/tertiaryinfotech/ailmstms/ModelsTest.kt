package com.tertiaryinfotech.ailmstms
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class ModelsTest {
 @Test fun roleFilterRejectsAdministrativeAccounts(){assertEquals(emptyList<String>(),Profile.parse(JSONObject("""{"id":"1","roles":["admin","finance","developer"]}""")).roles);assertEquals(listOf("trainer"),Profile.parse(JSONObject("""{"roles":["admin","trainer"]}""")).roles)}
 @Test fun resourceLinksRejectUnsafeSchemes(){assertFalse(isSafeLink("javascript:alert(1)"));assertFalse(isSafeLink("http://example.com"));assertFalse(isSafeLink("https://user:pass@example.com"));assertTrue(isSafeLink("https://drive.google.com/file/d/example/view"))}
 @Test fun singaporeTimesAreIndependentOfPhoneTimezone(){val s=ClassSession.parse(JSONObject("""{"id":"1","startDate":"2026-09-10","endDate":"2026-09-10","startTime":"09:00","endTime":"17:00"}"""))!!;assertEquals("2026-09-10T01:00:00Z",s.startsAt.toString());assertEquals("2026-09-10T09:00:00Z",s.endsAt.toString())}
 @Test fun invalidSessionIsSkipped(){assertNull(ClassSession.parse(JSONObject("""{"startDate":"invalid"}""")))}
}
