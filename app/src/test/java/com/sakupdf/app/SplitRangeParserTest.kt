package com.sakupdf.app

import com.sakupdf.app.domain.PageRange
import com.sakupdf.app.domain.SplitRangeParser
import com.sakupdf.app.domain.SplitRangeResult
import org.junit.Assert.*
import org.junit.Test

class SplitRangeParserTest {

    @Test
    fun `test standard range parsing 1-3, 5, 8-10`() {
        val result = SplitRangeParser.parse("1-3, 5, 8-10", 15)
        assertTrue(result is SplitRangeResult.Success)
        val success = result as SplitRangeResult.Success

        assertEquals(3, success.subRanges.size)
        assertEquals(PageRange(1, 3), success.subRanges[0])
        assertEquals(PageRange(5, 5), success.subRanges[1])
        assertEquals(PageRange(8, 10), success.subRanges[2])

        assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), success.uniquePages)
    }

    @Test
    fun `test duplicate page removal while preserving requested order`() {
        val result = SplitRangeParser.parse("3, 1-3, 5, 2, 4", 10)
        assertTrue(result is SplitRangeResult.Success)
        val success = result as SplitRangeResult.Success

        // Pages in order: 3, 1, 2, 3(dup), 5, 2(dup), 4
        // Preserving requested order without duplicates: 3, 1, 2, 5, 4
        assertEquals(listOf(3, 1, 2, 5, 4), success.uniquePages)
    }

    @Test
    fun `test single page and single range`() {
        val r1 = SplitRangeParser.parse("4", 10)
        assertTrue(r1 is SplitRangeResult.Success)
        assertEquals(listOf(4), (r1 as SplitRangeResult.Success).uniquePages)

        val r2 = SplitRangeParser.parse("2-5", 10)
        assertTrue(r2 is SplitRangeResult.Success)
        assertEquals(listOf(2, 3, 4, 5), (r2 as SplitRangeResult.Success).uniquePages)
    }

    @Test
    fun `test bounds validation`() {
        // Exceeds total pages
        val r1 = SplitRangeParser.parse("1-5, 12", 10)
        assertTrue(r1 is SplitRangeResult.Error)
        assertTrue((r1 as SplitRangeResult.Error).message.contains("melebihi total halaman"))

        // End of range exceeds total pages
        val r2 = SplitRangeParser.parse("8-15", 10)
        assertTrue(r2 is SplitRangeResult.Error)
        assertTrue((r2 as SplitRangeResult.Error).message.contains("melebihi total halaman"))

        // Zero or negative page
        val r3 = SplitRangeParser.parse("0-5", 10)
        assertTrue(r3 is SplitRangeResult.Error)
        assertTrue((r3 as SplitRangeResult.Error).message.contains("minimal harus 1"))
    }

    @Test
    fun `test inverted range rejected`() {
        val result = SplitRangeParser.parse("5-2", 10)
        assertTrue(result is SplitRangeResult.Error)
        assertTrue((result as SplitRangeResult.Error).message.contains("terbalik"))
    }

    @Test
    fun `test syntax validation errors`() {
        // Blank
        assertTrue(SplitRangeParser.parse("", 10) is SplitRangeResult.Error)
        assertTrue(SplitRangeParser.parse("   ", 10) is SplitRangeResult.Error)

        // Non-numeric
        val r1 = SplitRangeParser.parse("1-3, abc", 10)
        assertTrue(r1 is SplitRangeResult.Error)

        // Invalid format multiple hyphens
        val r2 = SplitRangeParser.parse("1-2-3", 10)
        assertTrue(r2 is SplitRangeResult.Error)

        // Trailing hyphen
        val r3 = SplitRangeParser.parse("1-", 10)
        assertTrue(r3 is SplitRangeResult.Error)

        // Double comma
        val r4 = SplitRangeParser.parse("1-3,, 5", 10)
        assertTrue(r4 is SplitRangeResult.Error)
    }

    @Test
    fun `test filename labels`() {
        assertEquals("page_001", PageRange(1, 1).toFilenameLabel())
        assertEquals("page_042", PageRange(42, 42).toFilenameLabel())
        assertEquals("pages_1-3", PageRange(1, 3).toFilenameLabel())
        assertEquals("pages_8-10", PageRange(8, 10).toFilenameLabel())
    }
}
