package com.sakupdf.app.domain

data class PageRange(
    val start: Int,
    val end: Int
) {
    val isSinglePage: Boolean get() = start == end
    val count: Int get() = end - start + 1
    val pages: List<Int> get() = (start..end).toList()

    fun toFilenameLabel(): String {
        return if (isSinglePage) {
            String.format("page_%03d", start)
        } else {
            "pages_${start}-${end}"
        }
    }
}

sealed interface SplitRangeResult {
    data class Success(
        val subRanges: List<PageRange>,
        val uniquePages: List<Int>
    ) : SplitRangeResult

    data class Error(val message: String) : SplitRangeResult
}

object SplitRangeParser {

    fun parse(rangeString: String, totalPages: Int): SplitRangeResult {
        val trimmed = rangeString.trim()
        if (trimmed.isEmpty()) {
            return SplitRangeResult.Error("Rentang halaman tidak boleh kosong.")
        }

        if (totalPages <= 0) {
            return SplitRangeResult.Error("Dokumen tidak memiliki halaman yang valid.")
        }

        val rawTokens = trimmed.split(",")
        if (rawTokens.isEmpty()) {
            return SplitRangeResult.Error("Format rentang tidak valid. Contoh: 1-3, 5, 8-10.")
        }

        val parsedRanges = mutableListOf<PageRange>()

        for (rawToken in rawTokens) {
            val token = rawToken.trim()
            if (token.isEmpty()) {
                return SplitRangeResult.Error("Format rentang tidak valid: terdapat tanda koma berlebih.")
            }

            if (token.contains("-")) {
                val parts = token.split("-")
                if (parts.size != 2) {
                    return SplitRangeResult.Error("Format rentang '$token' tidak valid. Contoh yang benar: 1-3 atau 5.")
                }

                val startStr = parts[0].trim()
                val endStr = parts[1].trim()

                if (startStr.isEmpty() || endStr.isEmpty()) {
                    return SplitRangeResult.Error("Format rentang '$token' tidak lengkap. Contoh: 1-3.")
                }

                val start = startStr.toIntOrNull()
                val end = endStr.toIntOrNull()

                if (start == null || end == null) {
                    return SplitRangeResult.Error("Nomor halaman pada '$token' harus berupa angka bulat positif.")
                }

                if (start < 1) {
                    return SplitRangeResult.Error("Nomor halaman awal ($start) pada rentang '$token' minimal harus 1.")
                }

                if (start > totalPages) {
                    return SplitRangeResult.Error("Halaman awal ($start) pada rentang '$token' melebihi total halaman ($totalPages).")
                }

                if (end > totalPages) {
                    return SplitRangeResult.Error("Halaman akhir ($end) pada rentang '$token' melebihi total halaman ($totalPages).")
                }

                if (start > end) {
                    return SplitRangeResult.Error("Rentang '$token' terbalik. Halaman awal ($start) tidak boleh lebih besar dari halaman akhir ($end).")
                }

                parsedRanges.add(PageRange(start, end))
            } else {
                val page = token.toIntOrNull()
                    ?: return SplitRangeResult.Error("Nomor halaman '$token' harus berupa angka bulat positif.")

                if (page < 1) {
                    return SplitRangeResult.Error("Nomor halaman ($page) minimal harus 1.")
                }

                if (page > totalPages) {
                    return SplitRangeResult.Error("Halaman $page melebihi total halaman dokumen ($totalPages).")
                }

                parsedRanges.add(PageRange(page, page))
            }
        }

        if (parsedRanges.isEmpty()) {
            return SplitRangeResult.Error("Tidak ada halaman yang dipilih untuk dipisahkan.")
        }

        // Deduplicate pages while preserving requested order
        val seenPages = mutableSetOf<Int>()
        val uniquePages = mutableListOf<Int>()
        for (range in parsedRanges) {
            for (p in range.pages) {
                if (!seenPages.contains(p)) {
                    seenPages.add(p)
                    uniquePages.add(p)
                }
            }
        }

        return SplitRangeResult.Success(
            subRanges = parsedRanges,
            uniquePages = uniquePages
        )
    }
}
