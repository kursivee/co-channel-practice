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

    // Investigate what actor does
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
        // Investigate what select does
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
    println("[${LocalDateTime.now()}] $s")
}