package shop

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.util.*

fun main() {
    val orders = mutableListOf<String>()
    repeat(30) {
        orders.add("ORDER ${it+1}")
    }
    runBlocking {
        val shop: Shop = Shop(OrderScreen(this))
        shop.hire(Worker("Worker 1", this))
        shop.hire(Worker("Worker 2", this))
        shop.open()
        launch {
            delay(300000)
            shop.close()
        }
        while(shop.open) {
            orders.forEach {
                shop.order(MenuItem.Coffee())
                delay(10000)
            }
        }
    }
}

class Shop(
    private val orderScreen: OrderScreen
){
    private val workers: MutableList<Worker> = mutableListOf()
    var open: Boolean = false
        private set

    fun hire(worker: Worker) {
        workers.add(worker)
    }

    fun order(menuItem: MenuItem ) {
        orderScreen.order(Order(
            menuItem,
            "Customer",
            null
        ))
    }

    fun open() {
        open = true
        workers.forEach {
            it.startWorking(orderScreen)
        }
    }

    fun close() {
        open = false
        workers.forEach {
            it.stopWorking()
        }
    }
}

class OrderScreen(scope: CoroutineScope): CoroutineScope by scope {
    private var orders: List<Order> = listOf()
    val inbound: Channel<OrderEvent> = Channel()
    val outbound: Channel<Order> = Channel()

    init {
        launch {
            inbound.consumeEach { event ->
                when(event) {
                    is Ordered -> {
                        orders = orders.toMutableList().also {
                            it.add(event.order)
                        }
                        outbound.send(event.order)
                    }
                    is Started -> {
                        orders = orders.map {
                            if(it.id == event.order.id) {
                                event.order
                            } else {
                                it
                            }
                        }
                    }
                    is Completed -> {
                        orders = orders.map {
                            if(it.id == event.order.id) {
                                event.order
                            } else {
                                it
                            }
                        }
                    }
                }
                log("Orders = ${orders.filter { it.orderState == OrderState.ORDERED }.joinToString("\n")}")
                log("In Progress = ${orders.filter { it.orderState == OrderState.IN_PROGRESS }.joinToString("\n")}")
                log("Completed = ${orders.filter { it.orderState == OrderState.COMPLETED }.joinToString("\n")}\n")
            }
        }
    }

    fun order(order: Order) {
        log("Customer ordered $order")
        launch {
            inbound.send(Ordered(order))
        }
    }

    suspend fun start(order: Order) {
        inbound.send(Started(order))
    }

    suspend fun complete(order: Order) {
        inbound.send(Completed(order))
    }
}

class Worker(private val id: String, scope: CoroutineScope): CoroutineScope by scope {
    private var job: Job? = null
    private lateinit var outbound: Channel<OrderEvent>

    fun startWorking(orderScreen: OrderScreen) {
        outbound = orderScreen.inbound
        job = launch {
            for(order in orderScreen.outbound) {
                orderScreen.start(order.copy(workerId = id, orderState = OrderState.IN_PROGRESS))
                wait(order.menuItem.prepTime)
                // Not sure why workerId keeps resetting to null on complete
                orderScreen.complete(order.copy(workerId = id, orderState = OrderState.COMPLETED))
            }
        }
    }

    fun stopWorking() {
        job?.cancel()
    }
}

sealed class OrderEvent
data class Ordered(val order: Order): OrderEvent()
data class Started(val order: Order): OrderEvent()
data class Completed(val order: Order): OrderEvent()


enum class OrderState {
    ORDERED,
    IN_PROGRESS,
    COMPLETED
}

data class Order(
    val menuItem: MenuItem, val customer: String,
    val workerId: String?,
    val orderState: OrderState = OrderState.ORDERED,
    val id: String = UUID.randomUUID().toString()
)

sealed class MenuItem {
    abstract val prepTime: Pair<Int, Int>
    data class Coffee(override val prepTime: Pair<Int, Int> = 2000 to 10000): MenuItem()
}


fun log(s: String) {
    println("[${Thread.currentThread().name}] - $s")
}

suspend fun wait(min: Int, max: Int = -1) {
    if(max == -1) {
        delay(min.toLong())
    } else {
        delay((Math.random() * max + min).toLong())
    }
}

suspend fun wait(pair: Pair<Int, Int>) {
    wait(pair.first, pair.second)
}