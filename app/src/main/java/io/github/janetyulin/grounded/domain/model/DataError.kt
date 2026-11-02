package io.github.janetyulin.grounded.domain.model

/**
 * Why loading data failed, independent of the transport. Repositories return these as the failure
 * of a [Result] so screens can pick a message without seeing HTTP or I/O details.
 */
sealed class DataError(cause: Throwable? = null) : Exception(cause) {
    /** No connection, timeout, DNS failure. Usually fixed by retrying later. */
    class Network(cause: Throwable) : DataError(cause)

    /** The server answered with an error status. */
    class Server(val code: Int) : DataError()

    /** Anything else, such as a response that couldn't be parsed. */
    class Unknown(cause: Throwable) : DataError(cause)
}
