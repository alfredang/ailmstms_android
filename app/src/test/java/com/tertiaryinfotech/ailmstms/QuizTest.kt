package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.ApiException
import com.tertiaryinfotech.ailmstms.data.DemoApi
import com.tertiaryinfotech.ailmstms.data.QuizLink
import com.tertiaryinfotech.ailmstms.data.QuizLoad
import com.tertiaryinfotech.ailmstms.data.QuizResult
import com.tertiaryinfotech.ailmstms.data.Repository
import com.tertiaryinfotech.ailmstms.data.parseQuizLink
import com.tertiaryinfotech.ailmstms.data.quizSubmission
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/** The quiz QR code: reading its link, reading the quiz, sending answers and showing the marking. */
class QuizTest {
    private val host = "lms-tms.tertiaryinfotech.com"
    private val course = "3f2b8a1c-9d4e-4f6a-8b7c-1a2b3c4d5e6f"

    private val quizJson = """{"success":true,"quiz":{"id":"quiz-1","courseId":"$course","title":"Day 1 recap","courseTitle":"Building AI Applications","courseCode":"TGS-1",
        "questions":[
          {"id":"q1","question":"One?","options":["A","B","C"],"correctIndex":0},
          {"id":"q2","question":"Two?","options":["A","B"],"correctIndex":1},
          {"id":"q3","question":"Three?","options":["A","B","C","D"],"correctIndex":3}]},
        "previous":PREVIOUS}"""

    private fun load(previous: String = "null") = QuizLoad.parse(JSONObject(quizJson.replace("PREVIOUS", previous)))

    @Test
    fun theQrLinkGivesTheCourseAndQuiz() {
        assertEquals(QuizLink(course, "quiz-1"), parseQuizLink("https://$host/quiz/$course/quiz-1", host))
        assertEquals(QuizLink(course, "1731999999999"), parseQuizLink("https://$host/quiz/$course/1731999999999", host))
        // A trailing slash, a query string, a fragment, an upper-case host and stray spaces are still the same quiz.
        assertEquals(QuizLink(course, "quiz-1"), parseQuizLink("  https://$host/quiz/$course/quiz-1/  ", host))
        assertEquals(QuizLink(course, "quiz-1"), parseQuizLink("https://$host/quiz/$course/quiz-1?utm=qr#top", host))
        assertEquals(QuizLink(course, "quiz-1"), parseQuizLink("https://LMS-TMS.tertiaryinfotech.com/quiz/$course/quiz-1", host))
        assertEquals(QuizLink(course, "quiz-1"), parseQuizLink("https://$host:443/quiz/$course/quiz-1", host))
    }

    @Test
    fun anythingElseIsNotAQuizLink() {
        assertNull(parseQuizLink(null, host))
        assertNull(parseQuizLink("", host))
        assertNull(parseQuizLink("http://$host/quiz/$course/quiz-1", host))
        assertNull(parseQuizLink("https://evil.example/quiz/$course/quiz-1", host))
        assertNull(parseQuizLink("https://$host.evil.example/quiz/$course/quiz-1", host))
        assertNull(parseQuizLink("https://user:pw@$host/quiz/$course/quiz-1", host))
        assertNull(parseQuizLink("https://$host:8443/quiz/$course/quiz-1", host))
        assertNull(parseQuizLink("https://$host/quiz/$course", host))
        assertNull(parseQuizLink("https://$host/quiz/", host))
        assertNull(parseQuizLink("https://$host/quiz/$course/quiz-1/extra", host))
        assertNull(parseQuizLink("https://$host/checkin/00000000000000000000000000000000", host))
        assertNull(parseQuizLink("https://$host/quiz/$course/..%2F..%2Fadmin", host))
        assertNull(parseQuizLink("https://$host/quiz/$course/a%20b", host))
        assertNull(parseQuizLink("00000000000000000000000000000000", host))
        assertNull(parseQuizLink("tiatms://quiz/$course/quiz-1", host))
    }

