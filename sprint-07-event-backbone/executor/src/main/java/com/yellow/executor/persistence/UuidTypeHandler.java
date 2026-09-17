package com.yellow.executor.persistence;

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
 * MyBatis type handler for {@code java.util.UUID} against Postgres's native
 * {@code uuid} column type.
 *
 * <p>WHY THIS EXISTS. MyBatis 3.x ships a default UUID handler that treats the
 * column as VARCHAR, which needs an explicit cast on Postgres to write into
 * a {@code uuid} column and can read as a {@link String} rather than a UUID
 * on the way back -- which, through auto-mapping, leaves {@code
 * ExecutableOrderRow.orderId} unset (silently null) and the guarded
 * {@code UPDATE orders WHERE order_id = ?} matches zero rows.
 *
 * <p>The fix is per-service because there are two Spring Boot processes
 * against the same database (trade-api and this executor), each with its own
 * MyBatis registry. {@code trade-api} has this handler already; the executor
 * needs its own copy.
 *
 * <p>Registered via {@link MyBatisTypeHandlerConfig}.
 */
@MappedTypes(UUID.class)
public class UuidTypeHandler extends BaseTypeHandler<UUID> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, UUID parameter, JdbcType jdbcType)
            throws SQLException {
        // Types.OTHER, not Types.VARCHAR: Postgres binds the UUID directly to
        // the uuid column without an explicit ::uuid cast.
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public UUID getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // Postgres's JDBC driver returns UUID columns as java.util.UUID when
        // getObject is asked for one explicitly.
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
