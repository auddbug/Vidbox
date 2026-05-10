package recloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.StringUtils.encodeUri

class VidboxProvider : MainAPI() {

    override var mainUrl = "https://vidbox.cc"
    override var name = "Vidbox"
    override var lang = "en"

    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasMainPage = true

    // ---------------- DATA MODELS ----------------

    data class VideoSearchResponse(
        @JsonProperty("list") val list: List<VideoItem>
    )

    data class VideoItem(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("thumbnail_360_url") val thumbnail360Url: String
    )

    data class VideoDetailResponse(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("thumbnail_720_url") val thumbnail720Url: String
    )

    // ---------------- MAIN PAGE ----------------

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val response =
            app.get("$mainUrl/videos?limit=26&page=$page").text

        val items = tryParseJson<VideoSearchResponse>(response)?.list ?: emptyList()

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Popular",
                    items.map { it.toSearchResponse(this) },
                    true
                )
            )
        )
    }

    // ---------------- SEARCH ----------------

    override suspend fun search(query: String, page: Int): SearchResponseList? {

        val response =
            app.get("$mainUrl/videos?limit=26&page=$page&search=${query.encodeUri()}").text

        val items = tryParseJson<VideoSearchResponse>(response)?.list ?: return null

        return items.map {
            it.toSearchResponse(this)
        }.toNewSearchResponseList()
    }

    // ---------------- LOAD ----------------

    override suspend fun load(url: String): LoadResponse? {

        val videoId = Regex("video/([a-zA-Z0-9]+)")
            .find(url)
            ?.groupValues
            ?.get(1)
            ?: return null

        val response =
            app.get("$mainUrl/video/$videoId").text

        val detail =
            tryParseJson<VideoDetailResponse>(response) ?: return null

        return detail.toLoadResponse(this)
    }

    // ---------------- MAPPING ----------------

    private fun VideoItem.toSearchResponse(provider: VidboxProvider): SearchResponse {
        return provider.newMovieSearchResponse(
            title,
            "$mainUrl/video/$id",
            TvType.Movie
        ) {
            posterUrl = thumbnail360Url
        }
    }

    private fun VideoDetailResponse.toLoadResponse(provider: VidboxProvider): LoadResponse {
        return provider.newMovieLoadResponse(
            title,
            "$mainUrl/video/$id",
            TvType.Movie,
            id
        ) {
            plot = description
            posterUrl = thumbnail720Url
        }
    }

    // ---------------- LINKS ----------------

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        loadExtractor(
            "$mainUrl/embed/video/$data",
            subtitleCallback,
            callback
        )

        return true
    }
}