    @Test
    fun theQuizAndTheLastAttemptAreRead() {
        val first = load()
        assertEquals("Day 1 recap", first.quiz.title)
        assertEquals("TGS-1", first.quiz.courseCode)
        assertEquals(listOf("q1", "q2", "q3"), first.quiz.questions.map { it.id })
        assertEquals(listOf("A", "B", "C", "D"), first.quiz.questions[2].options)
        assertEquals(3, first.quiz.questions[2].correctIndex)
        assertNull(first.previous)

        val again = load("""{"score":2,"total":3,"completedAt":"2026-11-21T03:00:00.000Z"}""")
        assertEquals(2, again.previous!!.score)
        assertEquals(3, again.previous!!.total)
        assertEquals("2026-11-21T03:00:00.000Z", again.previous!!.completedAt)
    }

    @Test
    fun questionsThatCannotBeAnsweredAreLeftOut() {
        val loaded = QuizLoad.parse(JSONObject("""{"success":true,"quiz":{"id":7,"courseId":"$course","questions":[
            {"id":1,"question":"Numeric id?","options":["A","B"],"correctIndex":1},
            {"id":"q2","question":"One option only","options":["A"],"correctIndex":0},
            {"question":"No id","options":["A","B"],"correctIndex":0}]},"previous":null}"""))
        assertEquals("7", loaded.quiz.id)
        assertEquals("Quiz", loaded.quiz.title)
        assertEquals(listOf("1"), loaded.quiz.questions.map { it.id })
    }

    @Test
    fun theSubmissionCarriesTheUserAndOneOptionIndexForEachQuestion() {
        val body = quizSubmission("user-1", course, "quiz-1", mapOf("q1" to 0, "q2" to 1, "q3" to 2))
        assertEquals("user-1", body.getString("userId"))
        assertEquals(course, body.getString("courseId"))
        assertEquals("quiz-1", body.getString("quizId"))
        val answers = body.getJSONObject("answers")
        assertEquals(3, answers.length())
        // The server only counts whole numbers, so the indexes must not be sent as text.
        assertEquals(2, answers.get("q3"))
        assertTrue(body.toString().contains("\"q1\":0"))
    }

    @Test
    fun theMarkingIsMappedOntoTheQuestionsInOrder() {
        val quiz = load().quiz
        val result = QuizResult.parse(JSONObject("""{"success":true,"attemptId":"a1","completedAt":"2026-11-21T03:05:00.000Z","score":2,"total":3,
            "breakdown":[
              {"questionId":"q3","correct":false,"correctIndex":3,"selectedIndex":2},
              {"questionId":"q1","correct":true,"correctIndex":0,"selectedIndex":0},
              {"questionId":"q2","correct":true,"correctIndex":1,"selectedIndex":1}]}"""))
        assertEquals(2, result.score)
        assertEquals(3, result.total)
        assertEquals(67, result.percent)
        val review = result.review(quiz, mapOf("q1" to 0, "q2" to 1, "q3" to 2))
        assertEquals(listOf("q1", "q2", "q3"), review.map { it.question.id })
        assertEquals(listOf(true, true, false), review.map { it.correct })
        assertEquals(listOf(0, 1, 2), review.map { it.selectedIndex })
        assertEquals(listOf(0, 1, 3), review.map { it.correctIndex })
    }

    @Test
    fun anUnansweredOrUnmarkedQuestionIsShownAsWrongWithTheRightOption() {
        val quiz = load().quiz
        val result = QuizResult.parse(JSONObject("""{"success":true,"score":0,"total":3,"completedAt":"",
            "breakdown":[{"questionId":"q1","correct":false,"correctIndex":0,"selectedIndex":null}]}"""))
        val review = result.review(quiz, mapOf("q2" to 1, "q3" to 0))
        // q1: the server says it was not answered.
        assertNull(review[0].selectedIndex)
        assertFalse(review[0].correct)
        assertEquals(0, review[0].correctIndex)
        // q2 and q3 were not in the marking: the learner's pick and the quiz's own key are used.
        assertTrue(review[1].correct)
        assertFalse(review[2].correct)
        assertEquals(3, review[2].correctIndex)
        assertEquals(0, QuizResult(0, 0, "", emptyList()).percent)
    }

