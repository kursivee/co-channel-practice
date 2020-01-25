import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.select
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

data class Order(
    val name: String,
    val type: String,
    var barista: String = ""
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
    runBlocking {
        val queue = produceOrder(orders)
        launch {
            for(i in queue) {
                i.barista = "Michael Scott"
                printz(processOrder(i, "Michael Scott"))
            }
        }
        launch {
            for(i in queue) {
                i.barista = "Dwight Schrute"
                printz(processOrder(i, "Dwight Schrute"))
            }
        }
        launch {
            for(i in queue) {
                i.barista = "Pam Beesly"
                printz(processOrder(i, "Dwight Schrute"))
            }
        }
    }
}

suspend fun processOrder(order: Order, id: String): String {
    delay(300)
    printz("${order.barista} - ${order.type} for ${order.name} being made")
    return makingOrder(order)
}

suspend fun makingOrder(order: Order): String {
    printz("${order.barista} - Making ${order.type} for ${order.name}")
    coroutineScope {
        async {
            BeverageMaker.pour(order)
        }
    }.await()
    return "${order.barista} - ${order.type} for ${order.name} ready!"
}

data class BeverageRequest(
    val order: Order,
    val channel: SendChannel<Boolean>
)

object BeverageMaker: CoroutineScope {
    // How does coroutineContext affect implementation
    override val coroutineContext: CoroutineContext = Job()

    /**
     * Still don't have a proper mental model of actor but it seems like it's just a coroutine with single responsibility.
     * In this case, the actor is a pourer who's job is to indicate when pouring is complete
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
    private val pourer1: SendChannel<BeverageRequest> = actor {
        consumeEach {
            printz("${it.order.barista} - Pouring ${it.order.type} on pourer 1")
            delay((Math.random() * 10000 + 2000).toLong())
            printz("${it.order.barista} - Pouring ${it.order.type} Complete")
            it.channel.send(true)
            it.channel.close()
        }
    }

    private val pourer2: SendChannel<BeverageRequest> = actor {
        consumeEach {
            printz("${it.order.barista} - Pouring ${it.order.type} on pourer 2")
            delay((Math.random() * 10000 + 2000).toLong())
            printz("${it.order.barista} - Pouring ${it.order.type} Complete")
            it.channel.send(true)
            it.channel.close()
        }
    }

    suspend fun pour(order: Order) {
        /**
         * For mental model purposes I'm calling this the "selection zone". Another analogy would be when you are
         * figuring out the shortest line to go to to pay for your groceries.
         * 
         * Select waits for the pourer to complete. Complete == channel.close()
         */
        select<Unit> {
            printz("${order.barista} - Waiting to pour")
            val channel = Channel<Boolean>()
            pourer1.onSend(BeverageRequest(order, channel)) {
                channel.receive()
            }
            pourer2.onSend(BeverageRequest(order, channel)) {
                channel.receive()
            }
        }
    }
}

fun printz(s: String) {
    println("[${LocalDateTime.now()}][${Thread.currentThread().name}] $s")
}