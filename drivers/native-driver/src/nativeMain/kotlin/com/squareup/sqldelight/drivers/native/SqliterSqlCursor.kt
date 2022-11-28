package com.squareup.sqldelight.drivers.native

import co.touchlab.sqliter.Cursor
import co.touchlab.sqliter.getBytesOrNull
import co.touchlab.sqliter.getDoubleOrNull
import co.touchlab.sqliter.getLongOrNull
import co.touchlab.sqliter.getStringOrNull
import com.squareup.sqldelight.db.SqlCursor

/**
 * Wrapper for cursor calls. Cursors point to real SQLite statements, so we need to be careful with
 * them. If dev closes the outer structure, this will get closed as well, which means it could start
 * throwing errors if you're trying to access it.
 */
internal class SqliterSqlCursor(
  private val cursor: Cursor,
  private val recycler: () -> Unit
) : SqlCursor {

  override fun close() {
    recycler()
  }

  override fun getBytes(index: Int): ByteArray? = cursor.getBytesOrNull(index)

  override fun getDouble(index: Int): Double? = cursor.getDoubleOrNull(index)

  override fun getLong(index: Int): Long? = cursor.getLongOrNull(index)

  override fun getString(index: Int): String? = cursor.getStringOrNull(index)

  override fun next(): Boolean = cursor.next()

  override fun getColumnsCount(): Int = cursor.columnCount

  override fun getColumnName(index: Int): String = cursor.columnName(index)

  override fun getType(index: Int): Int = cursor.getType(index).nativeCode

}
