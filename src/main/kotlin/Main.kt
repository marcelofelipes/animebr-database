package org.example

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.optionals.getOrNull
import kotlin.time.measureTime

val httpClient = HttpClient.newHttpClient()

@OptIn(ExperimentalEncodingApi::class)
fun giganimaRequest(methodName: String): String? {
    var body = """
        {
        "salt": "448",
        "sign": "ebd5aba4df34837158d360b1e81c4e72",
        "method_name": "$methodName"
    }
    """.trimIndent()
    body = "data=" + Base64.encode(body.toByteArray())

    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://giganima.top/api.php"))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

    return response.body()
}

fun findManamiAnime(animeName: String, manamiDatabase: List<JsonNode>): JsonNode? {
    return manamiDatabase.stream().filter {
        it["title"].asText().equals(animeName, true) || it.withArrayProperty("synonyms")
            .find { synonym -> synonym.asText().equals(animeName, true) } != null
    }.findFirst().getOrNull()
}

fun findManamiAnimeSimilar(animeName: String, manamiDatabase: List<JsonNode>): JsonNode? {
    return manamiDatabase.stream().filter {
        FuzzySearch.ratio(it["title"].asText(), animeName) > 80 || it.withArrayProperty("synonyms")
            .find { synonym -> FuzzySearch.ratio(synonym.asText(), animeName) > 80 } != null
    }.findFirst().getOrNull()
}

fun main() {
    val mapper = ObjectMapper()
    File("unmatch.txt").also {
        if (it.exists()) it.delete()
    }

//    val giganimaDatabase = giganimaRequest("get_category")?.let {
//        File("giganima-database.json").writeText(it)
//        mapper.readTree(it)
//    }
    val giganimaDatabase: JsonNode = mapper.readTree(File("giganima-database.json"))
    val manamiDatabase = mapper.readTree(File("anime-offline-database.json"))
        .withArrayProperty("data")
        .toList()

    println(manamiDatabase.size)

    val animesGiga = giganimaDatabase.withArrayProperty("HD_VIDEO")!!
    var match = 0
    var unMatchOriginal = 0
    var unMatch = 0
    val duration = measureTime {
        animesGiga.toList().parallelStream().forEach {
            var gigaTitle = it["category_name"].asText()
            gigaTitle = gigaTitle.replace("Dublado", "", true)
            if (gigaTitle.contains("(")) {
                gigaTitle = gigaTitle.substring(0, gigaTitle.indexOf("("))
            }
            var matchItem = findManamiAnime(gigaTitle, manamiDatabase)

            if (matchItem != null) {
                match++
            } else {
                unMatchOriginal++

                matchItem = findManamiAnimeSimilar(gigaTitle, manamiDatabase)

                if (matchItem != null) {
                    match++
                } else {
                    File("unmatch.txt").appendText("$gigaTitle\n")
                    unMatch++
                }
            }
        }
    }

    println("Match: $match")
    println("Unmatch (Global): $unMatchOriginal")
    println("Unmatch (Similar): $unMatch")
    println("Duration: $duration")
}