package top.ilovemyhome.zora.jdbi;


import top.ilovemyhome.zora.jdbi.page.Pageable;

import java.io.Serializable;
import java.util.Map;


public interface SearchCriteria extends Serializable {

    String whereClause();

    default Map<String, Object> normalParams() {
        return null;
    }

    default Map<String, ?> listParam() {
        return null;
    }

    default String pageableWhereClause(Pageable pageable) {
        return null;
    }

}
