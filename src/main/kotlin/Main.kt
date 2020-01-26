import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.select
import java.time.LocalDateTime

data class Order(
    val name: String,
    val type: String,
    var barista: Barista? = null
)

data class Barista(
    val name: String
)

fun CoroutineScope.produceOrder(orders: List<Order>) = produce {
    orders.forEach {
        send(it)
    }
}

fun main() {
    val orders = listOf(
        Order("A","Coffee"),
        Order("C", "Tea"),
        Order("B","Water"),
        Order("S","Bagel"),
        Order("S","Juice"),
        Order("S","Burger"),
        Order("S","Biscuit"),
        Order("S","Macaron"),
        Order("S","Bacon")
    )
    val baristas = listOf(
        Barista("Michael Scott"),
        Barista("Dwight Schrute"),
        Barista("Pam Beesly")
    )
    runBlocking {
        BeverageMaker.scope = this
        val queue = produceOrder(orders)
        coroutineScope {
            baristas.forEach {
                launch {
                    for(order in queue) {
                        order.barista = it
                        log(processOrder(order))
                    }
                }
            }
        }
        println("Closing")
        BeverageMaker.close()
    }

}

suspend fun processOrder(order: Order): String {
    delay(300)
    log("${order.barista!!.name} - ${order.type} for ${order.name} being made")
    return makingOrder(order)
}

suspend fun makingOrder(order: Order): String {
    log("${order.barista!!.name} - Making ${order.type} for ${order.name}")
    BeverageMaker.queue(order)
    return "${order.barista!!.name} - ${order.type} for ${order.name} ready!"
}

data class BeverageRequest(
    val order: Order,
    val channel: SendChannel<Boolean>
)

object BeverageMaker {
    var scope: CoroutineScope? = null
    /**
     * Still don't have a proper mental model of actor but it seems like it's just a coroutine with single responsibility.
     * In this case, the actor is a pourer whose job is to indicate when pouring is complete
     *
     * Adding capacity to actor will add a buffer. Essentially a queue of size [capacity] will form behind the actor.
     * Example:
     * When setting capacity to 0
     * pourer1 queue = [Michael Scott]
     * pourer2 queue = [Dwight Scrute]
     * In this case Pam Beesly is in the "selection zone". She has not committed to any queue as of yet.
     *
     * When setting capacity to 1
     * pourer1 queue = [Michael Scott, Dwight Scrute]
     * pourer2 queue = [Pam Beesly]
     *
     * * When setting capacity to 2
     * pourer1 queue = [Michael Scott, Dwight Scrute, Pam Beesly]
     * pourer2 queue = []
     */
    private fun initPourer(name: String, capacity: Int = 0): SendChannel<BeverageRequest> = scope!!.actor(capacity = capacity) {
        // Need to figure the difference between consume and consume each. Consume seems to terminate the pourer.
        consumeEach {
            log("${it.order.barista!!.name} - Pouring ${it.order.type} on $name")
            wait(2, 10)
            log("${it.order.barista!!.name} - Pouring ${it.order.type} Complete")
            it.channel.send(true)
            it.channel.close()
        }
    }

    private val pourers by lazy {
        listOf(
            initPourer("pourer 1"),
            initPourer("pourer 2")
        )
    }

    suspend fun queue(order: Order) {
        /**
         * For mental model purposes I'm calling this the "selection zone".
         * Some grocery stores have a dedicated person to facilitate which queue a customer goes to.
         * The selector determines which queue to send the customer by constantly watching states of every queue.
         *
         * In this case, the select clause is watching each pourer. Once a pourer has room in their queue,
         * the selector tells the barista to move to the pourer.
         *
         * Select waits for the pourer to complete. Complete == channel.close()
         */
        select<Unit> {
            log("${order.barista!!.name} - Waiting for open pourer")
            val channel = Channel<Boolean>()
            pourers.forEach {
                it.onSend(BeverageRequest(order, channel)) {
                    channel.receive()
                }
            }
        }
    }

    fun close() {
        pourers.forEach {
            it.close()
        }
    }
}

fun log(s: String) {
    println("[${LocalDateTime.now()}][${Thread.currentThread().name}] $s")
}

suspend fun wait(min: Int, max: Int) {
    delay((Math.random() * (max * 1000) + (min * 1000)).toLong())
}