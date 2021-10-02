package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import org.junit.Assert
import org.junit.Test

class ProviderTests {
    private fun getAllProviders(): List<MainAPI> {
        val allApis = APIHolder.apis
        allApis.addAll(APIHolder.restrictedApis)
        return allApis
    }

    @Test
    fun providers_exist() {
        Assert.assertTrue(getAllProviders().isNotEmpty())
    }

    @Test
    fun provider_correct_data() {
        val isoNames = SubtitleHelper.languages.map { it.ISO_639_1 }
        Assert.assertFalse("ISO does not contain any languages", isoNames.isNullOrEmpty())
        for (api in getAllProviders()) {
            Assert.assertTrue("Api does not contain a mainurl", api.mainUrl != "NONE")
            Assert.assertTrue("Api does not contain a name", api.name != "NONE")
            Assert.assertTrue("Api ${api.name} does not contain a valid language code", isoNames.contains(api.lang))
            Assert.assertTrue("Api ${api.name} does not contain any supported types", api.supportedTypes.isNotEmpty())
        }
    }

    @Test
    fun provider_correct_homepage() {
        for (api in getAllProviders()) {
            if (api.hasMainPage) {
                try {
                    val homepage = api.getMainPage()
                    when {
                        homepage == null -> {
                            Assert.fail("Homepage provider ${api.name} did not correctly load homepage!")
                        }
                        homepage.items.isEmpty() -> {
                            Assert.fail("Homepage provider ${api.name} does not contain any items!")
                        }
                        homepage.items.any { it.list.isEmpty() } -> {
                            Assert.fail("Homepage provider ${api.name} does not have any items on result!")
                        }
                    }
                } catch (e: Exception) {
                    if (e.cause is NotImplementedError) {
                        Assert.fail("Provider marked as hasMainPage, while in reality is has not been implemented")
                    }
                    logError(e)
                }
            }
        }
    }

    private fun loadLinks(api: MainAPI, url: String?): Boolean {
        Assert.assertNotNull("Api ${api.name} has invalid url on episode", url)
        if (url == null) return true
        var linksLoaded = 0
        try {
            val success = api.loadLinks(url, false, {}) { link ->
                Assert.assertTrue(
                    "Api ${api.name} returns link with invalid Quality",
                    Qualities.values().map { it.value }.contains(link.quality)
                )
                Assert.assertTrue("Api ${api.name} returns link with invalid url", link.url.length > 4)
                linksLoaded++
            }
            if (success) {
                return linksLoaded > 0
            }
            Assert.assertTrue("Api ${api.name} has returns false on .loadLinks", success)
        } catch (e: Exception) {
            if (e.cause is NotImplementedError) {
                Assert.fail("Provider has not implemented .loadLinks")
            }
            logError(e)
        }
        return true
    }

    @Test
    fun provider_correct() {
        val searchQueries = listOf("over", "iron", "guy")
        val providers = getAllProviders()
        for ((index, api) in providers.withIndex()) {
            try {
                println("Trying $api (${index + 1}/${providers.size})")
                var correctResponses = 0
                var searchResult: List<SearchResponse>? = null
                for (query in searchQueries) {
                    val response = try {
                        api.search(query)
                    } catch (e: Exception) {
                        if (e.cause is NotImplementedError) {
                            Assert.fail("Provider has not implemented .search")
                        }
                        logError(e)
                        null
                    }
                    if (!response.isNullOrEmpty()) {
                        correctResponses++
                        if (searchResult == null) {
                            searchResult = response
                        }
                    }
                }

                if (correctResponses == 0 || searchResult == null) {
                    println("Api ${api.name} did not return any valid search responses")
                    continue
                }

                try {
                    var validResults = false
                    for (result in searchResult) {
                        Assert.assertEquals("Invalid apiName on response on ${api.name}", result.apiName, api.name)
                        val load = api.load(result.url) ?: continue
                        Assert.assertEquals("Invalid apiName on load on ${api.name}", load.apiName, result.apiName)
                        Assert.assertTrue(
                            "Api ${api.name} on load does not contain any of the supportedTypes",
                            api.supportedTypes.contains(load.type)
                        )
                        when (load) {
                            is AnimeLoadResponse -> {
                                val gotNoEpisodes =
                                    load.dubEpisodes.isNullOrEmpty() && load.subEpisodes.isNullOrEmpty()
                                if (gotNoEpisodes) {
                                    println("Api ${api.name} got no episodes on ${load.url}")
                                    continue
                                }

                                val url = (load.dubEpisodes ?: load.subEpisodes)?.first()?.url
                                validResults = loadLinks(api, url)
                                if (!validResults) continue
                            }
                            is MovieLoadResponse -> {
                                val gotNoEpisodes = load.dataUrl.isBlank()
                                if (gotNoEpisodes) {
                                    println("Api ${api.name} got no movie on ${load.url}")
                                    continue
                                }

                                validResults = loadLinks(api, load.dataUrl)
                                if (!validResults) continue
                            }
                            is TvSeriesLoadResponse -> {
                                val gotNoEpisodes = load.episodes.isEmpty()
                                if (gotNoEpisodes) {
                                    println("Api ${api.name} got no episodes on ${load.url}")
                                    continue
                                }

                                validResults = loadLinks(api, load.episodes.first().data)
                                if (!validResults) continue
                            }
                        }
                        break
                    }

                    Assert.assertTrue("Api ${api.name} did not load on any}", validResults)
                } catch (e: Exception) {
                    if (e.cause is NotImplementedError) {
                        Assert.fail("Provider has not implemented .load")
                    }
                    logError(e)
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
    }
}