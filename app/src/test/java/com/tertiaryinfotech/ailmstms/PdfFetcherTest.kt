package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.NotPdfException
import com.tertiaryinfotech.ailmstms.data.PdfFetcher
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfFetcherTest {
    @get:Rule
    val folder = TemporaryFolder()
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    // The mock server speaks http, so the https rule is switched off here and tested on its own below.
    private fun fetcher(max: Long = 1024) = PdfFetcher(folder.root, maxBytes = max, httpsOnly = false)
    private fun pdf(size: Int = 64) = Buffer().writeUtf8("%PDF-1.7\n").write(ByteArray(size))

    private fun expectNotPdf(block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("Expected the link to be rejected")
        } catch (_: NotPdfException) {
        }
    }

    @Test
    fun aPdfIsDownloadedToTheCacheWithoutAnyCredentials() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody(pdf()))
        val progress = mutableListOf<Float?>()
        val file = fetcher().fetch(listOf(server.url("/a.pdf").toString())) { progress.add(it) }
        assertTrue(file.isFile)
        assertEquals("%PDF", file.readBytes().decodeToString(0, 4))
        assertEquals(1f, progress.last())
        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertEquals(listOf(file.name), folder.root.list()!!.toList())
    }

    @Test
    fun aFreshCopyIsReusedInsteadOfDownloadedAgain() = runBlocking {
        server.enqueue(MockResponse().setBody(pdf()))
        val url = server.url("/a.pdf").toString()
        val first = fetcher().fetch(listOf(url))
        val second = fetcher().fetch(listOf(url))
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun webPagesAreRejected() {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody("<html>Sign in</html>"))
        expectNotPdf { fetcher().fetch(listOf(server.url("/view").toString())) }
        // A page mislabelled as a download is still caught by its first bytes.
        server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody("<!DOCTYPE html><html>virus scan warning</html>"))
        expectNotPdf { fetcher().fetch(listOf(server.url("/uc").toString())) }
        server.enqueue(MockResponse().setResponseCode(404).setBody("gone"))
        expectNotPdf { fetcher().fetch(listOf(server.url("/gone").toString())) }
        assertTrue(folder.root.list()!!.isEmpty())
    }

    @Test
    fun filesOverTheCapAreRejectedEvenWithoutADeclaredLength() {
        server.enqueue(MockResponse().setBody(pdf(4096)))
        expectNotPdf { fetcher(max = 1024).fetch(listOf(server.url("/big.pdf").toString())) }
        server.enqueue(MockResponse().setChunkedBody(pdf(4096), 512))
        expectNotPdf { fetcher(max = 1024).fetch(listOf(server.url("/big-chunked.pdf").toString())) }
        assertTrue("no partial file is left behind", folder.root.list()!!.isEmpty())
    }

    @Test
    fun theNextCandidateIsTriedWhenTheFirstIsAnInterstitial() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Google Drive can't scan this file</html>"))
        server.enqueue(MockResponse().setBody(pdf()))
        val file = fetcher().fetch(listOf(server.url("/uc?id=1").toString(), server.url("/download?id=1").toString()))
        assertTrue(file.length() > 4)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun redirectsAreFollowedToTheFile() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(303).setHeader("Location", server.url("/real.pdf").toString()))
        server.enqueue(MockResponse().setBody(pdf()))
        assertTrue(fetcher().fetch(listOf(server.url("/export").toString())).isFile)
    }

    @Test
    fun onlyHttpsLinksAreFetchedInTheApp() {
        val strict = PdfFetcher(folder.root)
        expectNotPdf { strict.fetch(listOf(server.url("/a.pdf").toString())) }
        assertEquals(0, server.requestCount)
        assertFalse(folder.root.list()!!.any { it.endsWith(".pdf") })
    }
}
