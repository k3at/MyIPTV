package com.myiptv.app.data

import java.util.Locale

/**
 * Renders a provider's channel names in Arabic.
 *
 * Panels hand out names in whatever script the reseller typed - plenty of
 * Arabic-speaking customers get a Latin catalogue ("BEIN_NBA_4K",
 * "bein_Sports_SD1"). While the app is in Arabic those names are shown in
 * Arabic instead:
 *
 *  - a name the provider already sent in Arabic is left exactly as it is,
 *  - well-known brands and words come from [WORDS] ("beIN Sports" ->
 *    "بي إن سبورت", "MBC" -> "إم بي سي"),
 *  - anything else is transliterated letter by letter, so even an unheard-of
 *    name still comes out readable ("World" -> "وورلد"),
 *  - numbers and quality tags ("4K", "HD", "SD1") stay in Latin, and a token
 *    that mixes letters and digits ("S1E2") is never touched.
 *
 * This is only how a name is *shown*. The provider's text stays the
 * authoritative name for ids, favourites, sorting and the on-disk cache, so
 * switching language never invalidates anything.
 */
object ChannelNames {

    /** Whether names are currently rendered in Arabic. Set by every screen. */
    @Volatile
    var isArabic: Boolean = false
        private set

    /** Cached renderings; provider names repeat a lot across categories. */
    private val cache = HashMap<String, String>(256)

    private const val CACHE_LIMIT = 4096

    /** Called from `LocalizedActivity` once the screen knows its language. */
    fun setArabic(value: Boolean) {
        if (isArabic == value) return
        isArabic = value
        synchronized(cache) { cache.clear() }
    }

    /** The name to display: the Arabic rendering while Arabic is in use. */
    fun display(name: String): String {
        if (!isArabic || name.isEmpty()) return name
        synchronized(cache) { cache[name]?.let { return it } }
        val converted = toArabic(name)
        synchronized(cache) {
            if (cache.size >= CACHE_LIMIT) cache.clear()
            cache[name] = converted
        }
        return converted
    }

    /** True when [text] contains Arabic letters (used to pick a search mode). */
    fun isArabicText(text: String): Boolean = text.any { it in ARABIC_RANGE }

    /**
     * The Arabic rendering of [name] - always computed, regardless of the
     * current language, so the search box can match either script.
     */
    fun toArabic(name: String): String {
        // Nothing Latin in there: an Arabic name, a Cyrillic one, a bare number.
        if (name.none { it in 'a'..'z' || it in 'A'..'Z' }) return name

        // Providers separate words with underscores, dots and pipes; reading
        // "BEIN_NBA_4K" as "BEIN NBA 4K" is half the job.
        val tokens = name.replace(SEPARATORS, " ").trim().split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return name

        val out = StringBuilder(name.length + 16)
        var previous = ""
        var index = 0
        while (index < tokens.size) {
            // Two- and three-word brands first ("Al Jazeera", "Cartoon Network").
            var span = minOf(MAX_PHRASE_WORDS, tokens.size - index)
            var phrase: String? = null
            while (span >= 2) {
                val key = normalize((index until index + span).joinToString("") { tokens[it] })
                val hit = if (key.isEmpty()) null else WORDS[key]
                if (hit != null) {
                    phrase = hit
                    break
                }
                span--
            }

            val piece = phrase ?: tokenToArabic(tokens[index])
            index += if (phrase != null) span else 1
            if (piece.isEmpty()) continue

            // The definite article glues onto the word it belongs to, so
            // "Al Jazeera" reads as "الجزيرة" rather than "ال جزيرة".
            if (previous.isNotEmpty() && previous != ARTICLE) out.append(' ')
            out.append(piece)
            previous = piece
        }
        return if (out.isEmpty()) name else out.toString()
    }

    // ── one word ─────────────────────────────────────────────────────────

