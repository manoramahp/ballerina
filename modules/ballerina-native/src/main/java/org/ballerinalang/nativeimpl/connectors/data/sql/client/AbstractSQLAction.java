/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.nativeimpl.connectors.data.sql.client;

import org.ballerinalang.bre.Context;
import org.ballerinalang.model.types.TypeEnum;
import org.ballerinalang.model.values.BArray;
import org.ballerinalang.model.values.BDataTable;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.nativeimpl.connectors.data.sql.Constants;
import org.ballerinalang.nativeimpl.connectors.data.sql.SQLConnector;
import org.ballerinalang.nativeimpl.connectors.data.sql.SQLDataIterator;
import org.ballerinalang.natives.connectors.AbstractNativeAction;
import org.ballerinalang.util.exceptions.BallerinaException;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * {@code AbstractSQLAction} is the base class for all SQL Connector Action.
 *
 * @since 0.8.0
 */
public abstract class AbstractSQLAction extends AbstractNativeAction {


    protected void executeQuery(Context context, SQLConnector connector, String query, BArray parameters) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = connector.getSQLConnection();
            stmt = getPreparedStatement(conn, connector, query);
            createProcessedStatement(stmt, parameters);
            rs = stmt.executeQuery();
            BDataTable dataTable = new BDataTable(new SQLDataIterator(conn, stmt, rs), new HashMap<>(),
                    getColumnDefinitions(rs));
            context.getControlStack().setReturnValue(0, dataTable);
        } catch (SQLException e) {
            SQLConnectorUtils.cleanupConnection(rs, stmt, conn);
            throw new BallerinaException("execute query failed: " + e.getMessage(), e);
        }
    }

    protected void executeUpdate(Context context, SQLConnector connector, String query, BArray parameters) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = connector.getSQLConnection();
            stmt = conn.prepareStatement(query);
            createProcessedStatement(stmt, parameters);
            int count = stmt.executeUpdate();
            BInteger updatedCount = new BInteger(count);
            context.getControlStack().setReturnValue(0, updatedCount);
        } catch (SQLException e) {
            throw new BallerinaException("execute update failed: " + e.getMessage(), e);
        } finally {
            SQLConnectorUtils.cleanupConnection(rs, stmt, conn);
        }
    }

    protected void executeUpdateWithKeys(Context context, SQLConnector connector, String query,
            BArray<BString> keyColumns, BArray parameters) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = connector.getSQLConnection();
            int keyColumnCount = 0;
            if (keyColumns != null) {
                keyColumnCount = keyColumns.size();
            }
            if (keyColumnCount > 0) {
                String[] columnArray = new String[keyColumnCount];
                for (int i = 0; i < keyColumnCount; i++) {
                    columnArray[i] = keyColumns.get(i).stringValue();
                }
                stmt = conn.prepareStatement(query, columnArray);
            } else {
                stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            }
            createProcessedStatement(stmt, parameters);
            int count = stmt.executeUpdate();
            BInteger updatedCount = new BInteger(count);
            context.getControlStack().setReturnValue(0, updatedCount);
            rs = stmt.getGeneratedKeys();
            /*The result set contains the auto generated keys. It can have multiple rows if multiple rows have
            updated with the execute operation. There can be multiple auto generated columns in a table.
            TODO: iterate the result set and generate a array of key arrays*/
            if (rs.next()) {
                BArray<BString> generatedKeys = getGeneratedKeys(rs);
                context.getControlStack().setReturnValue(1, generatedKeys);
            }
        } catch (SQLException e) {
            throw new BallerinaException("execute update with generated keys failed: " + e.getMessage(), e);
        } finally {
            SQLConnectorUtils.cleanupConnection(rs, stmt, conn);
        }
    }

    protected void executeProcedure(Context context, SQLConnector connector, String query, BArray parameters) {
        Connection conn = null;
        CallableStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = connector.getSQLConnection();
            stmt = getPreparedCall(conn, connector, query);
            createProcessedStatement(stmt, parameters);
            boolean hasResult = stmt.execute();
            setOutParameters(stmt, parameters);
            if (hasResult) {
                rs = stmt.getResultSet();
                BDataTable datatable = new BDataTable(new SQLDataIterator(conn, stmt, rs), new HashMap<>(),
                        getColumnDefinitions(rs));
                context.getControlStack().setReturnValue(0, datatable);
            } else {
                SQLConnectorUtils.cleanupConnection(null, stmt, conn);
            }
        } catch (SQLException e) {
            SQLConnectorUtils.cleanupConnection(rs, stmt, conn);
            throw new BallerinaException("execute stored procedure failed: " + e.getMessage(), e);
        }
    }

    private PreparedStatement getPreparedStatement(Connection conn, SQLConnector connector, String query)
            throws SQLException {
        PreparedStatement stmt;
        boolean mysql = connector.getDatabaseName().contains("mysql");
        if (mysql) {
            stmt = conn.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            // To fulfill OBL_UNSATISFIED_OBLIGATION_EXCEPTION_EDGE findbugs validation.
            try {
                stmt.setFetchSize(Integer.MIN_VALUE);
            } catch (SQLException e) {
                stmt.close();
            }
        } else {
            stmt = conn.prepareStatement(query);
        }
        return stmt;
    }

    private CallableStatement getPreparedCall(Connection conn, SQLConnector connector, String query)
            throws SQLException {
        CallableStatement stmt;
        boolean mysql = connector.getDatabaseName().contains("mysql");
        if (mysql) {
            stmt = conn.prepareCall(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(Integer.MIN_VALUE);
        } else {
            stmt = conn.prepareCall(query);
        }
        return stmt;
    }

    private ArrayList<BDataTable.ColumnDefinition> getColumnDefinitions(ResultSet rs) throws SQLException {
        ArrayList<BDataTable.ColumnDefinition> columnDefs = new ArrayList<>();
        ResultSetMetaData rsMetaData = rs.getMetaData();
        int cols = rsMetaData.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            String colName = rsMetaData.getColumnName(i);
            int colType = rsMetaData.getColumnType(i);
            TypeEnum mappedType = SQLConnectorUtils.getColumnType(colType);
            columnDefs.add(new BDataTable.ColumnDefinition(colName, mappedType));
        }
        return columnDefs;
    }

    private BArray<BString> getGeneratedKeys(ResultSet rs) throws SQLException {
        BArray<BString> generatedKeys = new BArray<>(BString.class);
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        int columnType;
        String value;
        BigDecimal bigDecimal;
        for (int i = 1; i <= columnCount; i++) {
            columnType = metaData.getColumnType(i);
            switch (columnType) {
            case Types.INTEGER:
            case Types.TINYINT:
            case Types.SMALLINT:
                value = Integer.toString(rs.getInt(i));
                break;
            case Types.DOUBLE:
                value = Double.toString(rs.getDouble(i));
                break;
            case Types.FLOAT:
                value = Float.toString(rs.getFloat(i));
                break;
            case Types.BOOLEAN:
            case Types.BIT:
                value = Boolean.toString(rs.getBoolean(i));
                break;
            case Types.DECIMAL:
            case Types.NUMERIC:
                bigDecimal = rs.getBigDecimal(i);
                if (bigDecimal != null) {
                    value = bigDecimal.toPlainString();
                } else {
                    value = null;
                }
                break;
            case Types.BIGINT:
                value = Long.toString(rs.getLong(i));
                break;
            default:
                value = rs.getString(i);
                break;
            }
            generatedKeys.add(i - 1, new BString(value));
        }
        return generatedKeys;
    }

    private void createProcessedStatement(PreparedStatement stmt, BArray params) {
        int paramCount = params.size();
        for (int index = 0; index < paramCount; index++) {
            BStruct paramValue = (BStruct) params.get(index);
            String sqlType = paramValue.getValue(0).stringValue();
            BValue value = paramValue.getValue(1);
            int direction = Integer.parseInt(paramValue.getValue(2).stringValue());
            setParameter(stmt, sqlType, value, direction, index);
        }
    }

    private void setParameter(PreparedStatement stmt, String sqlType, BValue value, int direction, int index) {
        if (sqlType == null || sqlType.isEmpty()) {
            SQLConnectorUtils.setStringValue(stmt, value, index, direction, Types.VARCHAR);
        } else {
            String sqlDataType = sqlType.toUpperCase(Locale.getDefault());
            switch (sqlDataType) {
            case Constants.SQLDataTypes.INTEGER:
                SQLConnectorUtils.setIntValue(stmt, value, index, direction, Types.INTEGER);
                break;
            case Constants.SQLDataTypes.VARCHAR:
                SQLConnectorUtils.setStringValue(stmt, value, index, direction, Types.VARCHAR);
                break;
            case Constants.SQLDataTypes.DOUBLE:
                SQLConnectorUtils.setDoubleValue(stmt, value, index, direction, Types.DOUBLE);
                break;
            case Constants.SQLDataTypes.NUMERIC:
            case Constants.SQLDataTypes.DECIMAL:
                SQLConnectorUtils.setNumericValue(stmt, value, index, direction, Types.NUMERIC);
                break;
            case Constants.SQLDataTypes.BIT:
            case Constants.SQLDataTypes.BOOLEAN:
                SQLConnectorUtils.setBooleanValue(stmt, value, index, direction, Types.BIT);
                break;
            case Constants.SQLDataTypes.TINYINT:
                SQLConnectorUtils.setTinyIntValue(stmt, value, index, direction, Types.TINYINT);
                break;
            case Constants.SQLDataTypes.SMALLINT:
                SQLConnectorUtils.setSmallIntValue(stmt, value, index, direction, Types.SMALLINT);
                break;
            case Constants.SQLDataTypes.BIGINT:
                SQLConnectorUtils.setBigIntValue(stmt, value, index, direction, Types.BIGINT);
                break;
            case Constants.SQLDataTypes.REAL:
            case Constants.SQLDataTypes.FLOAT:
                SQLConnectorUtils.setRealValue(stmt, value, index, direction, Types.FLOAT);
                break;
            case Constants.SQLDataTypes.DATE:
                SQLConnectorUtils.setDateValue(stmt, value, index, direction, Types.DATE);
                break;
            case Constants.SQLDataTypes.TIMESTAMP:
                SQLConnectorUtils.setTimeStampValue(stmt, value, index, direction, Types.TIMESTAMP);
                break;
            case Constants.SQLDataTypes.TIME:
                SQLConnectorUtils.setTimeValue(stmt, value, index, direction, Types.TIME);
                break;
            case Constants.SQLDataTypes.BINARY:
                SQLConnectorUtils.setBinaryValue(stmt, value, index, direction, Types.BINARY);
                break;
            case Constants.SQLDataTypes.BLOB:
                SQLConnectorUtils.setBlobValue(stmt, value, index, direction, Types.BLOB);
                break;
            case Constants.SQLDataTypes.CLOB:
                SQLConnectorUtils.setClobValue(stmt, value, index, direction, Types.CLOB);
                break;
            default:
                throw new BallerinaException("unsupported datatype as parameter: " + sqlType + " index:" + index);
            }
        }
    }

    private void setOutParameters(CallableStatement stmt, BArray params) {
        int paramCount = params.size();
        for (int index = 0; index < paramCount; index++) {
            BStruct paramValue = (BStruct) params.get(index);
            String sqlType = paramValue.getValue(0).stringValue();
            int direction = Integer.parseInt(paramValue.getValue(2).stringValue());
            if (direction == Constants.QueryParamDirection.INOUT || direction == Constants.QueryParamDirection.OUT) {
                setOutParameterValue(stmt, sqlType, index, paramValue);
            }
        }
    }

    private void setOutParameterValue(CallableStatement stmt, String sqlType, int index, BStruct paramValue) {
        try {
            String sqlDataType = sqlType.toUpperCase(Locale.getDefault());
            Object elementValue;
            String stringValue = "";
            switch (sqlDataType) {
            case Constants.SQLDataTypes.INTEGER:
                elementValue = stmt.getInt(index + 1);
                stringValue = elementValue.toString();
                break;
            case Constants.SQLDataTypes.VARCHAR:
                elementValue = stmt.getString(index + 1);
                stringValue = elementValue == null ? "" : elementValue.toString();
                break;
            case Constants.SQLDataTypes.NUMERIC:
            case Constants.SQLDataTypes.DECIMAL:
                elementValue = stmt.getBigDecimal(index + 1);
                stringValue = elementValue == null ? "" : elementValue.toString();
                break;
            case Constants.SQLDataTypes.BIT:
            case Constants.SQLDataTypes.BOOLEAN:
                elementValue = stmt.getBoolean(index + 1);
                stringValue = elementValue.toString();
                break;
            case Constants.SQLDataTypes.TINYINT:
                elementValue = stmt.getByte(index + 1);
                stringValue = elementValue.toString();
                break;
            case Constants.SQLDataTypes.SMALLINT:
                elementValue = stmt.getShort(index + 1);
                stringValue = elementValue.toString();
                break;
            case Constants.SQLDataTypes.BIGINT:
                elementValue = stmt.getLong(index + 1);
                stringValue = elementValue.toString();
                break;
            case Constants.SQLDataTypes.REAL:
            case Constants.SQLDataTypes.FLOAT:
                elementValue = stmt.getFloat(index + 1);
                stringValue = elementValue.toString();
                break;
            case Constants.SQLDataTypes.DOUBLE:
                elementValue = stmt.getDouble(index + 1);
                stringValue = elementValue.toString();
                break;
            case Constants.SQLDataTypes.CLOB:
                elementValue = stmt.getClob(index + 1);
                stringValue = elementValue == null ? "" : SQLConnectorUtils.getString((Clob) elementValue);
                break;
            case Constants.SQLDataTypes.BLOB:
            case Constants.SQLDataTypes.BINARY:
                elementValue = stmt.getBlob(index + 1);
                stringValue = elementValue == null ? "" : SQLConnectorUtils.getString((Blob) elementValue);
                break;
            case Constants.SQLDataTypes.DATE:
                elementValue = stmt.getDate(index + 1);
                stringValue = elementValue == null ? "" : SQLConnectorUtils.getString((Date) elementValue);
                break;
            case Constants.SQLDataTypes.TIMESTAMP:
                elementValue = stmt.getTimestamp(index + 1);
                stringValue = elementValue == null ? "" : SQLConnectorUtils.getString((Timestamp) elementValue);
                break;
            case Constants.SQLDataTypes.TIME:
                elementValue = stmt.getTime(index + 1);
                stringValue = elementValue == null ? "" : SQLConnectorUtils.getString((Time) elementValue);
                break;
            default:
                throw new BallerinaException(
                        "unsupported datatype as out/inout parameter: " + sqlType + " index:" + index);
            }
            paramValue.setValue(1, new BString(stringValue)); //Value is the first position of the struct
        } catch (SQLException e) {
            throw new BallerinaException("error in getting out parameter value: " + e.getMessage(), e);
        }
    }
}
