package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.Brand
import com.tertiaryinfotech.ailmstms.data.DocumentDownloader
import com.tertiaryinfotech.ailmstms.data.Roles
import com.tertiaryinfotech.ailmstms.data.downloadSource
import com.tertiaryinfotech.ailmstms.data.isDriveLink
import com.tertiaryinfotech.ailmstms.data.money
import com.tertiaryinfotech.ailmstms.data.pdfCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    private val id = "1AbC_dEf-GhIjKlMnOpQrStUvWxYz012345"

    @Test
    fun driveFileLinksBecomeDirectDownloads() {
        val expected = "https://drive.google.com/uc?export=download&id=$id"
        assertEquals(expected, pdfCandidates("https://drive.google.com/file/d/$id/view?usp=sharing").first())
        assertEquals(expected, pdfCandidates("https://drive.google.com/file/d/$id/preview").first())
        assertEquals(expected, pdfCandidates("https://drive.google.com/open?id=$id").first())
        assertEquals(expected, pdfCandidates("https://drive.google.com/uc?id=$id&export=view").first())
        // The second candidate serves files that are too large for Drive's virus scan.
        assertEquals(2, pdfCandidates("https://drive.google.com/file/d/$id/view").size)
        assertTrue(pdfCandidates("https://drive.google.com/file/d/$id/view")[1].startsWith("https://drive.usercontent.google.com/download?id=$id"))
    }

    @Test
    fun downloadLinksPointAtTheFileItself() {
        val drive = "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t"
        assertEquals(drive, downloadSource("https://drive.google.com/file/d/$id/view?usp=sharing"))
        assertEquals(drive, downloadSource("https://drive.google.com/open?id=$id"))
        assertEquals("https://docs.google.com/presentation/d/$id/export?format=pdf", downloadSource("https://docs.google.com/presentation/d/$id/edit#slide=id.p"))
        assertEquals("https://docs.google.com/spreadsheets/d/$id/export?format=pdf", downloadSource("https://docs.google.com/spreadsheets/d/$id/edit"))
        assertEquals("https://example.com/files/guide.pdf", downloadSource("https://example.com/files/guide.pdf"))
        // A folder is not one file, and an unsafe link is never fetched.
        assertEquals(null, downloadSource("https://drive.google.com/drive/folders/$id"))
        assertEquals(null, downloadSource("https://drive.google.com/drive/u/0/folders/$id?usp=sharing"))
        assertEquals(null, downloadSource("http://example.com/guide.pdf"))
    }

    @Test
    fun downloadedFilesGetAUsableName() {
        assertEquals("Learner Guide v2.pdf", DocumentDownloader.fileName("Guide", """attachment; filename="Learner Guide v2.pdf"""", "application/pdf", "https://x.example/a"))
        assertEquals("Lab 1.pptx", DocumentDownloader.fileName("Guide", "attachment; filename*=UTF-8''Lab%201.pptx", "application/octet-stream", "https://x.example/a"))
        assertEquals("Learner slides.pdf", DocumentDownloader.fileName("Learner slides", "", "application/pdf", "https://docs.google.com/presentation/d/x/export?format=pdf"))
        assertEquals("Lab files.zip", DocumentDownloader.fileName("Lab files", "", "application/zip", "https://example.com/files/labs.zip"))
        assertEquals("a-b-c.pdf", DocumentDownloader.fileName("a/b:c", "", "application/pdf", "https://example.com/x"))
    }

    @Test
    fun oneRolePerSignInExceptForTheOwner() {
        assertEquals("trainer", Roles.resolve("trainer", listOf("learner", "trainer")))
        assertEquals("learner", Roles.resolve("trainer", listOf("learner")))
        assertEquals("trainer", Roles.resolve("learner", listOf("trainer")))
        // A session from before the sign-in choice keeps the trainer view when the account teaches.
        assertEquals("trainer", Roles.legacy(listOf("learner", "trainer")))
        assertEquals("learner", Roles.legacy(listOf("learner")))
        assertEquals(listOf("trainer"), Roles.available("someone@example.com", listOf("learner", "trainer"), "trainer"))
        // The owner account can view the app as every role, as on iOS (the server still checks each request).
        assertEquals(listOf("learner", "trainer", "developer", "admin"), Roles.available(" AngCH@tertiaryinfotech.com", listOf("trainer", "learner"), "trainer"))
        assertEquals(listOf("learner", "trainer", "developer", "admin"), Roles.available("angch@tertiaryinfotech.com", listOf("learner"), "learner"))
        assertEquals("S$3,350.00", money(3350.0))
    }

    @Test
    fun googleSlidesAndDocsBecomePdfExports() {
        assertEquals(listOf("https://docs.google.com/presentation/d/$id/export/pdf"), pdfCandidates("https://docs.google.com/presentation/d/$id/edit#slide=id.p"))
        assertEquals(listOf("https://docs.google.com/document/d/$id/export?format=pdf"), pdfCandidates("https://docs.google.com/document/d/$id/edit?usp=sharing"))
    }

    @Test
    fun linksThatCannotBePdfsGoToTheBrowser() {
        assertTrue(pdfCandidates("https://drive.google.com/drive/folders/$id").isEmpty())
        assertTrue(pdfCandidates("https://docs.google.com/spreadsheets/d/$id/edit").isEmpty())
        assertTrue(pdfCandidates("https://docs.google.com/presentation/d/e/2PACX-published/pub").isEmpty())
        assertTrue(pdfCandidates("https://sites.google.com/view/course").isEmpty())
        assertTrue(pdfCandidates("https://drive.google.com/file/d/../view").isEmpty())
    }

    @Test
    fun unsafeLinksAreNeverDownloaded() {
        assertTrue(pdfCandidates("http://drive.google.com/file/d/$id/view").isEmpty())
        assertTrue(pdfCandidates("javascript:alert(1)").isEmpty())
        assertTrue(pdfCandidates("https://user:pw@example.com/a.pdf").isEmpty())
        assertTrue(pdfCandidates("file:///sdcard/a.pdf").isEmpty())
        assertTrue(pdfCandidates("").isEmpty())
    }

    @Test
    fun otherHttpsLinksAreTriedAsTheyAre() {
        assertEquals(listOf("https://example.com/files/guide.pdf"), pdfCandidates("https://example.com/files/guide.pdf"))
        assertEquals(listOf("https://example.com/page"), pdfCandidates("https://example.com/page"))
    }

    @Test
    fun onlyGoogleDriveLinksAreRecordedAsSubmissions() {
        assertTrue(isDriveLink("https://drive.google.com/file/d/$id/view?usp=drivesdk"))
        assertFalse(isDriveLink("https://drive.google.com.evil.example/file/d/$id/view"))
        assertFalse(isDriveLink("http://drive.google.com/file/d/$id/view"))
    }

    @Test
    fun brandColourIsUsedOnlyWhenTextStaysReadable() {
        assertEquals(Brand.DEFAULT, Brand.palette(null))
        assertEquals(Brand.DEFAULT, Brand.palette("blue"))
        assertEquals(Brand.DEFAULT, Brand.palette("#3B82F6")) // the server default: white text would fail AA
        assertEquals(Brand.DEFAULT, Brand.palette("#FFD400")) // too light
        assertEquals(Brand.DEFAULT, Brand.palette("#333333")) // a grey is not a brand colour
        val green = Brand.palette("#0B6B3A")
        assertEquals(0x0B6B3A, green.primary)
        assertTrue(Brand.contrast(green.primary, 0xFFFFFF) >= 4.5)
        assertTrue(Brand.contrast(green.onDark, 0x0A1020) >= 7.0)
        assertTrue(Brand.luminance(green.deep) < Brand.luminance(green.primary))
        // The fixed palette itself meets the same bar.
        assertTrue(Brand.contrast(Brand.BLUE, 0xFFFFFF) >= 4.5)
        assertTrue(Brand.contrast(Brand.LIGHT_BLUE, 0x0A1020) >= 7.0)
        assertEquals(21.0, Brand.contrast(0x000000, 0xFFFFFF), 0.01)
    }
}
