package top.ilovemyhome.zora.jdbi.e2e.argument;


import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;
import top.ilovemyhome.zora.json.jackson.JacksonUtil;

import java.sql.Types;
import java.util.Map;

public class StringMapArgumentFactory extends AbstractArgumentFactory<Map<String,String>> {

    public StringMapArgumentFactory() {
        super(Types.VARCHAR);
    }

    @Override
    protected Argument build(Map<String, String> value, ConfigRegistry config) {
        String jsonVal = JacksonUtil.toJson(value);
        return (position, statement, ctx) -> statement.setString(position, jsonVal);
    }
}
