package top.ilovemyhome.zora.jdbi.dao;

import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.jdbi.SearchCriteria;
import top.ilovemyhome.zora.jdbi.SqlGenerator;
import top.ilovemyhome.zora.jdbi.TableDescription;
import top.ilovemyhome.zora.jdbi.page.Page;
import top.ilovemyhome.zora.jdbi.page.Pageable;
import top.ilovemyhome.zora.jdbi.page.impl.PageImpl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public abstract class BaseDaoJdbiImpl<T> implements BaseDao<T> {

    protected abstract void registerRowMappers(Jdbi jdbi);


    @Override
    public Long create(T entity) {
        HandleCallback<Long, RuntimeException> callback = invokeCreate(entity);
        return jdbi.withHandle(callback);
    }

    @Override
    public HandleCallback<Long, RuntimeException> invokeCreate(T entity) {
        String sql = sqlGenerator.create(table);
        LOGGER.info("Create SQL=[{}].", sql);
        return h -> {
            Long result;
            boolean isRecord = entity.getClass().isRecord();
            Update update;
            if (isRecord) {
                update = h.createUpdate(sql)
                    .bindMethods("t", entity);
            }else {
                update = h.createUpdate(sql)
                    .bindBean("t", entity);
            }
            if (table.isIdAutoGenerate()) {
                result = update.executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one();
            } else {
                result = (long) update.execute();
            }
            return result;
        };
    }


    @Override
    public int update(Long id, T entity) {
        String sql = sqlGenerator.updateById(table);
        LOGGER.info("Update SQL=[{}].", sql);
        HandleCallback<Integer, RuntimeException> callback = handle -> handle.createUpdate(sql)
            .bindBean("t", entity)
            .bind("id", id)
            .execute();
        return jdbi.withHandle(callback);
    }

    @Override
    public int update(String sql, Map<String, Object> params) {
        return update(sql, params, null);
    }

    @Override
    public int update(String sql, Map<String, Object> params, Map<String, ?> listParam) {
        // delegate to the fully-typed update method; cast listParam at call site
        @SuppressWarnings("unchecked")
        Map<String, List<?>> typedListParam = (Map<String, List<?>>) listParam;
        return update(sql, params, typedListParam, null);
    }

    @Override
    public int update(final String sql, final Map<String, Object> params, final Map<String, ?> listParam, final Map<String, Object> beanParam) {
        LOGGER.info("Update sql=[{}].", sql);
        return jdbi.withHandle(handle -> {
            Update update = handle.createUpdate(sql);
            @SuppressWarnings("unchecked")
            Map<String, List<?>> typedListParam = (Map<String, List<?>>) listParam;
            bindParamsForUpdate(update, params, typedListParam, beanParam);
            return update.execute();
        });
    }

    @Override
    public int delete(Long id) {
        // compute or retrieve cached deleteById SQL
        String deleteByIdSql = getCachedSql(SqlGenerator.SQL_STATEMENT.deleteById);
        HandleCallback<Integer, RuntimeException> callback = handle -> handle.createUpdate(deleteByIdSql)
            .bind("id", id)
            .execute();
        return jdbi.withHandle(callback);
    }

    @Override
    public int delete(List<Long> listOfId) {
        String deleteByIdsSql = getCachedSql(SqlGenerator.SQL_STATEMENT.deleteByIds);
        HandleCallback<Integer, RuntimeException> callback = handle -> handle.createUpdate(deleteByIdsSql)
            .bind("listOfid", listOfId)
            .execute();
        return jdbi.withHandle(callback);
    }

    @Override
    public int delete(String sql, Map<String, Object> params, Map<String, ?> listParam) {
        return update(sql, params, listParam);
    }

    @Override
    public void deleteAll() {
        String deleteAllSql = getCachedSql(SqlGenerator.SQL_STATEMENT.deleteAll);
        jdbi.withHandle(handle -> handle.createUpdate(deleteAllSql)
            .execute());
    }

    @Override
    public Iterable<T> save(Iterable<T> entities) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<T> findOne(Long id) {
        String selectByIdSql = getCachedSql(SqlGenerator.SQL_STATEMENT.selectById);
        LOGGER.info("FindOne SQL=[{}].", selectByIdSql);
        return (Optional<T>) jdbi.withHandle(handle -> handle.createQuery(selectByIdSql)
            .bind("id", id)
            .mapTo(getEntityType())
            .findOne());
    }

    @Override
    public boolean exists(Long id) {
        return findOne(id).isPresent();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> find(String sql, Map<String, Object> params, Map<String, ?> listParam) {
        LOGGER.info("Find sql=[{}].", sql);
        return (List<T>) jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindParamsForQuery(query, params, listParam);
            return query.mapTo(getEntityType()).list();
        });
    }

    @Override
    public Page<T> find(SearchCriteria searchCriteria, Pageable page) {
        int total = count(searchCriteria);
        String sql = sqlGenerator.select(table, searchCriteria, page);
        List<T> pagedResult = find(sql, searchCriteria.normalParams(), searchCriteria.listParam());
        return new PageImpl<>(pagedResult, page, total);
    }

    @Override
    public int count(String sql, Map<String, Object> params, Map<String, ?> listParam) {
        LOGGER.info("Count sql=[{}].", sql);
        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindParamsForQuery(query, params, listParam);
            return query.mapTo(Integer.class).one();
        });
    }


    @Override
    public List<T> find(SearchCriteria searchCriteria) {
        String sql = sqlGenerator.select(table, searchCriteria);
        return find(sql, searchCriteria.normalParams(), searchCriteria.listParam());
    }

    @Override
    public List<Long> findIds(SearchCriteria searchCriteria) {
        String sql = String.format("select %s from %s", table.getIdField(), table.getFromClause())
            + searchCriteria.whereClause();
        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(sql);
            bindParamsForQuery(query, searchCriteria.normalParams(), searchCriteria.listParam());
            return query.mapTo(Long.class).list();
        });
    }

    @Override
    public int count(SearchCriteria searchCriteria) {
        String sql = sqlGenerator.count(table, searchCriteria);
        return count(sql, searchCriteria.normalParams(), searchCriteria.listParam());
    }


    @Override
    @SuppressWarnings("unchecked")
    public List<T> findAll() {
        String selectAllSql = getCachedSql(SqlGenerator.SQL_STATEMENT.selectAll);
        return (List<T>) jdbi.withHandle(handle -> handle.createQuery(selectAllSql)
            .mapTo(getEntityType())
            .list()
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> findAllByIds(List<Long> ids) {
        String selectByIdsSql = getCachedSql(SqlGenerator.SQL_STATEMENT.selectByIds);
        return (List<T>) jdbi.withHandle(handle -> handle.createQuery(selectByIdsSql)
            .bindList("listOfid", ids)
            .mapTo(getEntityType())
            .list()
        );
    }


    @Override
    public long countAll() {
        String countAllSql = getCachedSql(SqlGenerator.SQL_STATEMENT.countAll);
        return jdbi.withHandle(handle -> {
            Query query = handle.createQuery(countAllSql);
            return query.mapTo(Long.class).one();
        });
    }


    @Override
    public String getCachedSql(SqlGenerator.SQL_STATEMENT sqlStatementType) {
        // compute and cache the SQL the first time it's requested
        return sqlCache.computeIfAbsent(sqlStatementType, key -> switch (key) {
            case deleteAll -> sqlGenerator.deleteAll(this.table);
            case deleteById -> sqlGenerator.deleteById(this.table);
            case deleteByIds -> sqlGenerator.deleteByIds(this.table);
            case selectAll -> sqlGenerator.selectAll(this.table);
            case selectById -> sqlGenerator.selectById(this.table);
            case selectByIds -> sqlGenerator.selectByIds(this.table, 2);
            case countAll -> sqlGenerator.count(this.table);
            case updateAll -> sqlGenerator.updateAll(this.table);
            case updateById -> sqlGenerator.updateById(this.table);
        });
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        return null;
    }


    private void bindParamsForUpdate(Update update, Map<String, Object> params, Map<String, ?> listParam, Map<String, Object> beanParam) {
        if (Objects.nonNull(listParam) && !listParam.isEmpty()) {
            for (Map.Entry<String, ?> e : listParam.entrySet()) {
                update.bindList(e.getKey(), (List<?>) e.getValue());
            }
        }
        if (Objects.nonNull(params) && !params.isEmpty()) {
            params.forEach(update::bind);
        }
        if (Objects.nonNull(beanParam) && !beanParam.isEmpty()) {
            beanParam.forEach(update::bindBean);
        }
    }

    private void bindParamsForQuery(Query query, Map<String, Object> params, Map<String, ?> listParam) {
        if (Objects.nonNull(listParam) && !listParam.isEmpty()) {
            for (Map.Entry<String, ?> e : listParam.entrySet()) {
                query.bindList(e.getKey(), (List<?>) e.getValue());
            }
        }
        if (Objects.nonNull(params) && !params.isEmpty()) {
            params.forEach(query::bind);
        }
    }

    protected BaseDaoJdbiImpl(TableDescription table, Jdbi jdbi) {
        this.table = table;
        this.jdbi = jdbi;
        registerRowMappers(this.jdbi);
        this.sqlGenerator = new SqlGenerator();
    }

    private Type getEntityType() {
        return Objects.isNull(this.table.getEntityClass())
            ? ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0]
            : this.table.getEntityClass();
    }

    // Use enum keys for type-safety and avoid accidental String/enum mismatches
    protected final Map<SqlGenerator.SQL_STATEMENT, String> sqlCache = new ConcurrentHashMap<>(10);

    private final SqlGenerator sqlGenerator;
    protected final TableDescription table;
    protected final Jdbi jdbi;

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseDaoJdbiImpl.class);


}
