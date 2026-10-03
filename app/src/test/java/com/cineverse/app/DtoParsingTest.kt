package com.cineverse.app

import com.cineverse.app.core.net.Http
import com.cineverse.app.data.tmdb.MediaDto
import com.cineverse.app.data.tmdb.PageDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shapes TMDB actually sends, parsed by the app's own Json.
 *
 * A list endpoint that fails to parse does not crash anything - catalogue reads
 * return an empty list on failure, by design - so a broken DTO shows up only as
 * a Home page full of skeletons. These pin the payloads that matter.
 */
class DtoParsingTest {

    @Test
    fun `a trending page with a person and their known_for parses`() {
        val body = """
            {"page":1,"total_pages":3,"total_results":60,"results":[
              {"id":525,"name":"Christopher Nolan","media_type":"person","popularity":12.5,
               "known_for_department":"Directing","profile_path":"/p.jpg",
               "known_for":[
                 {"id":27205,"title":"Inception","media_type":"movie","poster_path":"/i.jpg","vote_count":38000},
                 {"id":157336,"title":"Interstellar","media_type":"movie","poster_path":"/s.jpg"}
               ]},
              {"id":1396,"name":"Breaking Bad","media_type":"tv","first_air_date":"2008-01-20"}
            ]}
        """.trimIndent()
        val page = Http.json.decodeFromString(PageDto.serializer(MediaDto.serializer()), body)
        assertEquals(2, page.results.size)
        assertEquals(2, page.results[0].knownFor.size)
        assertEquals("Inception", page.results[0].knownFor[0].title)
        assertEquals(3, page.totalPages)
    }

    @Test
    fun `a plain movie list without known_for still parses`() {
        val body = """{"page":1,"results":[{"id":1,"title":"A","vote_average":7.1}],"total_pages":1}"""
        val page = Http.json.decodeFromString(PageDto.serializer(MediaDto.serializer()), body)
        assertEquals(1, page.results.size)
        assertEquals(0, page.results[0].knownFor.size)
    }
}
