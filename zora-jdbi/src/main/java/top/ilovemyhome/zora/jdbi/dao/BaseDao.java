package top.ilovemyhome.zora.jdbi.dao;

import org.jdbi.v3.core.HandleCallback;
import top.ilovemyhome.zora.jdbi.SearchCriteria;
import top.ilovemyhome.zora.jdbi.SqlGenerator;
import top.ilovemyhome.zora.jdbi.page.Page;
import top.ilovemyhome.zora.jdbi.page.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

//this interface is refer to @org.springframework.data.repository.CrudRepository
public interface BaseDao<T> {

    String getCachedSql(SqlGenerator.SQL_STATEMENT sqlStatementType);

    Page<T> findAll(Pageable pageable);

    Long create(T entity);

    HandleCallback<Long, RuntimeException> invokeCreate(T entity);

    int update(Long id, T entity);

    int update(String sql, final Map<String, Object> params);

    int update(String sql, final Map<String, Object> params, final Map<String, ?> listParam);

    int update(String sql, final Map<String, Object> params, final Map<String, ?> listParam, final Map<String, Object> beanParam);

    Iterable<T> save(Iterable<T> entities);

    Optional<T> findOne(Long id);

    boolean exists(Long id);

    List<T> find(SearchCriteria searchCriteria);

    Page<T> find(SearchCriteria searchCriteria, Pageable pageable);

    List<T> find(String sql, Map<String, Object> params, Map<String, ?> listParam);

    List<Long> findIds(SearchCriteria searchCriteria);

    int count(SearchCriteria searchCriteria);
    int count(String sql, Map<String, Object> params, Map<String, ?> listParam);

    List<T> findAll();

    List<T> findAllByIds(List<Long> ids);

    long countAll();

    int delete(Long id);

    int delete(List<Long> listOfId);

    int delete(final String sql, final Map<String, Object> params, final Map<String, ?> listParam);

    void deleteAll();


}
