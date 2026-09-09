package com.yellow.trade.mappers;
import com.yellow.entities.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface AccountMapper {

    @Select("SELECT account_id, holder_name, email, phone_number, demat_id, pan, " +
            "balance, account_state, version, created_at, updated_at " +
            "FROM account WHERE account_id = #{accountId}")
    Account requireAccount(@Param("accountId") Long accountId);

    // Optimistic-locked write, used by order placement (Story 5) and nowhere else.
    // Names the version the row was read at, in the same statement as the write.
    // Zero rows affected means someone else wrote first; the caller returns ORD-409.
    @Update("UPDATE account " +
            "SET balance = #{newBalance}, version = version + 1, updated_at = CURRENT_TIMESTAMP " +
            "WHERE account_id = #{accountId} AND version = #{expectedVersion}")
    int updateBalanceWithVersion(@Param("accountId") Long accountId,
                                 @Param("newBalance") BigDecimal newBalance,
                                 @Param("expectedVersion") Long expectedVersion);
}