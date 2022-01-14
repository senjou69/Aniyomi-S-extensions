package eu.kanade.tachiyomi.animeextension.en.genoanime

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GenoAnime : ParsedAnimeHttpSource() {

    override val name = "Genoanime"
    override val baseUrl = "https://www.genoanime.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    // Popular Anime
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/browse?sort=top_rated&page=$page")

    override fun popularAnimeSelector(): String = "div.trending__product div.col-lg-10 div.row div.col-lg-3.col-6"
    override fun popularAnimeNextPageSelector(): String = "div.text-center a i.fa.fa-angle-double-right"

    override fun popularAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.setUrlWithoutDomain(element.select("div.product__item a").attr("href").removePrefix("."))
        anime.title = element.select("div.product__item__text h5 a:nth-of-type(2)").first().text()
        anime.thumbnail_url = "$baseUrl/${element.select("div.product__item__pic").attr("data-setbg").removePrefix("./")}"
        return anime
    }

    // Latest Anime
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/browse?sort=latest&page=$page", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    // Search Anime
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val formBody = FormBody.Builder()
            .add("anime", query)
            .build()
        val newHeaders = headersBuilder()
            .set("Content-Length", formBody.contentLength().toString())
            .set("Content-Type", formBody.contentType().toString())
            .build()
        return POST("$baseUrl/data/searchdata.php", newHeaders, formBody)
    }

    override fun searchAnimeSelector(): String = "div.col-lg-3"
    override fun searchAnimeNextPageSelector(): String = "div.text-center.product__pagination a.search-page i.fa.fa-angle-double-left"

    override fun searchAnimeFromElement(element: Element): SAnime {
        val anime = SAnime.create()
        anime.url = element.select("a").attr("href").removePrefix(".")
        anime.title = element.select("div.product__item__text h5 a:nth-of-type(2)").text()
        anime.thumbnail_url = "$baseUrl${element.select("div.product__item div.product__item__pic.set-bg").attr("data-setbg").removePrefix(".")}"
        return anime
    }

    // Episode
    override fun episodeListSelector() = "div.anime__details__episodes div.tab-pane a"
    override fun episodeFromElement(element: Element): SEpisode {
        val episode = SEpisode.create()
        episode.url = element.attr("href").removePrefix(".")
        episode.name = element.select("a").text()
        episode.episode_number = element.text().removePrefix("Ep ").toFloat()
        episode.date_upload = System.currentTimeMillis()
        return episode
    }

    // Video
    override fun videoUrlParse(document: Document): String = throw Exception("Not used.")
    override fun videoListSelector() = "section.details.spad div.container div.row:nth-of-type(1) div.col-lg-12:nth-of-type(1)"

    override fun videoFromElement(element: Element): Video {
        val vidsrc = element.select("div#video iframe#iframe-to-load").attr("src")
        if (vidsrc.contains("https://goload.one/streaming.php?id=")) {
            val url = videoidgrab(vidsrc)
            var a = doodUrlParse(url)
            while (a.contains("""<b class="err">Security error</b>""")) {
                a = doodUrlParse(url)
            }
            return Video(
                url,
                "Doodstream",
                a,
                null,
                Headers.headersOf("Referer", url)
            )
        } else {
            return Video(
                element.select("video source").attr("src"),
                "Unknown quality",
                element.select("video source").attr("src"),
                null,
                Headers.headersOf("Referer", baseUrl),
            )
        }
    }

    // Anime window
    override fun animeDetailsParse(document: Document): SAnime {
        val anime = SAnime.create()
        anime.url = document.location()
        anime.title = document.select("div.anime__details__title h3").text()
        anime.thumbnail_url = "$baseUrl/${document.select("div.anime__details__pic").attr("data-setbg").removePrefix(".")}"
        anime.genre = document.select("div.col-lg-6.col-md-6:nth-of-type(1) ul li:nth-of-type(3)")
            .joinToString(", ") { it.text() }.replace("Genre:", "")
        anime.description = document.select("div.anime__details__text > p").text()
        document.select("div.col-lg-6.col-md-6:nth-of-type(2) ul li:nth-of-type(2)").text()
            ?.also { statusText ->
                when {
                    statusText.contains("Ongoing", true) -> anime.status = SAnime.ONGOING
                    statusText.contains("Completed", true) -> anime.status = SAnime.COMPLETED
                    else -> anime.status = SAnime.UNKNOWN
                }
            }
        return anime
    }

    // Custom Fun
    private fun doodUrlParse(url: String): String {
        val response = client.newCall(GET(url.replace("/e/", "/d/"))).execute()
        val content = response.body!!.string()
        val md5 = content.substringAfter("/download/").substringBefore("\"")
        if (md5.contains("<!doctype html>")) { throw Exception("video not found on doodstream.") }
        var abc = doodreq(url, md5)
        while (doodreq(url, md5).contains("""<b class="err">Security error</b>""")) {
            abc = doodreq(url, md5)
        }
        return abc
    }

    private fun doodreq(url: String, md5: String): String {
        return client.newCall(
            GET(
                "https://dood.ws/download/$md5",
                Headers.headersOf("referer", url)
            )
        ).execute().body!!.string().substringAfter("window.open('").substringBefore("\'")
    }

    private fun videoidgrab(url: String): String {
        val uwrl = """https://gogoplay1.com/streaming.php?id=${url.substringAfter(".php?id=")}"""
        val content = client.newCall(GET(uwrl)).execute().body!!.string().substringAfter("dood").substringBefore("\"")
        return "https://dood$content"
    }
}
