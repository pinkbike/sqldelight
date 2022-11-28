@file:JvmName("JdbcDrivers")
package com.squareup.sqldelight.sqlite.driver

import com.squareup.sqldelight.Transacter
import com.squareup.sqldelight.db.SqlCursor
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.db.SqlPreparedStatement
import com.squareup.sqldelight.sqlite.driver.ConnectionManager.Transaction
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource

@JvmName("fromDataSource")
fun DataSource.asJdbcDriver() = object : JdbcDriver() {
  override fun getConnection(): Connection {
    return connection
  }

  override fun closeConnection(connection: Connection) {
    connection.close()
  }
}

interface ConnectionManager {
  fun close()

  fun getConnection(): Connection

  fun closeConnection(connection: Connection)

  fun Connection.beginTransaction()

  fun Connection.endTransaction()

  fun Connection.rollbackTransaction()

  var transaction: Transaction?

  class Transaction(
    override val enclosingTransaction: Transaction?,
    private val connectionManager: ConnectionManager,
    val connection: Connection
  ) : Transacter.Transaction() {
    override fun endTransaction(successful: Boolean) {
      if (enclosingTransaction == null) {
        if (successful) connectionManager.apply { connection.endTransaction() }
        else connectionManager.apply { connection.rollbackTransaction() }
      }
      connectionManager.transaction = enclosingTransaction
    }
  }
}

abstract class JdbcDriver : SqlDriver, ConnectionManager {
  override fun close() {
  }

  override fun Connection.endTransaction() {
    commit()
    autoCommit = true
    closeConnection(this)
  }

  override fun Connection.rollbackTransaction() {
    rollback()
    autoCommit = true
    closeConnection(this)
  }

  override fun Connection.beginTransaction() {
    check(autoCommit) {
      """
      Expected autoCommit to be true by default. For compatibility with SQLDelight make sure it is
      set to true when returning a connection from [JdbcDriver.getConnection()]
      """.trimIndent()
    }
    autoCommit = false
  }

  private val transactions = ThreadLocal<Transaction>()

  override var transaction: Transaction?
    get() = transactions.get()
    set(value) { transactions.set(value) }

  /**
   * Returns a [Connection] and handler which closes the connection after the transaction finished.
   */
  fun connectionAndClose(): Pair<Connection, () -> Unit> {
    val enclosing = transaction
    return if (enclosing != null) {
      enclosing.connection to {}
    } else {
      val connection = getConnection()
      return connection to { closeConnection(connection) }
    }
  }

  override fun execute(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?
  ) {
    val (connection, onClose) = connectionAndClose()
    try {
      connection.prepareStatement(sql).use { jdbcStatement ->
        JdbcPreparedStatement(jdbcStatement)
          .apply { if (binders != null) this.binders() }
          .execute()
      }
    } finally {
      onClose()
    }
  }

  override fun executeQuery(
    identifier: Int?,
    sql: String,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?
  ): SqlCursor {
    val (connection, onClose) = connectionAndClose()
    try {
      return JdbcPreparedStatement(connection.prepareStatement(sql))
        .apply { if (binders != null) this.binders() }
        .executeQuery(onClose)
    } catch (e: Exception) {
      onClose()
      throw e
    }
  }

  override fun newTransaction(): Transacter.Transaction {
    val enclosing = transaction
    val connection = enclosing?.connection ?: getConnection()
    val transaction = Transaction(enclosing, this, connection)
    this.transaction = transaction

    if (enclosing == null) {
      connection.beginTransaction()
    }

    return transaction
  }

  override fun currentTransaction(): Transacter.Transaction? = transaction
}

/**
 * Binds the parameter to [preparedStatement] by calling [bindString], [bindLong] or similar.
 * After binding, [execute] executes the query without a result, while [executeQuery] returns [JdbcCursor].
 */
open class JdbcPreparedStatement(
  private val preparedStatement: PreparedStatement
) : SqlPreparedStatement {
  override fun bindBytes(index: Int, bytes: ByteArray?) {
    if (bytes == null) {
      preparedStatement.setNull(index, Types.BLOB)
    } else {
      preparedStatement.setBytes(index, bytes)
    }
  }

  override fun bindLong(index: Int, long: Long?) {
    if (long == null) {
      preparedStatement.setNull(index, Types.INTEGER)
    } else {
      preparedStatement.setLong(index, long)
    }
  }

  override fun bindDouble(index: Int, double: Double?) {
    if (double == null) {
      preparedStatement.setNull(index, Types.REAL)
    } else {
      preparedStatement.setDouble(index, double)
    }
  }

  override fun bindString(index: Int, string: String?) {
    if (string == null) {
      preparedStatement.setNull(index, Types.VARCHAR)
    } else {
      preparedStatement.setString(index, string)
    }
  }

  fun executeQuery(onClose: () -> Unit) =
    JdbcCursor(preparedStatement, preparedStatement.executeQuery(), onClose)

  fun execute() {
    preparedStatement.execute()
  }
}

/**
 * Iterate each row in [resultSet] and map the columns to Kotlin classes by calling [getString], [getLong] etc.
 * Use [next] to retrieve the next row and [close] to close the connection.
 */
open class JdbcCursor(
  private val preparedStatement: PreparedStatement,
  private val resultSet: ResultSet,
  private val onClose: () -> Unit
) : SqlCursor {
  override fun getString(index: Int): String? = resultSet.getString(index + 1)
  override fun getBytes(index: Int): ByteArray? = resultSet.getBytes(index + 1)
  override fun getLong(index: Int): Long? {
    return resultSet.getLong(index + 1).takeUnless { resultSet.wasNull() }
  }
  override fun getDouble(index: Int): Double? {
    return resultSet.getDouble(index + 1).takeUnless { resultSet.wasNull() }
  }
  override fun close() {
    resultSet.close()
    preparedStatement.close()
    onClose()
  }
  override fun next() = resultSet.next()
  override fun getColumnsCount(): Int = -1

  override fun getColumnName(index: Int) = null

  override fun getType(index: Int): Int = -1
}
