package top.ilovemyhome.zora.jdbi;

import org.apache.commons.lang3.StringUtils;
import top.ilovemyhome.zora.jdbi.page.Order;
import top.ilovemyhome.zora.jdbi.page.Pageable;
import top.ilovemyhome.zora.jdbi.page.Sort;

import java.util.*;
import java.util.stream.Collectors;


public class SqlGenerator {

    public static final String WHERE = " WHERE ";
    public static final String AND = " AND ";
    public static final String OR = " OR ";
    public static final String SELECT = "SELECT ";
    public static final String FROM = "FROM ";
    public static final String DELETE = "DELETE ";
    public static final String COMMA = ", ";
    public static final String PARAM = " = :%s";
    public static final String ID = "ID";

    public enum SQL_STATEMENT {
        deleteAll, deleteById, deleteByIds, selectAll, selectById, selectByIds, countAll, updateAll, updateById
    }

    public static final String SQL_CREATE_TEMPLATE = """
        INSERT INTO %s (
        %s)
        VALUES (
        %s);
        """;
    public static final String SQL_SELECT_ALL = """
        SELECT %s FROM %s
        """;

    public static final String SQL_COUNT_ALL = """
        select count(*) from %s
        """;


    private final String allColumnsClause;

    public SqlGenerator(String allColumnsClause) {
        this.allColumnsClause = allColumnsClause;
    }

    public SqlGenerator() {
        this("*");
    }

    public String count(TableDescription table) {
        return SELECT + "COUNT(*) " + FROM + table.getFromClause();
    }

    public String deleteById(TableDescription table) {
        return DELETE + FROM + table.getName() + whereByIdClause(table);
    }

    public String deleteByIds(TableDescription table) {
        return DELETE + FROM + table.getName() + whereByIdsClause(table, 1);
    }

    public String select(TableDescription table, SearchCriteria searchCriteria) {
        return String.format(SQL_SELECT_ALL, allColumnsClause, table.getFromClause()) +
            searchCriteria.whereClause();
    }

    public String select(TableDescription table, SearchCriteria searchCriteria, Pageable pageable) {
        String select = "";
        if (Objects.isNull(pageable)) {
            return select(table, searchCriteria);
        } else {
            select = String.format(SQL_SELECT_ALL, allColumnsClause, table.getFromClause()) +
                searchCriteria.whereClause();
            if (StringUtils.isEmpty(searchCriteria.pageableWhereClause(pageable))) {
                select = select + sortingClauseIfRequired(pageable.getSort()) + limitClause(pageable);
            }
        }
        return select;
    }

    public String count(TableDescription table, SearchCriteria searchCriteria) {
        return String.format(SQL_COUNT_ALL, table.getFromClause()) +
            searchCriteria.whereClause();
    }


    private String whereByIdClause(TableDescription table) {
        final StringBuilder whereClause = new StringBuilder(WHERE);
        String idField = table.getIdField();
        if (StringUtils.isNotBlank(idField)) {
            String idColumn = table.getFieldColumnMap().get(idField);
            whereClause.append(idColumn).append(String.format(PARAM, idField));
        } else {
            throw new IllegalArgumentException("Invalid ID field.");
        }
        return whereClause.toString();
    }

    private String whereByIdsClause(TableDescription table, int idsCount) {
        String idField = table.getIdField();
        if (StringUtils.isNotBlank(idField)) {
            return whereByIdsWithSingleIdColumn(table.getFieldColumnMap(), idField);
        } else {
            throw new IllegalArgumentException("Cannot find ID fields.");
        }
    }

    private String whereByIdsWithSingleIdColumn(Map<String, String> fieldColumnMap, String idField) {
        final StringBuilder whereClause = new StringBuilder(WHERE);
        String paramVal = String.format("<listOf%s>"
            , idField
        );
        return whereClause
            .append(fieldColumnMap.get(idField))
            .append(" IN (")
            .append(paramVal)
            .append(")")
            .toString();
    }

    public String selectAll(TableDescription table) {
        return SELECT + allColumnsClause + " " + FROM + table.getFromClause();
    }

