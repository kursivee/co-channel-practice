package shop

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach

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
            delay(30000)
            shop.close()
        }
        while(shop.open) {
            orders.forEach {
                shop.order(it)
                delay(3000)
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

    fun order(order: String ) {
        orderScreen.add(order)
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
    private val orders: MutableList<String> = mutableListOf()
    private val pending: MutableList<String> = mutableListOf()
    private val completed: MutableList<String> = mutableListOf()
    val receiveChannel: Channel<OrderEvent> = Channel()
    val sendChannel: Channel<String> = Channel()

    init {
        launch {
            receiveChannel.consumeEach { event ->
                when(event) {
                    is OrderStarted -> {
                        if(orders.removeIf { it == event.order }) {
                            log("${event.owner} taking order ${event.order}")
                            pending.add(event.order)
                        }
                    }
                    is OrderAdded -> {
                        orders.add(event.order)
                    }
                    is OrderCanceled -> {
                        if(pending.removeIf { it == event.order }) {
                            log("${event.order} canceled")
                            orders.add(event.order)
                        }
                    }
                    is OrderCompleted -> {
                        if(pending.removeIf { it == event.order }) {
                            log("${event.owner} completed order ${event.order}")
                            completed.add(event.order)
                        }
                    }
                }
                log("Current Orders = ${orders.joinToString(", ")}")
                log("Processed Orders = ${pending.joinToString(", ")}")
                log("Completed Orders = ${completed.joinToString(", ")}\n")
            }
        }
    }

    fun add(type: String) {
        log("Cashier adding order $type")
        launch {
            receiveChannel.send(OrderAdded(type))
            sendChannel.send(type)
        }
    }
}

class Worker(private val id: String, scope: CoroutineScope): CoroutineScope by scope {
    private var job: Job? = null
    private var currentOrder: String? = null
    private lateinit var channel: Channel<OrderEvent>

    fun startWorking(orderScreen: OrderScreen) {
        channel = orderScreen.receiveChannel
        job = launch {
            for(order in orderScreen.sendChannel) {
                // This has potential to break since the job can be cancelled before
                // setting the currentOrder. Better solution would be to maintain worker
                // in OrderScreen
                orderScreen.receiveChannel.send(OrderStarted(order, id))
                currentOrder = order
                wait(4000, 10000)
                orderScreen.receiveChannel.send(OrderCompleted(order, id))
                currentOrder = null
            }
        }
    }

    fun stopWorking() {
        job?.cancel()
        runBlocking {
            currentOrder?.let {
                channel.send(OrderCanceled(it, id))
            }
        }
    }
}

sealed class OrderEvent
data class OrderAdded(val order: String): OrderEvent()
data class OrderStarted(val order: String, val owner: String): OrderEvent()
data class OrderCanceled(val order: String, val owner: String): OrderEvent()
data class OrderCompleted(val order: String, val owner: String): OrderEvent()


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