    private fun tokenToArabic(raw: String): String {
        // A token that already carries Arabic (the middle of "قناة_ONE") is left
        // alone; the Latin tokens around it are handled one by one.
        if (raw.any { it in ARABIC_RANGE }) return raw

        val key = normalize(raw)
        if (key.isEmpty()) return raw

        // Quality tags stay readable in Latin: "4K", "FHD", "SD1".
        if (key in KEEP || KEEP_SUFFIXES.matches(key)) return raw

        WORDS[key]?.let { return it }

        // "MBC2" is "MBC" plus a number, not an unknown word.
        val digits = key.takeLastWhile { it.isDigit() }
        if (digits.isNotEmpty()) {
            val stem = key.dropLast(digits.length)
            if (stem.isNotEmpty()) WORDS[stem]?.let { return "$it $digits" }
        }

        // Letters and digits together are a device label or "S1E2", not a word.
        if (key.any { it.isDigit() } && key.any { it.isLetter() }) return raw

        // "BBC" style acronyms are read out letter by letter. A vowel rules that
        // out, so short real words ("HIT", "BOX", "FREE") are transliterated
        // instead of being spelled letter by letter.
        val letters = raw.filter { it.isLetter() }
        if (letters.length in 2..4 &&
            letters.all { it.isUpperCase() } &&
            letters.none { it.lowercaseChar() in VOWELS }
        ) {
            return spellOut(letters)
        }

        return transliterate(raw)
    }

    // ── letter-by-letter ─────────────────────────────────────────────────

    /**
     * Latin word to Arabic letters. Deliberately forgiving: the goal is that an
     * Arabic reader recognises the name, not academic transliteration.
     */
    private fun transliterate(token: String): String {
        val lower = token.lowercase(Locale.ROOT)
        val last = lower.length - 1
        val out = StringBuilder(token.length + 8)
        var i = 0
        while (i <= last) {
            val ch = lower[i]
            if (ch !in 'a'..'z') {
                out.append(token[i])
                i++
                continue
            }
            if (i + 3 <= lower.length) {
                val triple = TRIPLES[lower.substring(i, i + 3)]
                if (triple != null) {
                    out.append(triple)
                    i += 3
                    continue
                }
            }
            if (i + 2 <= lower.length) {
                val pair = DIGRAPHS[lower.substring(i, i + 2)]
                if (pair != null) {
                    out.append(pair)
                    i += 2
                    continue
                }
            }
            when {
                // "one" -> "ون": a trailing "e" is silent.
                ch == 'e' && i == last -> i++
                // "cinema" -> "سينما": c before e/i/y is soft.
                ch == 'c' && i < last && lower[i + 1] in "eiy" -> {
                    out.append('س')
                    i++
                }
                // "series" -> "سيريز": a trailing s after a vowel is voiced.
                ch == 's' && i == last && i > 0 && lower[i - 1] in VOWELS -> {
                    out.append('ز')
                    i++
                }
                else -> {
                    out.append(SINGLES[ch] ?: token[i].toString())
                    i++
                }
            }
        }
        return out.toString()
    }

    /** "NBA" -> "إن بي إيه". */
    private fun spellOut(letters: String): String =
        letters.lowercase(Locale.ROOT)
            .map { LETTER_NAMES[it] ?: it.toString() }
            .joinToString(" ")

