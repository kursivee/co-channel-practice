package flow.subscribe

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File

@InternalCoroutinesApi
fun main() {
    runBlocking {
        subscribe().collect {
            println(it)
        }
    }
}

/**
 * Simulate subscribing to a feed
 *
 * "Processes" data as it comes through
 */
fun subscribe(): Flow<String> {
    val reader = File("src/main/kotlin/flow/subscribe/text.txt").bufferedReader()
    return flow<String> {
        for(i in reader.lines()) {
            emit(i)
            delay(2000)
        }
    }.map {
        it.replace("\"", "")
    }
}