package eu.kanade.tachiyomi.animeextension.ar.xsmovie

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.Exception

class XsMovie : ParsedAnimeHttpSource() {

    override val name = "XS Movie"

    override val baseUrl = "https://ww.xsanime.com"

    override val lang = "ar"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    // Popular Anime
    override fun popularAnimeSelector(): String = "ul.boxes--holder div.itemtype_anime a"

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/movies_list/page/$page")

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.attr("href"))
        anime.title = element.attr("title")
        anime.thumbnail_url = element.select("div.itemtype_anime_poster img").first().attr("abs:src")
        return anime
    }

    override fun popularAnimeNextPageSelector(): String = "ul.page-numbers li a.next"

    // Episodes
    override fun episodeListSelector() = "h1.post--inner-title > a"

    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.setUrlWithoutDomain(element.attr("href"))
        episode.name = "movie"

        return episode
    }

    // Video Links

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val iframe = document.select("div.player--iframe iframe").attr("src").removePrefix("https://ww.xsanime.com/embedd?url=")
        val referer = response.request.url.encodedPath
        val newHeaderList = mutableMapOf(Pair("referer", baseUrl + referer))
        headers.forEach { newHeaderList[it.first] = it.second }
        val iframeResponse = client.newCall(GET(iframe, newHeaderList.toHeaders()))
            .execute().asJsoup()
        return iframeResponse.select(videoListSelector()).map { videoFromElement(it) }
    }

    override fun videoListSelector() = "source"

    override fun videoFromElement(element: Element): Video {
        return Video(element.attr("src"), "Default", element.attr("src"), null)
    }

    override fun videoUrlParse(document: Document) = throw Exception("not used")

    // Search

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.attr("href"))
        anime.title = element.attr("title")
        anime.thumbnail_url = element.select("div.itemtype_anime_poster img").first().attr("abs:src")
        return anime
    }

    override fun searchAnimeNextPageSelector(): String = "ul.page-numbers li a.next"

    override fun searchAnimeSelector(): String = "ul.boxes--holder div.itemtype_anime a"

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/?s=$query&type=movie&page=$page", headers)
    }

    // Anime Details

    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.thumbnail_url = document.select("div.inner--image img").first().attr("src")
        anime.title = document.select("h1.post--inner-title").text()
        anime.genre = document.select("ul.terms--and--metas > li:contains(تصنيفات الأنمي) > a").joinToString(", ") { it.text() }
        anime.description = document.select("div.post--content--inner").text()
        document.select("ul.terms--and--metas li:contains(عدد الحلقات) a").text()?.also { statusText ->
            when {
                statusText.contains("غير معروف", true) -> anime.status = SAnime.ONGOING
                else -> anime.status = SAnime.COMPLETED
            }
        }
        return anime
    }

    // Latest

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SAnime = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")
}
