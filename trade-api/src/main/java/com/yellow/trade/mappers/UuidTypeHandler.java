package com.yellow.trade.mappers;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

/**
 * Binds java.util.UUID to PostgreSQL's uuid type.
 *
 * MyBatis ships handlers for the JDBC types the spec names, and uuid is not
 * one of them -- it is a PostgreSQL extension type, so the mapping has to be
 * declared. Without this, MyBatis reports "No typehandler found for property
 * orderId" while parsing the result map, at startup rather than at first use,
 * which is the right time to find out.
 *
 * Types.OTHER on the way in is what tells the driver to send the value as a
 * uuid rather than as a string the server then has to cast -- and a cast
 * would work until somebody compared a uuid column to a text parameter and
 * lost the index.
 */
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName, UUID.class);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getObject(columnIndex, UUID.class);
    }

    @Override
    public UUID getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getObject(columnIndex, UUID.class);
    }
}
