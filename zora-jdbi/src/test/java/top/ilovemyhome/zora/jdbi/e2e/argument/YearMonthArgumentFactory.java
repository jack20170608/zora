package top.ilovemyhome.zora.jdbi.e2e.argument;

import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;
import top.ilovemyhome.zora.common.date.LocalDateUtils;

import java.sql.Types;
import java.time.YearMonth;

public class YearMonthArgumentFactory extends AbstractArgumentFactory<YearMonth> {

    public YearMonthArgumentFactory() {
        super(Types.VARCHAR);
    }

    @Override
    protected Argument build(YearMonth value, ConfigRegistry config) {
        return (position, statement, ctx) -> statement.setString(position, LocalDateUtils.formatYearMonth(value));
    }
}
