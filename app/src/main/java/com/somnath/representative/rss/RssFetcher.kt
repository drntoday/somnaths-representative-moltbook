package com.somnath.representative.rss

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class RssFetcher(
    private val client: OkHttpClient = OkHttpClient()
) {
    fun fetch(feedUrl: String, limit: Int = 10): Result<List<RssItem>> {
        return runCatching {
            val request = Request.Builder()
                .url(feedUrl)
                .header("User-Agent", "SomnathRepresentative/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("RSS request failed (${response.code})")
                }
                val body = response.body?.string().orEmpty()
                parseFeed(body, limit)
            }
        }
    }

    private fun parseFeed(xml: String, limit: Int): List<RssItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())

        var eventType = parser.eventType
        var inEntry = false
        var title = ""
        var link = ""
        var publishedAt: String? = null
        var currentTag: String? = null

        val items = mutableListOf<RssItem>()

        while (eventType != XmlPullParser.END_DOCUMENT && items.size < limit) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name?.lowercase()
                    currentTag = tagName

                    if (tagName == "item" || tagName == "entry") {
                        inEntry = true
                        title = ""
                        link = ""
                        publishedAt = null
                    }

                    if (inEntry && tagName == "link") {
                        val atomHref = parser.getAttributeValue(null, "href")
                        if (!atomHref.isNullOrBlank()) {
                            link = atomHref.trim()
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    if (!inEntry) {
                        eventType = parser.next()
                        continue
                    }

                    val text = parser.text?.trim().orEmpty()
                    if (text.isBlank()) {
                        eventType = parser.next()
                        continue
                    }

                    when (currentTag) {
                        "title" -> if (title.isBlank()) title = text
                        "link" -> if (link.isBlank()) link = text
                        "pubdate", "published", "updated", "dc:date" -> {
                            if (publishedAt.isNullOrBlank()) publishedAt = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tagName = parser.name?.lowercase()
                    if (tagName == "item" || tagName == "entry") {
                        inEntry = false
                        val cleanTitle = title.trim()
                        if (cleanTitle.isNotBlank()) {
                            items.add(
                                RssItem(
                                    title = cleanTitle,
                                    link = link.trim(),
                                    publishedAt = publishedAt?.trim()?.ifBlank { null }
                                )
                            )
                        }
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        return items
    }
}
