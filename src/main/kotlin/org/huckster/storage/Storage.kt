package org.huckster.storage

import com.vladsch.kotlin.jdbc.session
import com.vladsch.kotlin.jdbc.sqlQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.huckster.arbitrage.model.Arbitrage
import org.intellij.lang.annotations.Language

/**
 * Хранилище
 */
class Storage(properties: StorageProperties) {

    private val session = session(properties.url, properties.user, properties.password)
    private val log = KotlinLogging.logger { }

    /**
     * Сохранить арбитраж
     */
    suspend fun save(arbitrage: Arbitrage) = withContext(Dispatchers.IO) {

        @Language("PostgreSQL")
        val insertArbitrageSql =
            "insert into arbitrages (id, timestamp, profit) values (?, ?, ?)"

        @Language("PostgreSQL")
        val insertOrderSql =
            "insert into arbitrage_orders (arbitrage_id, index, type, symbol, price) values (?, ?, ?, ?, ?)"

        session.transaction { transaction ->
            transaction.execute(
                sqlQuery(
                    insertArbitrageSql,
                    arbitrage.id,
                    arbitrage.timestamp,
                    arbitrage.profit
                )
            )
            arbitrage.orders.forEachIndexed { index, order ->
                transaction.execute(
                    sqlQuery(
                        insertOrderSql,
                        arbitrage.id,
                        index,
                        order.type.name,
                        order.symbol,
                        order.price
                    )
                )
            }
        }

        log.info("Arbitrage saved: ${arbitrage.id}")
    }
}
