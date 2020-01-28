package flow

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

@InternalCoroutinesApi
fun main() {
    fun f(): Flow<String> = flow {
        "Hello World".forEach {
            emit(it.toString())
            delay(1000)
        }
    }

    runBlocking {
        f().collect { value -> println(value) }
    }
}

