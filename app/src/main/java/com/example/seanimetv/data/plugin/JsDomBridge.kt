package com.example.seanimetv.data.plugin

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

/**
 * jQuery-like DOM bridge for the QuickJS plugin engine, backed by Jsoup.
 *
 * Group 6 (QuickJSEngine) wraps [JsDomBridge.parse] and exposes the result as the
 * `LoadDoc(html)` global callable from plugin JavaScript. The returned [JsDoc] is
 * then exposed as a root `$(selector)` function in JS.
 *
 * All CSS selectors required by the multistream-en plugin (Anikoto adapter) are
 * supported natively by Jsoup with no custom parsing:
 *   - Tag selectors: `div`, `a`, `ul`, `li`, `span`
 *   - Class selectors: `.item`, `.name`, `.d-title`, `.ep-status`, `.dub`
 *   - Compound (tag+class, multi-class): `div.item`, `a.name.d-title`, `.ep-status.dub`
 *   - Attribute presence: `a[data-ids]`
 *   - Attribute value: `[data-type="sub"]`, `[data-link-id]`
 *   - Child combinator: `ul.ep-range li > a`
 *   - Descendant: `.servers .type[data-type="sub"] li[data-link-id]`
 *   - Multi-selector (comma-separated): `ul.ep-range li > a, .ep-range a, a[data-ids]`
 *
 * Bridge failures are caught at every layer — they must never propagate as exceptions
 * into the QuickJS runtime.
 */
class JsDomBridge {

    /**
     * Parses [html] into a Jsoup document and returns a queryable [JsDoc] wrapper.
     * Returns an empty document on parse failure.
     */
    fun parse(html: String): JsDoc {
        return try {
            JsDoc(Jsoup.parse(html))
        } catch (e: Exception) {
            JsDoc(Jsoup.parse(""))
        }
    }
}

/**
 * Wraps a parsed Jsoup [Document] and exposes a [select] method corresponding to
 * the root `$(selector)` function the plugin receives from `LoadDoc`.
 */
class JsDoc(private val root: Document) {

    /**
     * Runs [selector] against the document root and returns the matched elements.
     * Returns an empty [JsElements] on selector error.
     */
    fun select(selector: String): JsElements {
        return try {
            JsElements(root.select(selector))
        } catch (e: Exception) {
            JsElements(Elements())
        }
    }
}

/**
 * Wraps a Jsoup [Elements] collection and exposes a jQuery-like chainable API.
 *
 * Method signatures deliberately mirror the TypeScript plugin API:
 * ```typescript
 * $("div.item").each((i, el) => {
 *     el.find("a.name").first().attr("href")
 *     el.text()
 *     el.length()   // method, not property
 * })
 * ```
 *
 * All methods return safe fallback values (empty [JsElements], empty string, 0)
 * instead of throwing — bridge failures must not crash QuickJS.
 */
class JsElements(private val elements: Elements) {

    /**
     * Searches within every element in this set for descendants matching [selector].
     * Equivalent to jQuery's `.find()`.
     */
    fun find(selector: String): JsElements {
        return try {
            val result = Elements()
            for (el in elements) {
                result.addAll(el.select(selector))
            }
            JsElements(result)
        } catch (e: Exception) {
            JsElements(Elements())
        }
    }

    /**
     * Iterates every element, invoking [callback] with the zero-based index and a
     * single-element [JsElements] wrapper. Exceptions thrown by the callback are
     * caught per iteration — a failing callback must not stop the remaining iterations.
     *
     * Returns `this` for chaining.
     */
    fun each(callback: (Int, JsElements) -> Unit): JsElements {
        elements.forEachIndexed { i, el ->
            try {
                val single = Elements()
                single.add(el)
                callback(i, JsElements(single))
            } catch (e: Exception) {
                // Bridge crash-safety: skip this iteration, continue with the rest.
            }
        }
        return this
    }

    /**
     * Returns a [JsElements] wrapping only the first matched element, or an empty
     * wrapper if this set is empty. Equivalent to jQuery's `.first()`.
     */
    fun first(): JsElements {
        if (elements.isEmpty()) return JsElements(Elements())
        // Use Kotlin stdlib firstOrNull() to avoid Jsoup's own @Nullable first() method.
        val firstEl = elements.firstOrNull() ?: return JsElements(Elements())
        val single = Elements()
        single.add(firstEl)
        return JsElements(single)
    }

    /**
     * Returns the value of attribute [name] from the first element, or an empty
     * string if there are no elements or the attribute is absent.
     */
    fun attr(name: String): String = elements.firstOrNull()?.attr(name) ?: ""

    /**
     * Returns the combined text content of the first matched element, or an empty
     * string if the set is empty.
     */
    fun text(): String = elements.firstOrNull()?.text() ?: ""

    /**
     * Returns the number of matched elements.
     * Exposed as a *method* (not a property) to match the plugin's `el.length()` call pattern.
     */
    fun length(): Int = elements.size

    /**
     * Filters this set to elements matching [selector]. Equivalent to
     * jQuery/cheerio's `.filter(selector)` (Anikoto uses this).
     */
    fun filter(selector: String): JsElements {
        return try {
            val result = Elements()
            for (el in elements) {
                if (el.`is`(selector)) result.add(el)
            }
            JsElements(result)
        } catch (e: Exception) {
            JsElements(Elements())
        }
    }

    /** Returns the last matched element (cheerio `.last()`). */
    fun last(): JsElements {
        val lastEl = elements.lastOrNull() ?: return JsElements(Elements())
        val single = Elements()
        single.add(lastEl)
        return JsElements(single)
    }

    /** Returns the element at [index] (cheerio `.eq(i)`). */
    fun eq(index: Int): JsElements {
        val el = elements.getOrNull(index) ?: return JsElements(Elements())
        val single = Elements()
        single.add(el)
        return JsElements(single)
    }

    /** Direct children of every element (cheerio `.children()`). */
    fun children(): JsElements {
        val result = Elements()
        for (el in elements) result.addAll(el.children())
        return JsElements(result)
    }

    /** Parents of every element (cheerio `.parent()`). */
    fun parent(): JsElements {
        val result = Elements()
        for (el in elements) el.parent()?.let { result.add(it) }
        return JsElements(result)
    }

    /** Inner HTML of the first element (cheerio `.html()`). */
    fun html(): String = elements.firstOrNull()?.html() ?: ""
}
