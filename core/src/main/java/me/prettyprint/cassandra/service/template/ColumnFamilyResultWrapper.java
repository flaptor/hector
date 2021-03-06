package me.prettyprint.cassandra.service.template;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import me.prettyprint.cassandra.model.ExecutionResult;
import me.prettyprint.cassandra.model.HColumnImpl;
import me.prettyprint.cassandra.serializers.ByteBufferSerializer;
import me.prettyprint.cassandra.service.CassandraHost;
import me.prettyprint.hector.api.Serializer;
import me.prettyprint.hector.api.beans.HColumn;

import org.apache.cassandra.thrift.ColumnOrSuperColumn;

/**
 * Wraps the results with as an Iterator. The underlying Iterator has already been advanced
 * to the first row upon construction.
 * 
 * @author zznate
 */
public class ColumnFamilyResultWrapper<K,N> extends AbstractResultWrapper<K,N> {
  
  private Map<N,HColumn<N,ByteBuffer>> columns = new LinkedHashMap<N,HColumn<N,ByteBuffer>>();
  private Iterator<Map.Entry<ByteBuffer, List<ColumnOrSuperColumn>>> rows;
  private Map.Entry<ByteBuffer, List<ColumnOrSuperColumn>> entry;
  private ExecutionResult<Map<ByteBuffer,List<ColumnOrSuperColumn>>> executionResult;
  
  public ColumnFamilyResultWrapper(Serializer<K> keySerializer,
      Serializer<N> columnNameSerializer,
      ExecutionResult<Map<ByteBuffer,List<ColumnOrSuperColumn>>> executionResult) {
    super(keySerializer, columnNameSerializer, executionResult);    
    this.rows = executionResult.get().entrySet().iterator();    
    next();
  }
   
  /**
   * All the column names we know about in the current iterator position
   * @return
   */
  public Collection<N> getColumnNames() {
    return columns.keySet();
  }
  
  public ByteBuffer getColumnValue( N columnName) {
    HColumn<N,ByteBuffer> col = getColumn( columnName );
    return col != null ? col.getValue() : null;
  }

  private HColumn<N,ByteBuffer> getColumn( N columnName ) {
    return columns.get( columnName );
  }
  

  private void applyToRow(List<ColumnOrSuperColumn> cosclist) {
    HColumn<N, ByteBuffer> column;
    N colName;
    for (Iterator<ColumnOrSuperColumn> iterator = cosclist.iterator(); iterator.hasNext();) {
      ColumnOrSuperColumn cosc = iterator.next();            

      colName = columnNameSerializer.fromByteBuffer(cosc.getColumn().name.duplicate());
      column = columns.get(colName);
      
      if ( column == null ) {
        column = new HColumnImpl<N, ByteBuffer>(cosc.getColumn(), columnNameSerializer, ByteBufferSerializer.get());
      } else {
        ((HColumnImpl<N, ByteBuffer>)column).apply(cosc.getColumn());
      }
      columns.put(colName, column); 
      iterator.remove();
    }
  }

  @Override
  public K getKey() {    
    return keySerializer.fromByteBuffer(entry.getKey());
  }

  @Override
  public ColumnFamilyResult<K, N> next() {
    if ( !hasNext() ) {
      throw new NoSuchElementException("No more rows left on this HColumnFamily");
    }
    entry = rows.next();    
    applyToRow(entry.getValue());
    return this;
  }
  
  @Override
  public boolean hasNext() {
    return rows.hasNext();
  }

  @Override
  public void remove() {
    rows.remove();
  }

}
