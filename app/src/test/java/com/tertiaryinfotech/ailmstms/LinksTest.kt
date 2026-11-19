package com.tertiaryinfotech.ailmstms

import com.tertiaryinfotech.ailmstms.data.Brand
import com.tertiaryinfotech.ailmstms.data.isDriveLink
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
