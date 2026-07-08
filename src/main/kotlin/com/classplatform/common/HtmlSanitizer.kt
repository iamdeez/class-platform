package com.classplatform.common

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

object HtmlSanitizer {

	private val safelist: Safelist = Safelist.relaxed()
		.addTags("figure", "figcaption", "section")
		.addAttributes("img", "style", "class")
		.addAttributes("figure", "class")
		.addAttributes("section", "class")
		.addProtocols("img", "src", "http", "https")

	fun sanitize(html: String): String = Jsoup.clean(html, safelist)
}