    public String limitClause(Pageable page) {
        final int offset = (page.getPageNumber() -1) * page.getPageSize();
        return " LIMIT " + page.getPageSize() + " OFFSET " + offset;
    }

    public String selectById(TableDescription table) {
        return selectAll(table) + whereByIdClause(table);
    }

    public String selectByIds(TableDescription table, int idsCount) {
        switch (idsCount) {
            case 0:
                return selectAll(table);
            case 1:
                return selectById(table);
            default:
                return selectAll(table) + whereByIdsClause(table, idsCount);
        }
    }

    protected String sortingClauseIfRequired(Sort sort) {
        if (sort == null) {
            return "";
        }
        StringBuilder orderByClause = new StringBuilder();
        orderByClause.append(" ORDER BY ");
        for (Iterator<Order> iterator = sort.iterator(); iterator.hasNext(); ) {
            final Order order = iterator.next();
            // Validate property name against allowed field names (simple check to prevent SQL injection)
            String property = validatePropertyName(order.getProperty());
            orderByClause.
                append(property).
                append(" ").
                append(order.getDirection().toString());
            if (iterator.hasNext()) {
                orderByClause.append(COMMA);
            }
        }
        return orderByClause.toString();
    }

    /**
     * Validate that the property name is a valid SQL identifier.
     * This is a simple safeguard against SQL injection via ORDER BY clauses.
     * In production, consider using a whitelist of allowed field names from TableDescription.
     */
    private String validatePropertyName(String property) {
        if (property == null || property.isEmpty()) {
            throw new IllegalArgumentException("Property name cannot be null or empty");
        }
        // Simple pattern: allow alphanumeric, underscore, and dot (for table.column notation)
        if (!property.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$")) {
            throw new IllegalArgumentException("Invalid property name: " + property + ". Only alphanumeric, underscore, and dot allowed.");
        }
        return property;
    }

    public String updateAll(TableDescription table){
        return createUpdateStatement(table) + " where 1 = 1 ";
    }

    public String updateById(TableDescription table) {
        return createUpdateStatement(table) + whereByIdClause(table);
    }

    public String create(TableDescription table) {
        TreeMap<String, String> columns = table.getFieldColumnMap();
        StringBuilder columnBuilder = new StringBuilder();
        StringBuilder valueBuilder = new StringBuilder();
        for (Iterator<Map.Entry<String, String>> iterator = columns.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, String> entry = iterator.next();
            final String fieldName = entry.getKey();
            final String columnName = entry.getValue();
            boolean isIdColumn = fieldName.equals(table.getIdField());
            if (table.isIdAutoGenerate() && isIdColumn) {
                continue;
            }
            columnBuilder.append(columnName);
            valueBuilder.append(":t.").append(fieldName);
            if (iterator.hasNext()) {
                columnBuilder.append(COMMA);
                valueBuilder.append(COMMA);
            }
        }
        return String.format(SQL_CREATE_TEMPLATE, table.getName(), columnBuilder, valueBuilder);
    }


    public String deleteAll(TableDescription table) {
        return DELETE + FROM + table.getName();
    }

    private String createUpdateStatement(TableDescription table) {
        final StringBuilder updateQuery = new StringBuilder("UPDATE " + table.getName() + " SET ");
        Set<Map.Entry<String, String>> nonIdFieldSet = table.getFieldColumnMap().entrySet().stream()
            .filter(entry -> !entry.getKey().equals(table.getIdField())).collect(Collectors.toSet());
        for (Iterator<Map.Entry<String, String>> iterator = nonIdFieldSet.iterator(); iterator.hasNext(); ) {
            Map.Entry<String, String> fieldEntry = iterator.next();
            String fieldName = fieldEntry.getKey();
            String column = fieldEntry.getValue();
            updateQuery.append(column).append(" = :t.").append(fieldName);
            if (iterator.hasNext()) {
                updateQuery.append(COMMA);
            }
        }
        return updateQuery.toString();
    }

    public String getAllColumnsClause() {
        return allColumnsClause;
    }
}