    @Test
    fun theApiCallsUseTheSharedQuizRoutesWithTheSessionToken() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val store = MemoryState().apply { token = "lms_abc"; profile = """{"id":"user-1","fullName":"Alex Tan","roles":["learner"]}""" }
            val repo = Repository(store, server.url("/").toString().trimEnd('/'))
            fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

            server.enqueue(json(quizJson.replace("PREVIOUS", "null")))
            assertEquals(3, repo.quiz(course, "quiz-1").quiz.questions.size)
            val get = server.takeRequest()
            assertEquals("GET", get.method)
            assertEquals("/api/courses/quiz?courseId=$course&quizId=quiz-1", get.path)
            assertEquals("Bearer lms_abc", get.getHeader("Authorization"))

            server.enqueue(json("""{"success":true,"attemptId":"a1","completedAt":"2026-11-21T03:05:00.000Z","score":1,"total":3,"breakdown":[]}"""))
            assertEquals(1, repo.submitQuiz(course, "quiz-1", mapOf("q1" to 0)).score)
            val post = server.takeRequest()
            assertEquals("POST", post.method)
            assertEquals("/api/courses/submit-quiz", post.path)
            assertEquals("Bearer lms_abc", post.getHeader("Authorization"))
            val sent = JSONObject(post.body.readUtf8())
            assertEquals("the attempt is always the signed-in user's own", "user-1", sent.getString("userId"))
            assertEquals(0, sent.getJSONObject("answers").getInt("q1"))

            // A quiz the trainer removed: the server's message is shown and the session is kept.
            server.enqueue(json("""{"success":false,"error":"Quiz not found"}""", 404))
            try {
                repo.quiz(course, "gone")
                fail("Expected an ApiException")
            } catch (e: ApiException) {
                assertEquals(404, e.status)
                assertEquals("Quiz not found", e.message)
                assertFalse(e.sessionEnded)
            }
            // An expired session signs the user out, so the quiz can reopen after sign-in.
            server.enqueue(json("""{"success":false,"error":"Unauthorized"}""", 401))
            try {
                repo.quiz(course, "quiz-1")
                fail("Expected an ApiException")
            } catch (e: ApiException) {
                assertTrue(e.sessionEnded)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun theDemoQuizIsMarkedInMemoryAndRemembersTheLastAttempt() = runBlocking {
        val api = DemoApi(today = { LocalDate.parse("2026-11-10") }, pause = 0)
        val first = api.quiz(DemoApi.QUIZ_COURSE_ID, DemoApi.QUIZ_ID)
        assertNull(first.previous)
        assertTrue(first.quiz.questions.size >= 3)
        assertTrue(first.quiz.questions.all { it.options.size >= 2 && it.correctIndex in it.options.indices })
        val allRight = first.quiz.questions.associate { it.id to it.correctIndex }
        val oneWrong = allRight + (first.quiz.questions[0].id to (first.quiz.questions[0].correctIndex + 1) % first.quiz.questions[0].options.size)
        val result = api.submitQuiz(DemoApi.QUIZ_COURSE_ID, DemoApi.QUIZ_ID, oneWrong)
        assertEquals(first.quiz.questions.size - 1, result.score)
        assertFalse(result.review(first.quiz, oneWrong)[0].correct)
        assertEquals(result.score, api.quiz(DemoApi.QUIZ_COURSE_ID, DemoApi.QUIZ_ID).previous!!.score)
        assertEquals(first.quiz.questions.size, api.submitQuiz(DemoApi.QUIZ_COURSE_ID, DemoApi.QUIZ_ID, allRight).score)
        // A scanned link with any ids still opens the sample quiz in the demo.
        assertEquals("other", api.quiz("c", "other").quiz.id)
    }
}
