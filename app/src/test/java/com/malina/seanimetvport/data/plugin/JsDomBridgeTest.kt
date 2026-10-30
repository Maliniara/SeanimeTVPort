package com.malina.seanimetvport.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies all CSS selectors used by the multistream-en plugin (Anikoto adapter).
 * Selector categories covered: tag, class, compound (tag+class, multi-class),
 * attribute presence, attribute value, child combinator, descendant, multi-selector.
 */
class JsDomBridgeTest {

    private lateinit var bridge: JsDomBridge

    // HTML matching the structure of the Anikoto search page
    private val searchPageHtml = """
        <html><body>
          <div class="item">
            <a class="name d-title" href="/anime/1">Attack on Titan</a>
            <span class="ep-status dub">DUB</span>
          </div>
          <div class="item">
            <a class="name d-title" href="/anime/2">Naruto</a>
          </div>
          <div class="other-class">
            <span class="ep-status">SUB only</span>
          </div>
        </body></html>
    """.trimIndent()

    // HTML matching the Anikoto AJAX episode list response
    private val episodeListHtml = """
        <html><body>
          <ul class="ep-range">
            <li><a href="/ep/1" data-ids="101,102"><span class="d-title">Episode 1</span></a></li>
            <li><a href="/ep/2" data-ids="103"><span class="d-title">Episode 2</span></a></li>
            <li><a href="/ep/3"><span class="d-title">Episode 3</span></a></li>
          </ul>
        </body></html>
    """.trimIndent()

    // HTML matching the Anikoto server list AJAX response
    private val serverListHtml = """
        <html><body>
          <div class="servers">
            <div class="type" data-type="sub">
              <li data-link-id="s1">Sub Server 1</li>
              <li data-link-id="s2">Sub Server 2</li>
            </div>
            <div class="type" data-type="dub">
              <li data-link-id="d1">Dub Server</li>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    @Before
    fun setUp() {
        bridge = JsDomBridge()
    }

    // --- Tag + class compound selector ---

    /**
     * select_divItem — div.item matches only div elements with class "item".
     */
    @Test
    fun select_divItem() {
        val doc = bridge.parse(searchPageHtml)
        val items = doc.select("div.item")
        assertEquals(2, items.length())
    }

    // --- Multi-class compound selector ---

    /**
     * select_compoundClasses_epStatusDub — .ep-status.dub matches elements with both classes.
     */
    @Test
    fun select_compoundClasses_epStatusDub() {
        val doc = bridge.parse(searchPageHtml)
        val dubs = doc.select(".ep-status.dub")
        // Only the DUB span has both classes; the SUB-only span has only .ep-status
        assertEquals(1, dubs.length())
    }

    /**
     * select_anchorMultipleClasses — a.name.d-title selects anchors with both classes.
     */
    @Test
    fun select_anchorMultipleClasses() {
        val doc = bridge.parse(searchPageHtml)
        val links = doc.select("a.name.d-title")
        assertEquals(2, links.length())
    }

    // --- Attribute presence selector ---

    /**
     * select_attributePresence_dataIds — a[data-ids] selects only anchors that have the attribute.
     */
    @Test
    fun select_attributePresence_dataIds() {
        val doc = bridge.parse(episodeListHtml)
        val withIds = doc.select("a[data-ids]")
        // Only 2 of the 3 episode anchors have data-ids
        assertEquals(2, withIds.length())
    }

    // --- Child combinator ---

    /**
     * select_childCombinator — ul.ep-range li > a matches direct anchor children of li elements.
     */
    @Test
    fun select_childCombinator() {
        val doc = bridge.parse(episodeListHtml)
        val anchors = doc.select("ul.ep-range li > a")
        assertEquals(3, anchors.length())
    }

    // --- Attribute value + descendant combinator ---

    /**
     * select_attributeValue_descendant — complex selector with attribute value filters correctly.
     */
    @Test
    fun select_attributeValue_descendant() {
        val doc = bridge.parse(serverListHtml)
        val subServers = doc.select(".servers .type[data-type=\"sub\"] li[data-link-id]")
        assertEquals(2, subServers.length())
    }

    /**
     * select_attributeValue_dubType — verifying the dub variant works independently.
     */
    @Test
    fun select_attributeValue_dubType() {
        val doc = bridge.parse(serverListHtml)
        val dubServers = doc.select(".servers .type[data-type=\"dub\"] li[data-link-id]")
        assertEquals(1, dubServers.length())
    }

    // --- Chaining: find(), first(), attr(), text(), length() ---

    /**
     * find_childSelector — find() searches within each already-matched element.
     */
    @Test
    fun find_childSelector() {
        val doc = bridge.parse(searchPageHtml)
        val items = doc.select("div.item")
        val titles = items.find("a.name.d-title")
        assertEquals(2, titles.length())
    }

    /**
     * first_returnsSingleElement — first() wraps only the first match.
     */
    @Test
    fun first_returnsSingleElement() {
        val doc = bridge.parse(episodeListHtml)
        val first = doc.select("ul.ep-range li > a").first()
        assertEquals(1, first.length())
    }

    /**
     * first_returnsCorrectHref — first() preserves attribute access on the returned element.
     */
    @Test
    fun first_returnsCorrectHref() {
        val doc = bridge.parse(episodeListHtml)
        val href = doc.select("ul.ep-range li > a").first().attr("href")
        assertEquals("/ep/1", href)
    }

    /**
     * attr_returnsAttributeValue — attr() returns the named attribute of the first match.
     */
    @Test
    fun attr_returnsAttributeValue() {
        val doc = bridge.parse(episodeListHtml)
        val dataIds = doc.select("a[data-ids]").first().attr("data-ids")
        assertEquals("101,102", dataIds)
    }

    /**
     * text_returnsTextContent — text() returns the visible text of the first matched element.
     */
    @Test
    fun text_returnsTextContent() {
        val doc = bridge.parse(episodeListHtml)
        val text = doc.select("span.d-title").first().text()
        assertEquals("Episode 1", text)
    }

    /**
     * each_iteratesAllMatchedElements — each() invokes callback for every match with index.
     */
    @Test
    fun each_iteratesAllMatchedElements() {
        val doc = bridge.parse(searchPageHtml)
        val collected = mutableListOf<String>()
        doc.select("div.item").each { _, el ->
            collected.add(el.find("a.name.d-title").text())
        }
        assertEquals(listOf("Attack on Titan", "Naruto"), collected)
    }

    /**
     * length_isCallableAsMethod — length() is invocable as a method (not just a property access).
     * This mirrors the plugin's el.length() usage pattern.
     */
    @Test
    fun length_isCallableAsMethod() {
        val doc = bridge.parse(searchPageHtml)
        val items = doc.select("div.item")
        val count: Int = items.length()
        assertEquals(2, count)
    }

    /**
     * select_emptyResult_noThrow — querying with no matches returns JsElements with length 0.
     */
    @Test
    fun select_emptyResult_noThrow() {
        val doc = bridge.parse(searchPageHtml)
        val result = doc.select(".nonexistent-class")
        assertEquals(0, result.length())
    }

    /**
     * each_callbackException_doesNotHaltIteration — an exception thrown by one callback
     * iteration must not stop processing the remaining elements (bridge crash-safety).
     */
    @Test
    fun each_callbackException_doesNotHaltIteration() {
        val doc = bridge.parse(searchPageHtml)
        var callCount = 0
        doc.select("div.item").each { i, _ ->
            callCount++
            if (i == 0) throw RuntimeException("simulated callback error")
        }
        // Both items must be visited even though the first callback threw
        assertEquals(2, callCount)
    }
}
