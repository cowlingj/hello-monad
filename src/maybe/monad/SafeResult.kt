package maybe.monad

// poor mans optional
data class SafeResult<S>(val data: S?, val err: Throwable?) {
    // this is like rx's map
    fun <V>sequence(transform: (s: S) -> V): SafeResult<V> {
        return if (this.data == null) {
            SafeResult(null, this.err)
        } else {
            try {
                SafeResult(transform(this.data), null)
            } catch (transformThrown: Throwable) {
                SafeResult(null as V?, transformThrown)
            }
        }
    }

    // this is maybe monad func
    fun <V>flatSequence(transform: (s: S) -> SafeResult<V>): SafeResult<V> {
        return if (this.data == null) {
            SafeResult(null, this.err)
        } else {
            transform(this.data)
        }
    }

    // this is subscribe
    fun endSequence(success: (s: S) -> Unit, failure: (e: Throwable?) -> Unit) {
        if (this.data == null) {
            failure(this.err)
        } else {
            success(this.data)
        }
    }
}

interface Composable {
    infix fun compose(other: Composable): Composable
}

data class HttpResponse(val data: String): Composable {
    override infix fun compose(other: Composable) = HttpResponse("$this composed with $other")
    override fun toString() = data
}

const val SUPER_SECRET_URL = "https://www.apple.com"
@Throws(Exception::class)
fun httpGet(url: String) : HttpResponse {
    if (url == SUPER_SECRET_URL) {
        throw Exception("Exception for $url")
    } else {
        return HttpResponse(url)
    }
}


class Service(private val url: String) {
    fun safeRequest(): SafeResult<HttpResponse> {
        return try {
            val data = httpGet(url)
            SafeResult(data, null)
        } catch (thrown: Throwable) {
            SafeResult(null, thrown)
        }
    }
}


class ComposedService(private val first: Service, private vararg val rest: Service) {
    fun getDataOrErrors(): SafeResult<HttpResponse> = if (rest.isEmpty()) {
        first.safeRequest()
      } else {
        rest.fold(first.safeRequest()) { dataOrErr, currentService ->
            dataOrErr.flatSequence{ accumulatedData ->
                return@flatSequence currentService.safeRequest().sequence { currentData ->
                    currentData compose accumulatedData
                }
            }
        }
    }

}

fun main(args: Array<String>) {
    val good = Service("https://www.android.com")
    val bad = Service(SUPER_SECRET_URL)

    val c1 = ComposedService(good)
    val c2 = ComposedService(bad)
    val c3 = ComposedService(good, good, good, bad, good)
    val c4 = ComposedService(good, good, good, good, good)

    c1.getDataOrErrors().endSequence({ println(it)}, { println(it?.message) })
    c2.getDataOrErrors().endSequence({ println(it)}, { println(it?.message) })
    c3.getDataOrErrors().endSequence({ println(it)}, { println(it?.message) })
    c4.getDataOrErrors().endSequence({ println(it)}, { println(it?.message) })

}