    private fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase(Locale.ROOT)) {
            if (ch in 'a'..'z' || ch in '0'..'9') sb.append(ch)
        }
        return sb.toString()
    }

    // ── tables ───────────────────────────────────────────────────────────

    private val ARABIC_RANGE = '\u0600'..'\u06FF'
    private const val ARTICLE = "ال"
    private const val MAX_PHRASE_WORDS = 3
    private const val VOWELS = "aeiouy"

    private val SEPARATORS = Regex("[\\s_\\-./|:]+")

    /** Read as-is: quality, codec and packaging tags. */
    private val KEEP = setOf(
        "4k", "8k", "2k", "uhd", "fhd", "fullhd", "hd", "sd", "3d",
        "h264", "h265", "hevc", "mpeg2", "mpeg4", "vip", "ppv", "iptv", "dvb", "ac3"
    )
    private val KEEP_SUFFIXES = Regex("^(?:4k|8k|uhd|fhd|fullhd|hd|sd)\\d*$")

    private val TRIPLES = mapOf(
        "sch" to "ش",
        "tch" to "تش"
    )

    private val DIGRAPHS = mapOf(
        "kh" to "خ", "sh" to "ش", "th" to "ث", "dh" to "ذ", "gh" to "غ",
        "ch" to "تش", "ph" to "ف", "ck" to "ك", "qu" to "كو",
        "aa" to "ا", "ee" to "ي", "ii" to "ي", "oo" to "و", "ou" to "و",
        "ow" to "و", "au" to "و", "ai" to "اي", "ay" to "اي", "ei" to "ي",
        "ey" to "ي", "ea" to "ي", "ie" to "ي", "oa" to "و", "ue" to "و",
        "ui" to "وي", "ss" to "س", "ll" to "ل", "tt" to "ت",
        "nn" to "ن", "mm" to "م", "bb" to "ب", "dd" to "د", "ff" to "ف",
        "gg" to "ج", "pp" to "ب", "rr" to "ر", "zz" to "ز"
    )

    private val SINGLES = mapOf(
        'a' to "ا", 'b' to "ب", 'c' to "ك", 'd' to "د", 'e' to "ي", 'f' to "ف",
        'g' to "ج", 'h' to "ه", 'i' to "ي", 'j' to "ج", 'k' to "ك", 'l' to "ل",
        'm' to "م", 'n' to "ن", 'o' to "و", 'p' to "ب", 'q' to "ق", 'r' to "ر",
        's' to "س", 't' to "ت", 'u' to "و", 'v' to "ف", 'w' to "و", 'x' to "كس",
        'y' to "ي", 'z' to "ز"
    )

    private val LETTER_NAMES = mapOf(
        'a' to "إيه", 'b' to "بي", 'c' to "سي", 'd' to "دي", 'e' to "إي",
        'f' to "إف", 'g' to "جي", 'h' to "إتش", 'i' to "آي", 'j' to "جيه",
        'k' to "كيه", 'l' to "إل", 'm' to "إم", 'n' to "إن", 'o' to "أو",
        'p' to "بي", 'q' to "كيو", 'r' to "آر", 's' to "إس", 't' to "تي",
        'u' to "يو", 'v' to "في", 'w' to "دبليو", 'x' to "إكس", 'y' to "واي",
        'z' to "زد"
    )

    /**
     * Known names, keyed by their letters and digits only, so "beIN" and
     * "BEIN" are the same key and "Al Jazeera" can be listed as one entry.
     */
    private val WORDS = mapOf(
        // ── providers and brands ──
        "bein" to "بي إن", "beinsports" to "بي إن سبورت",
        "mbc" to "إم بي سي", "mbcmasr" to "إم بي سي مصر", "mbcmasr2" to "إم بي سي مصر 2",
        "mbcdrama" to "إم بي سي دراما", "mbcbollywood" to "إم بي سي بوليوود",
        "osn" to "أو إس إن", "rotana" to "روتانا", "sky" to "سكاي",
        "skysports" to "سكاي سبورت", "cnn" to "سي إن إن", "bbc" to "بي بي سي",
        "aljazeera" to "الجزيرة", "jazeera" to "جزيرة",
        "alarabiya" to "العربية", "alarabia" to "العربية", "arabia" to "العربية",
        "abudhabi" to "أبوظبي", "dubai" to "دبي", "ksa" to "السعودية", "saudia" to "السعودية",
        "qatar" to "قطر", "kuwait" to "الكويت", "bahrain" to "البحرين", "oman" to "عمان",
        "jordan" to "الأردن", "lebanon" to "لبنان", "iraq" to "العراق", "egypt" to "مصر",
        "morocco" to "المغرب", "tunisia" to "تونس", "algeria" to "الجزائر",
        "syria" to "سوريا", "palestine" to "فلسطين", "yemen" to "اليمن",
        "sudan" to "السودان", "libya" to "ليبيا", "turkey" to "تركيا",
        "thmanyah" to "ثمانية", "shahid" to "شاهد", "fatafeat" to "فتافيت",
        "eurosport" to "يوروسبورت", "dazn" to "دازون", "beoutq" to "بي آوت كيو",
        "mtv" to "إم تي في", "tnt" to "تي إن تي", "dmax" to "ديماكس",
        "fox" to "فوكس", "foxmovies" to "فوكس موفيز", "sony" to "سوني",
        "hbo" to "إتش بي أو", "amc" to "إيه إم سي", "nbc" to "إن بي سي",
        "cbs" to "سي بي إس", "abc" to "إيه بي سي", "cbc" to "سي بي سي",
        "dmc" to "دي إم سي", "on" to "أون", "starz" to "ستارز", "starzplay" to "ستارز بلاي",
        "natgeo" to "نات جيو", "nationalgeographic" to "ناشيونال جيوغرافيك",
        "discovery" to "ديسكفري", "history" to "هيستوري",
        "animalplanet" to "أنيمال بلانيت", "cartoonnetwork" to "كارتون نيتوورك",
        "cartoon" to "كارتون", "spacetoon" to "سبيس تون", "quest" to "كويست",
        "alkass" to "الكأس", "ssc" to "إس إس سي", "sscsport" to "إس إس سي سبورت",
        "wwe" to "دبليو دبليو إي", "nba" to "إن بي إيه", "nfl" to "إن إف إل",
        "mlb" to "إم إل بي", "nhl" to "إن إتش إل", "ufc" to "يو إف سي",
        "rt" to "آر تي", "trt" to "تي آر تي", "dw" to "دي دبليو", "ard" to "إيه آر دي",
        "zdf" to "زد دي إف", "rtl" to "آر تي إل", "rai" to "راي", "tv5" to "تي في 5",
        "alarabiyaalhadath" to "العربية الحدث", "alhadath" to "الحدث",
        "almayadeen" to "الميادين", "mayadeen" to "الميادين", "almanar" to "المنار",
        "alnahar" to "النهار", "alhayat" to "الحياة", "alekhbariya" to "الإخبارية",
        "alqahira" to "القاهرة", "alkahera" to "القاهرة", "alalam" to "العالم",
        // ── words that show up in category-style names ──
        "al" to ARTICLE, "el" to ARTICLE, "tv" to "تي في", "live" to "لايف", "replay" to "ريبلاي",
        // Articles that would otherwise come out as a single odd letter.
        "the" to "",
        "sports" to "سبورتس", "sport" to "سبورت", "news" to "نيوز",
        "movies" to "موفيز", "movie" to "موفي", "cinema" to "سينما",
        "series" to "سيريز", "drama" to "دراما", "comedy" to "كوميدي",
        "action" to "أكشن", "kids" to "كيدز", "kid" to "كيد", "family" to "فاميلي",
        "music" to "ميوزيك", "documentary" to "وثائقي", "docs" to "وثائقية",
        "food" to "فود", "foodnetwork" to "فود نيتوورك", "travel" to "ترافل",
        "fashion" to "فاشن", "premium" to "بريميوم", "max" to "ماكس",
        "plus" to "بلس", "gold" to "جولد", "silver" to "سيلفر", "classic" to "كلاسيك",
        "world" to "وورلد", "international" to "إنترناشونال", "channel" to "قناة",
        "network" to "نيتوورك", "entertainment" to "إنتيرتينمنت", "arabic" to "عربي",
        "arab" to "عرب", "turkish" to "تركي", "english" to "إنجليزي", "french" to "فرنسي",
        "star" to "ستار", "select" to "سيليكت", "extra" to "إكسترا", "direct" to "ديركت",
        "radio" to "راديو", "shows" to "شوز", "show" to "شو", "movietime" to "موفي تايم",
        "one" to "ون", "two" to "تو", "three" to "ثري", "four" to "فور",
        "five" to "فايف", "six" to "سيكس", "seven" to "سفن", "test" to "تيست",
        "local" to "لوكال", "alkass2" to "الكأس 2"
    )
}
