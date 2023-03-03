package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbUrl
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/* WIP
search https://api.vanillo.tv/v1/search?query=example

wont ever use? https://api.vanillo.tv/v1/playlists/FTWFYu3JS1qpyXEGtdtQ0A/videos
as series/tv-show ? https://api.vanillo.tv/v1/profiles/ellie/videos?offset=0&limit=10
no need bc recommended and search will provide -- direct video info https://api.vanillo.tv/v1/videos/S2D0tfcU-zp
mainpage https://api.vanillo.tv/v1/videos/recommended?limit=30

TO WATCH
POST "{\"videoId\":\"$keyVideoID\"}" https://api.vanillo.tv/v1/_/watch
returns {"status":"success","data":{"watchToken":"4nUSIpyc"}} (<-- they expire real fast)
to GET playback links https://api.vanillo.tv/v1/_/watch/manifests?watchToken=$watchToken"
{"status":"success","data":{"media":{"dash":"https://us.cdn.vanillo.tv//manifest.mpd","hls":"https://us.cdn.vanillo.tv//master.m3u8"}}}

more refs: see sampledata.json.txt
basic watchable video: basic.sh
*/

class VanilloProvider : MainAPI() {
    override var name = "Vanillo"
    override var mainUrl = "https://api.vanillo.tv"
    override val instantLinkLoading = false // bc If link is stored in the "data" string, links can be instantly loaded
    override val hasQuickSearch = true
    override val hasChromecastSupport = false // bc Set false if links require referer
    override val hasDownloadSupport = false // bc HLS and DASH?
    override val hasMainPage = false // for now, has recommended though

data class VanSearchResponse(val results: List<VanilloSearchItem>)
data class VanilloSearchResult(
@JsonProperty("type") val type: String, // profile or video
@JsonProperty("id") val id: String, // user or videoID
@JsonProperty("title") val title: String?, //video only
@JsonProperty("thumbnail") val thumbnail: String?, //video only
@JsonProperty("description") val description: String?, //video only
@JsonProperty("duration") val duration: Double?,
@JsonProperty("category") val category: String?,
@JsonProperty("privacy") val privacy: String?,
//@JsonProperty("defaultThumbnails") val defaultThumbnails: List<String>?,
@JsonProperty("status") val status: String?,
@JsonProperty("publishedAt") val publishedAt: String?
    )

data class HomePageList(
@JsonProperty("type") val type: String, // profile or video
@JsonProperty("id") val id: String, // user or videoID
@JsonProperty("title") val title: String?, //video only
@JsonProperty("thumbnail") val thumbnail: String?, //video only
@JsonProperty("description") val description: String?, //video only
@JsonProperty("duration") val duration: Double?,
@JsonProperty("category") val category: String?,
@JsonProperty("privacy") val privacy: String?,
//@JsonProperty("defaultThumbnails") val defaultThumbnails: List<String>?,
@JsonProperty("status") val status: String?,
@JsonProperty("publishedAt") val publishedAt: String?
    )
	
data class WatchTokenData(
@JsonProperty("watchToken") val watchToken: String
)

data class WatchFinalPlaybackMedia(
@JsonProperty("dash") val dash: String,
@JsonProperty("hls") val hls: String
)

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return vanisearch(query)
    }

    override suspend fun vanisearch(query: String): List<SearchResponse> {
        val url = "$mainUrl/v1/search?query=$query"
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        val response = app.get(url).text
        val mapped = response.let { mapper.readValue<List<VanilloSearchResult>>(it) }
        if (mapped.isEmpty()) return returnValue

        for (i in mapped) {
            val currentUrl = "$mainUrl/v1/_/${i.id}"
            val currentPoster = "${i.thumbnail}"
            if (i.type == "profile") { // treat is as TV-SERIES, maybe playlists too? (dirty hack / workaround for video social site imo), seasons could be used for paging
                returnValue.add(
                    TvSeriesSearchResponse(
                        i.title,
                        currentUrl,
                        this.name,
                        TvType.TvSeries,
                        currentPoster,
                        i.year,
                        null
                    )
                )
            } else if (i.type == "video") { // MOVIE cuz like it's single af
                returnValue.add(
                    MovieSearchResponse(
                        i.title,
                        currentUrl,
                        this.name,
                        TvType.Movie,
                        currentUrl,
                        i.year
                    )
                )
            }
        }
        return returnValue
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // println("loadling link $data")

    // 3 get watch token
    val watchTokenUrl = URL("https://api.vanillo.tv/v1/_/watch")
    val watchTokenUrlConnection = watchTokenUrl.openConnection() as HttpURLConnection
    watchTokenUrlConnection.requestMethod = "POST"
    watchTokenUrlConnection.setRequestProperty("Content-Type", "application/json; utf-8")
    watchTokenUrlConnection.doOutput = true
    val jsonInputString = "{\"videoId\":\"$data\"}"
    val jsonBytes = jsonInputString.toByteArray(StandardCharsets.UTF_8)
    watchTokenUrlConnection.outputStream.write(jsonBytes)
    val watchToken_response = watchTokenUrlConnection.inputStream.bufferedReader().use { it.readText() }
    val watchToken_json = JSONObject(watchToken_response)
    val watchToken = watchToken_json.getJSONObject("data").getString("watchToken")
    println("Watch token: $watchToken")

    //now can actually watch videos!
    val manifests_url = "https://api.vanillo.tv/v1/_/watch/manifests?watchToken=${URLEncoder.encode(watchToken, "UTF-8")}"
    val manifests_response = URL(manifests_url).readText()
    val manifests_json = JSONObject(manifests_response)
    val dash_url = manifests_json.getJSONObject("data").getJSONObject("media").getString("dash")
    val hls_url = manifests_json.getJSONObject("data").getJSONObject("media").getString("hls")
	
            val mediaRootDocument = app.get(manifests_url).document
			
	        callback.invoke (
            ExtractorLink(
                name, //source
                name, //name (video title?)
                hls_url, //m3u8
                "https://vanillo.tv", // referrer
                Qualities.Unknown.value,
                headers = mapOf("accept" to "*/*"),
                true //isM3u8
            )
        )
        return true
		
	}

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val res = tryParseJson<Manifest>(app.get("https://api.vanillo.tv/v1/videos/recommended?limit=30").text) ?: return null
        val lists = mutableListOf<HomePageList>()
        res.data.videos.forEach {
			    return provider.newMovieSearchResponse(name, thumbnail)
        }
        return HomePageResponse(
            lists,
            false
        )
    }
    
	/*
    private data class CatalogResponse(val metas: List<CatalogEntry>)
    private data class CatalogEntry(
        val name: String,
        val id: String,
        val poster: String?,
        val description: String?,
        val type: String?,
        val videos: List<Video>?
*/

}
